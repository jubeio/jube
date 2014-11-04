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
package io.fabric8.jube.apimaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.CurrentState;
import io.fabric8.kubernetes.api.model.PodListSchema;
import io.fabric8.kubernetes.api.model.PodSchema;
import io.fabric8.kubernetes.api.model.ReplicationControllerListSchema;
import io.fabric8.kubernetes.api.model.ReplicationControllerSchema;
import io.fabric8.kubernetes.api.model.ServiceListSchema;
import io.fabric8.kubernetes.api.model.ServiceSchema;
import io.fabric8.utils.Filter;
import io.fabric8.utils.Strings;
import io.fabric8.zookeeper.ZkPath;
import io.fabric8.zookeeper.utils.ZooKeeperUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.zookeeper.data.Stat;
import io.fabric8.jube.KubernetesModel;
import io.fabric8.jube.local.EntityListener;
import io.fabric8.jube.local.EntityListenerList;
import io.fabric8.jube.local.LocalKubernetesModel;
import io.fabric8.jube.local.NodeHelper;
import io.fabric8.jube.local.PodCurrentContainer;
import io.fabric8.jube.model.HostNode;
import io.fabric8.jube.model.HostNodeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mirrors ZooKeeper data into a local in memory model and all updates to the model are written directly to ZooKeeper
 */
public class ApiMasterKubernetesModel implements KubernetesModel {
    private static final transient Logger LOG = LoggerFactory.getLogger(ApiMasterKubernetesModel.class);

    private final LocalKubernetesModel memoryModel = new LocalKubernetesModel();
    private final CuratorFramework curator;
    private final HostNodeModel hostNodeModel;
    private final ExecutorService treeCacheExecutor = Executors.newSingleThreadExecutor();

    private final PathChildrenCacheListener treeListener = new PathChildrenCacheListener() {
        @Override
        public void childEvent(CuratorFramework curatorFramework, PathChildrenCacheEvent event) throws Exception {
            treeCacheEvent(event);
        }
    };

    private final TreeCache treeCache;
    private final String zkPath;

    private final EntityListenerList<PodSchema> podListeners = new EntityListenerList<>();
    private final EntityListenerList<ReplicationControllerSchema> replicationControllerListeners = new EntityListenerList<>();
    private final EntityListenerList<ServiceSchema> serviceListeners = new EntityListenerList<>();

    @Singleton
    @Inject
    public ApiMasterKubernetesModel(CuratorFramework curator, HostNodeModel hostNodeModel) throws Exception {
        this.curator = curator;
        this.hostNodeModel = hostNodeModel;
        this.zkPath = ZkPath.KUBERNETES_MODEL.getPath();
        this.treeCache = new TreeCache(curator, zkPath, true, false, true, treeCacheExecutor);
        this.treeCache.start(TreeCache.StartMode.NORMAL);
        this.treeCache.getListenable().addListener(treeListener);
    }

    // Add and remove listeners
    //-------------------------------------------------------------------------

    public void addPodListener(EntityListener<PodSchema> listener) {
        podListeners.addListener(listener);
    }

    public void removePodListener(EntityListener<PodSchema> listener) {
        podListeners.removeListener(listener);
    }

    public void addReplicationControllerListener(EntityListener<ReplicationControllerSchema> listener) {
        replicationControllerListeners.addListener(listener);
    }

    public void removeReplicationControllerListener(EntityListener<ReplicationControllerSchema> listener) {
        replicationControllerListeners.removeListener(listener);
    }

    public void addServiceListener(EntityListener<ServiceSchema> listener) {
        serviceListeners.addListener(listener);
    }

    public void removeServiceListener(EntityListener<ServiceSchema> listener) {
        serviceListeners.removeListener(listener);
    }

    // Updating API which just writes to ZK and waits for ZK watches to update in memory
    // -------------------------------------------------------------------------
    @Override
    public PodSchema deletePod(String podId) {
        if (Strings.isNotBlank(podId)) {
            PodSchema answer = memoryModel.deletePod(podId);
            deleteEntity(zkPathForPod(podId));
            return answer;
        } else {
            return null;
        }
    }

    @Override
    public void updatePod(String id, PodSchema entity) {
        writeEntity(zkPathForPod(id), entity);
        // memoryModel.updatePod(id, entity);
    }

    @Override
    public boolean updatePodIfNotExist(String id, PodSchema entity) {
        if (memoryModel.updatePodIfNotExist(id, entity)) {
            // lets not write it yet - we're just doing this to set a unique ID
            return true;
        }
        return false;
    }

