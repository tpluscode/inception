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
package de.tudarmstadt.ukp.clarin.webanno.brat.annotation;

import java.io.Serializable;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.request.Request;

import de.tudarmstadt.ukp.clarin.webanno.brat.config.BratAnnotationEditorProperties;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.LoadConfResponse;
import de.tudarmstadt.ukp.inception.diam.editor.actions.EditorAjaxRequestHandlerBase;
import de.tudarmstadt.ukp.inception.diam.model.ajax.AjaxResponse;

class LoadConfHandler
    extends EditorAjaxRequestHandlerBase
    implements Serializable
{
    private static final long serialVersionUID = 586794742935679178L;

    private final BratAnnotationEditorProperties bratProperties;

    public LoadConfHandler(BratAnnotationEditorProperties aBratProperties)
    {
        bratProperties = aBratProperties;
    }

    @Override
    public String getCommand()
    {
        return LoadConfResponse.COMMAND;
    }

    @Override
    public AjaxResponse handle(AjaxRequestTarget aTarget, Request aRequest)
    {
        return new LoadConfResponse(bratProperties);
    }
}
