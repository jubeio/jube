/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.jube.apimaster;

import io.fabric8.kubernetes.api.KubernetesClient;
import io.fabric8.kubernetes.api.KubernetesFactory;
import io.fabric8.kubernetes.api.model.PodSchema;

import javax.validation.constraints.NotNull;

/**
 * A simple client for working with {@link KubernetesExtensions}
 */
public class KubernetesExtensionsClient extends KubernetesClient {
    private KubernetesExtensions extensions;

    public KubernetesExtensionsClient() {
    }

    public KubernetesExtensionsClient(String url) {
        super(url);
    }

    public KubernetesExtensionsClient(KubernetesFactory factory) {
        super(factory);
    }

    public KubernetesExtensions getExtensions() {
        if (extensions == null) {
            extensions = getFactory().createWebClient(KubernetesExtensions.class);
        }
        return extensions;
    }

    // Delegate API
    //-------------------------------------------------------------------------


    public String createLocalPod(PodSchema entity) throws Exception {
        return getExtensions().createLocalPod(entity);
    }

    public String deleteLocalPod(@NotNull String podId) throws Exception {
        return getExtensions().deleteLocalPod(podId);
    }
}
