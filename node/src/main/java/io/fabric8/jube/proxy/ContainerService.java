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
package io.fabric8.jube.proxy;

import java.net.URI;
import java.net.URISyntaxException;

import io.fabric8.kubernetes.api.model.CurrentState;
import io.fabric8.kubernetes.api.model.PodSchema;
import io.hawt.util.Strings;
import io.fabric8.jube.local.NodeHelper;

/**
 * Represents a single service implementation in a container
 */
public class ContainerService {
    private final PodSchema pod;
    private final URI uri;

    public ContainerService(Service service, PodSchema pod) throws URISyntaxException {
        this.pod = pod;

        int serviceContainerPort = service.getContainerPort();
        int port = NodeHelper.findHostPortForService(pod, serviceContainerPort);

        // lets get host / port of the container
        String host = null;
        CurrentState currentState = pod.getCurrentState();
        if (currentState != null) {
            host = currentState.getHost();
            if (Strings.isBlank(host)) {
                host = currentState.getPodIP();
            }
        }
        if (Strings.isBlank(host)) {
            throw new IllegalArgumentException("No host for pod " + pod.getId() + " so cannot use it with service " + service.getId());
        } else {
            uri = new URI("tcp://" + host + ":" + port);
        }
    }

    @Override
    public String toString() {
        return "ContainerService{"
                + "pod=" + pod.getId()
                + ", uri=" + uri
                + '}';
    }

    public URI getURI() {
        return uri;
    }
}
