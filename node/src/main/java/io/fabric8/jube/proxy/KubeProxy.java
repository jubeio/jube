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

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.fabric8.gateway.loadbalancer.LoadBalancer;
import io.fabric8.kubernetes.api.model.PodSchema;
import io.fabric8.kubernetes.api.model.ServiceSchema;
import io.fabric8.jube.apimaster.ApiMasterKubernetesModel;
import io.fabric8.jube.local.EntityListener;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.impl.DefaultVertxFactory;

/**
 * Manages instances of {@link ServiceProxy} for each service that gets created or destroyed
 */
public class KubeProxy {
    private final Vertx vertx;
    private final ApiMasterKubernetesModel model;
    private Map<String, ServiceProxy> serviceMap = new ConcurrentHashMap<>();

    @Singleton
    @Inject
    public KubeProxy(ApiMasterKubernetesModel model) {
        this.model = model;
        this.vertx = DefaultVertxFactory.newVertx();

        model.addPodListener(new EntityListener<PodSchema>() {
            @Override
            public void entityChanged(String id, PodSchema entity) {
                Collection<ServiceProxy> services = getServices();
                for (ServiceProxy service : services) {
                    service.entityChanged(id, entity);
                }
            }

            @Override
            public void entityDeleted(String id) {
                Collection<ServiceProxy> services = getServices();
                for (ServiceProxy service : services) {
                    service.entityDeleted(id);
                }
            }
        });

        model.addServiceListener(new EntityListener<ServiceSchema>() {
            @Override
            public void entityChanged(String id, ServiceSchema entity) {
                serviceChanged(id, entity);
            }

            @Override
            public void entityDeleted(String id) {
                serviceDeleted(id);
            }
        });
    }

    protected synchronized void serviceChanged(String id, ServiceSchema serviceEntity) {
        // lets delete the old service as we may have changed the port or selector
        serviceDeleted(id);

        Service service = new Service(serviceEntity);
        int port = service.getPort();
        LoadBalancer loadBalancer = service.getLoadBalancer();
        ServiceProxy serviceProxy = new ServiceProxy(vertx, service, port, loadBalancer);
        serviceProxy.init();
        serviceMap.put(id, serviceProxy);

        // now lets populate it with the current pods
        ImmutableMap<String, PodSchema> podMap = model.getPodMap();
        ImmutableSet<Map.Entry<String, PodSchema>> entries = podMap.entrySet();
        for (Map.Entry<String, PodSchema> entry : entries) {
            String podId = entry.getKey();
            PodSchema pod = entry.getValue();
            serviceProxy.entityChanged(podId, pod);
        }
        System.out.println("Service now initialised as: " + service);
    }

    protected void serviceDeleted(String id) {
        ServiceProxy service = serviceMap.remove(id);
        if (service != null) {
            service.destroy();
        }
    }

    protected Collection<ServiceProxy> getServices() {
        return serviceMap.values();
    }
}
