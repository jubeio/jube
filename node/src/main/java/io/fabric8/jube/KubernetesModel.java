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
package io.fabric8.jube;

import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.PodListSchema;
import io.fabric8.kubernetes.api.model.PodSchema;
import io.fabric8.kubernetes.api.model.ReplicationControllerListSchema;
import io.fabric8.kubernetes.api.model.ReplicationControllerSchema;
import io.fabric8.kubernetes.api.model.ServiceListSchema;
import io.fabric8.kubernetes.api.model.ServiceSchema;
import io.fabric8.utils.Filter;
import io.fabric8.jube.local.PodCurrentContainer;

/**
 */
public interface KubernetesModel {
    ImmutableMap<String, PodSchema> getPodMap();

    PodListSchema getPods();

    ImmutableList<PodSchema> getPods(Map<String, String> replicaSelector);

    ImmutableList<PodSchema> getPods(Filter<PodSchema> podFilter);

    PodSchema getPod(String id);

    void updatePod(String id, PodSchema pod);

    String getOrCreateId(String id, String kind);

    boolean updatePodIfNotExist(String id, PodSchema pod);

    PodSchema deletePod(String podId);

    ImmutableMap<String, PodCurrentContainer> getPodRunningContainers(KubernetesModel model);

    ReplicationControllerSchema getReplicationController(String id);

    ReplicationControllerListSchema getReplicationControllers();

    ImmutableMap<String, ReplicationControllerSchema> getReplicationControllerMap();

    void updateReplicationController(String id, ReplicationControllerSchema replicationController);

    void deleteReplicationController(String controllerId);

    ServiceListSchema getServices();

    ServiceSchema getService(String id);

    ImmutableMap<String, ServiceSchema> getServiceMap();

    void updateService(String id, ServiceSchema entity);

    void deleteService(String serviceId);

    String createID(String kind);
}
