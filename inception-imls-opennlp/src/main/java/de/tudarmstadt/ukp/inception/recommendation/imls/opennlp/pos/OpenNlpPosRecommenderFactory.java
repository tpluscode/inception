/*
 * Copyright 2018
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.recommendation.imls.opennlp.pos;

import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.SPAN_TYPE;
import static de.tudarmstadt.ukp.clarin.webanno.model.AnchoringMode.SINGLE_TOKEN;

import org.apache.uima.cas.CAS;
import org.springframework.stereotype.Component;

import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngine;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngineFactoryImplBase;

@Component
public class OpenNlpPosRecommenderFactory
    extends RecommendationEngineFactoryImplBase<OpenNlpPosRecommenderTraits>
{
    // This is a string literal so we can rename/refactor the class without it changing its ID
    // and without the database starting to refer to non-existing recommendation tools.
    public static final String ID = 
            "de.tudarmstadt.ukp.inception.recommendation.imls.opennlp.pos.OpenNlpPosClassificationTool";

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public RecommendationEngine build(Recommender aRecommender)
    {
        return new OpenNlpPosRecommender(aRecommender, readTraits(aRecommender));
    }

    @Override
    public String getName()
    {
        return "Token Sequence Classifier (OpenNLP POS)";
    }

    @Override
    public boolean accepts(AnnotationLayer aLayer, AnnotationFeature aFeature)
    {
        if (aLayer == null || aFeature == null) {
            return false;
        }
        
        return SINGLE_TOKEN.equals(aLayer.getAnchoringMode()) && 
                SPAN_TYPE.equals(aLayer.getType()) && 
                CAS.TYPE_NAME_STRING.equals(aFeature.getType());
    }

    @Override
    public OpenNlpPosRecommenderTraits createTraits()
    {
        return new OpenNlpPosRecommenderTraits();
    }
    
    @Override
    public boolean isMultipleRecommendationProvider()
    {
        return true;
    }
}
