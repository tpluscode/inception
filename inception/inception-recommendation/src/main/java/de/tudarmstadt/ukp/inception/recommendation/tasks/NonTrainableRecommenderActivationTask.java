/*
 * Licensed to the Technische Universität Darmstadt under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Technische Universität Darmstadt 
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.recommendation.tasks;

import static de.tudarmstadt.ukp.inception.recommendation.api.recommender.TrainingCapability.TRAINING_NOT_SUPPORTED;
import static de.tudarmstadt.ukp.inception.support.logging.LogLevel.ERROR;
import static java.lang.System.currentTimeMillis;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.concurrent.ConcurrentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.inception.recommendation.api.RecommendationService;
import de.tudarmstadt.ukp.inception.recommendation.api.model.EvaluatedRecommender;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngine;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationException;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommenderContext;
import de.tudarmstadt.ukp.inception.recommendation.event.RecommenderEvaluationResultEvent;
import de.tudarmstadt.ukp.inception.recommendation.event.RecommenderTaskNotificationEvent;
import de.tudarmstadt.ukp.inception.schema.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.inception.support.logging.LogMessage;
import jakarta.persistence.NoResultException;

/**
 * This task activates all non-trainable recommenders.
 */
public class NonTrainableRecommenderActivationTask
    extends RecommendationTask_ImplBase
{
    public static final String TYPE = "NonTrainableRecommenderActivationTask";

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private @Autowired AnnotationSchemaService annoService;
    private @Autowired RecommendationService recommendationService;
    private @Autowired ApplicationEventPublisher appEventPublisher;

    public NonTrainableRecommenderActivationTask(Builder<? extends Builder<?>> aBuilder)
    {
        super(aBuilder.withType(TYPE));
    }

    @Override
    public String getTitle()
    {
        return "Activating non-trainable recommenders...";
    }

    @Override
    public void execute()
    {
        User user = getUser().orElseThrow();

        for (AnnotationLayer layer : annoService.listAnnotationLayer(getProject())) {
            if (!layer.isEnabled()) {
                continue;
            }

            List<Recommender> recommenders = recommendationService.listRecommenders(layer);
            if (recommenders == null || recommenders.isEmpty()) {
                continue;
            }

            List<EvaluatedRecommender> evaluatedRecommenders = new ArrayList<>();
            for (Recommender r : recommenders) {
                // Make sure we have the latest recommender config from the DB - the one from
                // the active recommenders list may be outdated
                Optional<Recommender> optRecommender = freshenRecommender(user, r);
                if (optRecommender.isEmpty()) {
                    continue;
                }

                Recommender recommender = optRecommender.get();
                String recommenderName = recommender.getName();

                try {
                    long start = System.currentTimeMillis();

                    considerRecommender(user, recommender).ifPresent(evaluatedRecommender -> {
                        var result = evaluatedRecommender.getEvaluationResult();

                        evaluatedRecommenders.add(evaluatedRecommender);
                        appEventPublisher.publishEvent(new RecommenderEvaluationResultEvent(this,
                                recommender, user.getUsername(), result,
                                currentTimeMillis() - start, evaluatedRecommender.isActive()));
                    });
                }
                catch (Throwable e) {
                    // Catching Throwable is intentional here as we want to continue the execution
                    // even if a particular recommender fails.
                    LOG.error("[{}][{}]: Failed", user.getUsername(), recommenderName, e);
                    appEventPublisher.publishEvent(RecommenderTaskNotificationEvent
                            .builder(this, getProject(), user.getUsername()) //
                            .withMessage(new LogMessage(this, ERROR, e.getMessage())) //
                            .build());
                }
            }

            recommendationService.setEvaluatedRecommenders(user, layer, evaluatedRecommenders);

            appEventPublisher
                    .publishEvent(
                            RecommenderTaskNotificationEvent
                                    .builder(this, getProject(), user.getUsername()) //
                                    .withMessage(LogMessage.info(this,
                                            "Activation of non-trainable recommenders complete"))
                                    .build());
        }
    }

    private Optional<EvaluatedRecommender> considerRecommender(User user, Recommender recommender)
        throws RecommendationException, ConcurrentException
    {
        var optFactory = recommendationService.getRecommenderFactory(recommender);
        if (optFactory.isEmpty()) {
            sendMissingFactoryNotification(user, recommender);
            return Optional.empty();
        }

        var factory = optFactory.get();
        if (!factory.accepts(recommender.getLayer(), recommender.getFeature())) {
            return Optional.of(skipRecommenderWithInvalidSettings(user, recommender));
        }

        RecommendationEngine engine = factory.build(recommender);
        if (TRAINING_NOT_SUPPORTED == engine.getTrainingCapability()) {
            return Optional.of(activateNonTrainableRecommender(user, recommender, engine));
        }

        return Optional.of(skipTrainableRecommender(user, recommender));
    }

    private EvaluatedRecommender activateNonTrainableRecommender(User user, Recommender recommender,
            RecommendationEngine aEngine)
    {
        RecommenderContext ctx = aEngine
                .newContext(recommendationService.getContext(user.getUsername(), recommender)
                        .orElse(RecommenderContext.emptyContext()));
        ctx.setUser(user);
        ctx.close();
        recommendationService.putContext(user, recommender, ctx);

        String recommenderName = recommender.getName();
        LOG.debug("[{}][{}]: Activating [{}] non-trainable recommender", user.getUsername(),
                recommenderName, recommenderName);
        info("Recommender [%s] activated because it is not trainable", recommenderName);
        return EvaluatedRecommender.makeActiveWithoutEvaluation(recommender);
    }

    private EvaluatedRecommender skipTrainableRecommender(User user, Recommender recommender)
    {
        String recommenderName = recommender.getName();
        LOG.debug(
                "[{}][{}]: Recommender requires training - deferring activation to selection task",
                user.getUsername(), recommenderName);
        info("Recommender [%s] requires training - deferring activation to selection task",
                recommenderName);
        return EvaluatedRecommender.makeInactiveWithoutEvaluation(recommender, "Requires training");
    }

    private EvaluatedRecommender skipRecommenderWithInvalidSettings(User user,
            Recommender recommender)
    {
        String recommenderName = recommender.getName();
        LOG.info("[{}][{}]: Recommender configured with invalid layer or feature "
                + "- skipping recommender", user.getUsername(), recommenderName);
        info("Recommender [%s] configured with invalid layer or feature - skipping recommender",
                recommenderName);
        return EvaluatedRecommender.makeInactiveWithoutEvaluation(recommender,
                "Invalid layer or feature");
    }

    private void sendMissingFactoryNotification(User user, Recommender recommender)
    {
        LOG.error("[{}][{}]: No recommender factory available for [{}]", user.getUsername(),
                recommender.getName(), recommender.getTool());
        appEventPublisher.publishEvent(
                RecommenderTaskNotificationEvent.builder(this, getProject(), user.getUsername()) //
                        .withMessage(LogMessage.error(this,
                                "No recommender factory available for %s", recommender.getTool())) //
                        .build());
    }

    private Optional<Recommender> freshenRecommender(User aUser, Recommender r)
    {
        // Make sure we have the latest recommender config from the DB - the one from
        // the active recommenders list may be outdated
        Recommender recommender;
        try {
            recommender = recommendationService.getRecommender(r.getId());
        }
        catch (NoResultException e) {
            LOG.info("[{}][{}]: Recommender no longer available - skipping", aUser.getUsername(),
                    r.getName());
            return Optional.empty();
        }

        if (!recommender.isEnabled()) {
            LOG.debug("[{}][{}]: Recommender is disabled - skipping", aUser.getUsername(),
                    recommender.getName());
            return Optional.empty();
        }

        return Optional.of(recommender);
    }

    public static Builder<Builder<?>> builder()
    {
        return new Builder<>();
    }

    public static class Builder<T extends Builder<?>>
        extends RecommendationTask_ImplBase.Builder<T>
    {
        public NonTrainableRecommenderActivationTask build()
        {
            return new NonTrainableRecommenderActivationTask(this);
        }
    }
}
