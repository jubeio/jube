/**
 *  Copyright 2005-2014 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.jube.main;

import java.util.HashSet;
import java.util.Set;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.apache.cxf.feature.LoggingFeature;
import org.apache.cxf.jaxrs.swagger.SwaggerFeature;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import io.fabric8.jube.apimaster.ApiMasterService;

@ApplicationPath("/")
public class NodeApplication extends Application {

    @Produces
    private JacksonJsonProvider jacksonJsonProvider = new JacksonJsonProvider();

    @Inject
    private ApiMasterService apiMasterService;

    @Inject
    @ConfigProperty(name = "CXF_LOG_REQUESTS", defaultValue = "false")
    private boolean cxfLogRequests;

    @Override
    public Set<Object> getSingletons() {
        Set<Object> answer = new HashSet<Object>();
        answer.add(apiMasterService);
        answer.add(jacksonJsonProvider);
        answer.add(new SwaggerFeature());
/*
    TODO
        answer.add();
        answer.add(new EnableJMXFeature());
*/
        if (cxfLogRequests) {
            answer.add(new LoggingFeature());
        }
        return answer;
    }
}