    @Override
    public void updateService(String id, ServiceSchema entity) {
        writeEntity(zkPathForService(id), entity);
        // memoryModel.updateService(id, entity);
    }

    @Override
    public void deleteService(String id) {
        deleteEntity(zkPathForService(id));
        //memoryModel.deleteService(id);
    }

    @Override
    public void updateReplicationController(String id, ReplicationControllerSchema entity) {
        writeEntity(zkPathForReplicationController(id), entity);
        //memoryModel.updateReplicationController(id, entity);
    }

    @Override
    public void deleteReplicationController(String id) {
        deleteEntity(zkPathForReplicationController(id));
        // memoryModel.deleteReplicationController(id);
    }

    // Reading API from memory
    // -------------------------------------------------------------------------

    @Override
    public String getOrCreateId(String id, String kind) {
        return memoryModel.getOrCreateId(id, kind);
    }

    @Override
    public ImmutableMap<String, PodSchema> getPodMap() {
        return memoryModel.getPodMap();
    }

    @Override
    public PodListSchema getPods() {
        return memoryModel.getPods();
    }

    @Override
    public ImmutableList<PodSchema> getPods(Map<String, String> replicaSelector) {
        return memoryModel.getPods(replicaSelector);
    }

    @Override
    public ImmutableList<PodSchema> getPods(Filter<PodSchema> podFilter) {
        return memoryModel.getPods(podFilter);
    }

    @Override
    public PodSchema getPod(String id) {
        return memoryModel.getPod(id);
    }

    @Override
    public ImmutableMap<String, PodCurrentContainer> getPodRunningContainers(KubernetesModel model) {
        return memoryModel.getPodRunningContainers(model);
    }

    @Override
    public ReplicationControllerSchema getReplicationController(String id) {
        return memoryModel.getReplicationController(id);
    }

    @Override
    public ReplicationControllerListSchema getReplicationControllers() {
        return memoryModel.getReplicationControllers();
    }

    @Override
    public ImmutableMap<String, ReplicationControllerSchema> getReplicationControllerMap() {
        return memoryModel.getReplicationControllerMap();
    }

    @Override
    public ServiceListSchema getServices() {
        return memoryModel.getServices();
    }

    @Override
    public ServiceSchema getService(String id) {
        return memoryModel.getService(id);
    }

    @Override
    public ImmutableMap<String, ServiceSchema> getServiceMap() {
        return memoryModel.getServiceMap();
    }

    @Override
    public String createID(String kind) {
        return memoryModel.createID(kind);
    }

    // Load balancing API
    //-------------------------------------------------------------------------

    public String remoteCreatePod(PodSchema pod) {
        Exception failed = null;
        List<HostNode> hosts = new ArrayList<>(hostNodeModel.getMap().values());

        // lets try randomize the list
        int size = hosts.size();
        if (size <= 0) {
            throw new IllegalStateException("No host nodes available");
        }
        if (size == 1) {
            HostNode hostNode = hosts.get(0);
            try {
                return tryCreatePod(hostNode, pod);
            } catch (Exception e) {
                failed = e;
                LOG.error("Failed to create pod: " + pod.getId() + " on host: " + hostNode + ". " + e, e);
            }
        } else {
            Collections.shuffle(hosts);
            for (HostNode hostNode : hosts) {
                try {
                    return tryCreatePod(hostNode, pod);
                } catch (Exception e) {
                    failed = e;
                    LOG.error("Failed to create pod: " + pod.getId() + " on host: " + hostNode + ". " + e, e);
                }
            }
        }
        CurrentState currentState = NodeHelper.getOrCreateCurrentState(pod);
        currentState.setStatus("Terminated: " + failed);
        return null;
    }

    protected String tryCreatePod(HostNode hostNode, PodSchema pod) throws Exception {
        System.out.println("===== attempting to create pod on host: " + hostNode.getWebUrl());
        KubernetesExtensionsClient client = createClient(hostNode);
        return client.createLocalPod(pod);
    }

    public String deleteRemotePod(PodSchema pod) {
        List<HostNode> hosts = new ArrayList<>(hostNodeModel.getMap().values());
        for (HostNode hostNode : hosts) {
            try {
                return tryDeletePod(hostNode, pod);
            } catch (Exception e) {
                LOG.warn("Failed to delete pod on host " + hostNode.getWebUrl() + ". Might not be on that pod ;). " + e, e);
            }
        }
        return null;
    }

