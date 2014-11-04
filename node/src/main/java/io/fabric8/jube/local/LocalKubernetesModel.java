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
package io.fabric8.jube.local;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.UUID.randomUUID;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ManifestContainer;
import io.fabric8.kubernetes.api.model.PodCurrentContainerInfo;
import io.fabric8.kubernetes.api.model.PodListSchema;
import io.fabric8.kubernetes.api.model.PodSchema;
import io.fabric8.kubernetes.api.model.ReplicationControllerListSchema;
import io.fabric8.kubernetes.api.model.ReplicationControllerSchema;
import io.fabric8.kubernetes.api.model.ServiceListSchema;
import io.fabric8.kubernetes.api.model.ServiceSchema;
import io.fabric8.utils.Filter;
import io.fabric8.utils.Filters;
import io.hawt.util.Strings;
import io.fabric8.jube.KubernetesModel;


/**
 * A pure in memory implementation of the {@link KubernetesModel}
 */
public class LocalKubernetesModel implements KubernetesModel {
    private ConcurrentHashMap<String, PodSchema> podMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, ReplicationControllerSchema> replicationControllerMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, ServiceSchema> serviceMap = new ConcurrentHashMap<>();

    public LocalKubernetesModel() {
    }

    // Pods
    //-------------------------------------------------------------------------

    @Override
    public ImmutableMap<String, PodSchema> getPodMap() {
        return ImmutableMap.copyOf(podMap);
    }


    @Override
    public PodListSchema getPods() {
        PodListSchema answer = new PodListSchema();
        answer.setItems(Lists.newArrayList(podMap.values()));
        return answer;
    }

    @Override
    public ImmutableList<PodSchema> getPods(Map<String, String> replicaSelector) {
        return getPods(KubernetesHelper.createPodFilter(replicaSelector));
    }

    @Override
    public ImmutableList<PodSchema> getPods(Filter<PodSchema> podFilter) {
        return ImmutableList.copyOf(Filters.filter(getPodMap().values(), podFilter));
    }

    @Override
    public PodSchema getPod(String id) {
        return podMap.get(id);
    }

    @Override
    public void updatePod(String id, PodSchema pod) {
        id = getOrCreateId(id, NodeHelper.KIND_POD);

        // lets make sure that for each container we have a current container created
        Map<String, PodCurrentContainerInfo> info = NodeHelper.getOrCreateCurrentContainerInfo(pod);

        List<ManifestContainer> containers = KubernetesHelper.getContainers(pod);
        for (ManifestContainer container : containers) {
            String name = getOrCreateId(container.getName(), NodeHelper.KIND_POD);
            container.setName(name);
            PodCurrentContainerInfo containerInfo = info.get(name);
            if (containerInfo == null) {
                containerInfo = new PodCurrentContainerInfo();
                info.put(name, containerInfo);
            }
        }
        podMap.put(id, pod);
    }

    @Override
    public String getOrCreateId(String id, String kind) {
        if (Strings.isBlank(id)) {
            id = createID(kind);
        }
        return id;
    }


    /**
     * Updates the pod if one does not already exist
     */
    @Override
    public boolean updatePodIfNotExist(String id, PodSchema pod) {
        PodSchema oldValue = podMap.putIfAbsent(id, pod);
        return oldValue == null;
    }

    /**
     * Removes the pods from the model.
     * <p/>
     * <b>Note</b> you should make sure to delete any container processes too!
     */
    @Override
    public PodSchema deletePod(String podId) {
        if (Strings.isBlank(podId)) {
            return null;
        }
        return podMap.remove(podId);
    }

    /**
     * Returns all the current containers and their pods
     */
    @Override
    public ImmutableMap<String, PodCurrentContainer> getPodRunningContainers(KubernetesModel model) {
        Map<String, PodCurrentContainer> answer = new HashMap<>();
        for (Map.Entry<String, PodSchema> entry : podMap.entrySet()) {
            String podId = entry.getKey();
            PodSchema podSchema = entry.getValue();
            Map<String, PodCurrentContainerInfo> currentContainers = KubernetesHelper.getCurrentContainers(podSchema);
            for (Map.Entry<String, PodCurrentContainerInfo> containerEntry : currentContainers.entrySet()) {
                String containerId = containerEntry.getKey();
                PodCurrentContainerInfo currentContainer = containerEntry.getValue();
                PodCurrentContainer podCurrentContainer = new PodCurrentContainer(model, podId, podSchema, containerId, currentContainer);
                answer.put(containerId, podCurrentContainer);
            }
        }
        return ImmutableMap.copyOf(answer);
    }


    // Replication Controllers
    //-------------------------------------------------------------------------

    @Override
    public ReplicationControllerSchema getReplicationController(String id) {
        return replicationControllerMap.get(id);
    }

    @Override
    public ReplicationControllerListSchema getReplicationControllers() {
        ReplicationControllerListSchema answer = new ReplicationControllerListSchema();
        answer.setItems(Lists.newArrayList(replicationControllerMap.values()));
        return answer;
    }

    @Override
    public ImmutableMap<String, ReplicationControllerSchema> getReplicationControllerMap() {
        return ImmutableMap.copyOf(replicationControllerMap);
    }

    @Override
    public void updateReplicationController(String id, ReplicationControllerSchema replicationController) {
        id = getOrCreateId(id, NodeHelper.KIND_REPLICATION_CONTROLLER);
        replicationControllerMap.put(id, replicationController);
    }

    @Override
    public void deleteReplicationController(String controllerId) {
        replicationControllerMap.remove(controllerId);
        System.out.println("Deleted replicationController " + controllerId + ". Now has " + replicationControllerMap.size() + " service(s)");
    }


    // Services
    //-------------------------------------------------------------------------

    @Override
    public ServiceListSchema getServices() {
        ServiceListSchema answer = new ServiceListSchema();
        answer.setItems(Lists.newArrayList(serviceMap.values()));
        return answer;
    }

    @Override
    public ServiceSchema getService(String id) {
        return serviceMap.get(id);
    }

    @Override
    public ImmutableMap<String, ServiceSchema> getServiceMap() {
        return ImmutableMap.copyOf(serviceMap);
    }

    @Override
    public void updateService(String id, ServiceSchema entity) {
        id = getOrCreateId(id, NodeHelper.KIND_SERVICE);
        serviceMap.put(id, entity);
    }

    @Override
    public void deleteService(String serviceId) {
        serviceMap.remove(serviceId);
        System.out.println("Deleted service " + serviceId + ". Now has " + serviceMap.size() + " service(s)");

    }

    // Other stuff
    //-------------------------------------------------------------------------

    /**
     * Creates a new ID for the given kind
     */
    @Override
    public String createID(String kind) {
        return kind + "-" + randomUUID().toString();
    }

}