    protected String tryDeletePod(HostNode hostNode, PodSchema pod) throws Exception {
        String id = pod.getId();
        System.out.println("===== attempting to delete pod: " + id + " on host: " + hostNode.getWebUrl());
        KubernetesExtensionsClient client = createClient(hostNode);
        return client.deleteLocalPod(id);
    }


    private KubernetesExtensionsClient createClient(HostNode hostNode) {
        String webUrl = hostNode.getWebUrl();
        if (Strings.isNullOrBlank(webUrl)) {
            throw new IllegalArgumentException("No WebUrl so could not create client for host: " + hostNode);
        }
        return new KubernetesExtensionsClient(webUrl);
    }


    // Implementation methods
    //-------------------------------------------------------------------------
    protected String zkPathForPod(String id) {
        return zkPath + "/" + NodeHelper.KIND_POD + "-" + id;
    }

    protected String zkPathForService(String id) {
        return zkPath + "/" + NodeHelper.KIND_SERVICE + "-" + id;
    }

    protected String zkPathForReplicationController(String id) {
        return zkPath + "/" + NodeHelper.KIND_REPLICATION_CONTROLLER + "-" + id;
    }


    protected void writeEntity(String path, Object entity) {
        try {
            String json = KubernetesHelper.toJson(entity);
            System.out.println("Writing to path: " + path + " json: " + json);
            ZooKeeperUtils.setData(curator, path, json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update object at path: " + path + ". " + e, e);
        }
    }

    protected void deleteEntity(String path) {
        try {
            Stat stat = curator.checkExists().forPath(path);
            if (stat != null) {
                ZooKeeperUtils.delete(curator, path);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to delete object at path: " + path + ". " + e, e);
        }
    }

    protected void treeCacheEvent(PathChildrenCacheEvent event) {
        ChildData childData = event.getData();
        if (childData == null) {
            return;
        }
        String path = childData.getPath();
        PathChildrenCacheEvent.Type type = event.getType();
        byte[] data = childData.getData();
        if (data == null || data.length == 0 || path == null) {
            return;
        }
        if (path.startsWith(zkPath)) {
            path = path.substring(zkPath.length());
        }
        boolean remove = false;
        switch (type) {
        case CHILD_ADDED:
        case CHILD_UPDATED:
            break;
        case CHILD_REMOVED:
            remove = true;
            break;
        default:
            return;
        }
        try {
            Object dto = KubernetesHelper.loadJson(data);
            updateLocalModel(dto, remove);
        } catch (Exception e) {
            LOG.warn("Failed to parse the JSON: " + new String(data) + ". Reason: " + e, e);
        }
    }

    protected void updateLocalModel(Object dto, boolean remove) {
        if (dto instanceof PodSchema) {
            podChanged((PodSchema) dto, remove);
        } else if (dto instanceof ReplicationControllerSchema) {
            replicationControllerChanged((ReplicationControllerSchema) dto, remove);
        } else if (dto instanceof ServiceSchema) {
            serviceChanged((ServiceSchema) dto, remove);
        } else {
            System.out.println("Unrecognised DTO: " + dto);
        }
    }

    protected void podChanged(PodSchema entity, boolean remove) {
        if (remove) {
            String id = entity.getId();
            if (Strings.isNotBlank(id)) {
                memoryModel.deletePod(id);
                podListeners.entityDeleted(id);

            }
        } else {
            String id = memoryModel.getOrCreateId(entity.getId(), NodeHelper.KIND_POD);
            memoryModel.updatePod(id, entity);
            podListeners.entityChanged(id, entity);
        }
    }

    protected void replicationControllerChanged(ReplicationControllerSchema entity, boolean remove) {
        if (remove) {
            String id = entity.getId();
            if (Strings.isNotBlank(id)) {
                memoryModel.deleteReplicationController(id);
                replicationControllerListeners.entityDeleted(id);
            }
        } else {
            String id = memoryModel.getOrCreateId(entity.getId(), NodeHelper.KIND_REPLICATION_CONTROLLER);
            memoryModel.updateReplicationController(id, entity);
            replicationControllerListeners.entityChanged(id, entity);
        }
    }

    protected void serviceChanged(ServiceSchema entity, boolean remove) {
        if (remove) {
            String id = entity.getId();
            if (Strings.isNotBlank(id)) {
                memoryModel.deleteService(id);
                serviceListeners.entityDeleted(id);
            }
        } else {
            String id = memoryModel.getOrCreateId(entity.getId(), NodeHelper.KIND_SERVICE);
            memoryModel.updateService(id, entity);
            serviceListeners.entityChanged(id, entity);
        }
    }

}
