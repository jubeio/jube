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
package org.jboss.jube.local;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableSet;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ControllerCurrentState;
import io.fabric8.kubernetes.api.model.ControllerDesiredState;
import io.fabric8.kubernetes.api.model.CurrentState;
import io.fabric8.kubernetes.api.model.DesiredState;
import io.fabric8.kubernetes.api.model.Env;
import io.fabric8.kubernetes.api.model.Manifest;
import io.fabric8.kubernetes.api.model.ManifestContainer;
import io.fabric8.kubernetes.api.model.PodCurrentContainerInfo;
import io.fabric8.kubernetes.api.model.PodSchema;
import io.fabric8.kubernetes.api.model.PodTemplate;
import io.fabric8.kubernetes.api.model.PodTemplateDesiredState;
import io.fabric8.kubernetes.api.model.Port;
import io.fabric8.kubernetes.api.model.ReplicationControllerSchema;
import io.fabric8.kubernetes.api.model.Running;
import io.fabric8.kubernetes.api.model.ServiceSchema;
import io.fabric8.kubernetes.api.model.State;
import io.fabric8.utils.Objects;
import io.fabric8.utils.Strings;
import io.hawt.aether.OpenMavenURL;
import org.jboss.jube.KubernetesModel;
import org.jboss.jube.apimaster.ApiMasterService;
import org.jboss.jube.process.InstallOptions;
import org.jboss.jube.process.Installation;
import org.jboss.jube.process.ProcessController;
import org.jboss.jube.process.ProcessManager;
import org.jboss.jube.util.ImageMavenCoords;
import org.jboss.jube.util.InstallHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A set of helper functions for implementing the local node
 */
public final class NodeHelper {
    public static final String KIND_POD = "Pod";
    public static final String KIND_REPLICATION_CONTROLLER = "ReplicationController";
    public static final String KIND_SERVICE = "SERVICE";

    private static final transient Logger LOG = LoggerFactory.getLogger(NodeHelper.class);

    private NodeHelper() {
        // utility class
    }

    /**
     * Returns the desired state; lazily creating one if required
     */
    public static DesiredState getOrCreateDesiredState(PodSchema pod) {
        Objects.notNull(pod, "pod");
        DesiredState desiredState = pod.getDesiredState();
        if (desiredState == null) {
            desiredState = new DesiredState();
            pod.setDesiredState(desiredState);
        }
        return desiredState;
    }

    /**
     * Returns the current state of the given pod; lazily creating one if required
     */
    public static CurrentState getOrCreateCurrentState(PodSchema pod) {
        Objects.notNull(pod, "pod");
        CurrentState currentState = pod.getCurrentState();
        if (currentState == null) {
            currentState = new CurrentState();
            pod.setCurrentState(currentState);
        }
        return currentState;
    }

    public static PodTemplateDesiredState getPodTemplateDesiredState(ReplicationControllerSchema replicationController) {
        if (replicationController != null) {
            return getPodTemplateDesiredState(replicationController.getDesiredState());
        }
        return null;
    }

    public static PodTemplateDesiredState getPodTemplateDesiredState(ControllerDesiredState desiredState) {
        PodTemplate podTemplate;
        PodTemplateDesiredState podTemplateDesiredState = null;
        if (desiredState != null) {
            podTemplate = desiredState.getPodTemplate();
            if (podTemplate != null) {
                podTemplateDesiredState = podTemplate.getDesiredState();
            }
        }
        return podTemplateDesiredState;
    }

    /**
     * Returns the current container map for the current pod state; lazily creating if required
     */
    public static Map<String, PodCurrentContainerInfo> getOrCreateCurrentContainerInfo(PodSchema pod) {
        CurrentState currentState = getOrCreateCurrentState(pod);
        Map<String, PodCurrentContainerInfo> info = currentState.getInfo();
        if (info == null) {
            info = new HashMap<>();
            currentState.setInfo(info);
        }
        return info;
    }

    /**
     * Returns the containers state, lazily creating any objects if required.
     */
    public static State getOrCreateContainerState(PodSchema pod, String containerName) {
        PodCurrentContainerInfo containerInfo = getOrCreateContainerInfo(pod, containerName);
        State state = containerInfo.getState();
        if (state == null) {
            state = new State();
            containerInfo.setState(state);
        }
        return state;
    }

    /**
     * Returns the container information for the given pod and container name, lazily creating as required
     */
    public static PodCurrentContainerInfo getOrCreateContainerInfo(PodSchema pod, String containerName) {
        Map<String, PodCurrentContainerInfo> map = getOrCreateCurrentContainerInfo(pod);
        PodCurrentContainerInfo containerInfo = map.get(containerName);
        if (containerInfo == null) {
            containerInfo = new PodCurrentContainerInfo();
            map.put(containerName, containerInfo);
        }
        return containerInfo;
    }

    /**
     * Creates any missing containers; updating the currentState with the new values.
     */
    public static String createMissingContainers(ProcessManager processManager, KubernetesModel model, PodSchema pod, CurrentState currentState, List<ManifestContainer> containers) throws Exception {
        Map<String, PodCurrentContainerInfo> currentContainers = KubernetesHelper.getCurrentContainers(currentState);

        for (ManifestContainer container : containers) {
            // TODO check if we already have a working container
            createContainer(processManager, model, container, pod, currentState);
        }
        return null;
    }

    /**
     * Converts a possibly null list of Env objects into a Map of environment variables
     */
    public static Map<String, String> createEnvironmentVariableMap(List<Env> envList) {
        Map<String, String> answer = new HashMap<>();
        if (envList != null) {
            for (Env env : envList) {
                String name = env.getName();
                String value = env.getValue();
                if (Strings.isNotBlank(name)) {
                    answer.put(name, value);
                }
            }
        }
        return answer;
    }


    public static List<Env> createEnvironmentVariables(Map<String, String> environment) {
        List<Env> answer = new ArrayList<>();
        Set<Map.Entry<String, String>> entries = environment.entrySet();
        for (Map.Entry<String, String> entry : entries) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (Strings.isNotBlank(key)) {
                Env env = new Env();
                env.setName(key);
                env.setValue(value);
                answer.add(env);
            }
        }
        return answer;
    }

    protected static void createContainer(ProcessManager processManager, KubernetesModel model, ManifestContainer container, PodSchema pod, CurrentState currentState) throws Exception {
        String containerName = container.getName();
        String image = container.getImage();
        Strings.notEmpty(image);
        OpenMavenURL mavenUrl = ImageMavenCoords.dockerImageToMavenURL(image);
        Objects.notNull(mavenUrl, "mavenUrl");

        LOG.info("Creating new container " + containerName + " from: " + mavenUrl);
        Map<String, String> envVarMap = createEnvironmentVariableMap(container.getEnv());
        // now lets copy in the service env vars...
        appendServiceEnvironmentVariables(envVarMap, model);
        LOG.info("Env variables are: " + envVarMap);
        InstallOptions.InstallOptionsBuilder builder = new InstallOptions.InstallOptionsBuilder().
                url(mavenUrl).environment(envVarMap);
        if (Strings.isNotBlank(containerName)) {
            builder = builder.name(containerName).id(containerName);
        }
        InstallOptions installOptions = builder.build();

        try {
            Installation installation = processManager.install(installOptions, null);
            File installDir = installation.getInstallDir();

            PodCurrentContainerInfo containerInfo = NodeHelper.getOrCreateContainerInfo(pod, containerName);

            LOG.info("Installed new process at: " + installDir);

            // TODO add a container to the current state
            ProcessController controller = installation.getController();
            controller.start();

            Long pid = controller.getPid();
            containerAlive(pod, containerName, pid != null && pid > 0);

            Map<String, String> environment = controller.getConfig().getEnvironment();
            container.setEnv(createEnvironmentVariables(environment));
            List<Port> installationPorts = createInstallationPorts(environment, installation);
            if (installationPorts != null) {
                container.setPorts(installationPorts);
            }
        } catch (Exception e) {
            currentState.setStatus("Terminated: " + e);
            System.out.println("ERROR: Failed to create pod: " + pod.getId() + ". " + e);
            e.printStackTrace();
            LOG.error("Failed to create pod: " + pod.getId() + ". " + e, e);
        }
    }

    protected static List<Port> createInstallationPorts(Map<String, String> environment, Installation installation) {
        try {
            List<Port> answer = new ArrayList<>();
            File installDir = installation.getInstallDir();
            Map<String, String> portMap = InstallHelper.readPortsFromDirectory(installDir);
            Set<Map.Entry<String, String>> entries = portMap.entrySet();
            for (Map.Entry<String, String> entry : entries) {
                String key = entry.getKey();
                String value = entry.getValue();
                String containerPort = value;
                String envVarName = InstallHelper.portNameToHostEnvVarName(key);
                String hostPort = environment.get(envVarName);
                if (Strings.isNullOrBlank(hostPort)) {
                    LOG.warn("Cannot find env var value for $" + envVarName + " so cannot define the host port");
                } else {
                    Port port = new Port();
                    port.setName(key);
                    int containerPortNumber = toPortNumber(value, key, "container");
                    int hostPortNumber = toPortNumber(hostPort, key, "host");
                    if (containerPortNumber > 0) {
                        port.setContainerPort(containerPortNumber);
                    }
                    if (hostPortNumber > 0) {
                        port.setHostPort(hostPortNumber);
                    }
                    answer.add(port);
                }
            }
            return answer;
        } catch (IOException e) {
            LOG.warn("Failed to load ports for installation " + installation + ". " + e, e);
            return null;
        }
    }

    protected static int toPortNumber(String text, String key, String description) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            LOG.warn("Failed to parse port text '" + text + "' for port " + key + " " + description + ". " + e, e);
            return -1;
        }
    }

    public static int findHostPortForService(PodSchema pod, int serviceContainerPort) {
        List<ManifestContainer> containers = KubernetesHelper.getContainers(pod);
        for (ManifestContainer container : containers) {
            List<Port> ports = container.getPorts();
            if (ports != null) {
                for (Port port : ports) {
                    Integer containerPort = port.getContainerPort();
                    Integer hostPort = port.getHostPort();
                    if (containerPort != null && containerPort == serviceContainerPort) {
                        if (hostPort != null) {
                            return hostPort;
                        }
                    }
                }
            }
        }
        LOG.warn("Could not find host port for service port: " + serviceContainerPort + " pod " + pod);
        return serviceContainerPort;
    }

    public static void appendServiceEnvironmentVariables(Map<String, String> map, KubernetesModel model) {
        ImmutableSet<Map.Entry<String, ServiceSchema>> entries = model.getServiceMap().entrySet();
        for (Map.Entry<String, ServiceSchema> entry : entries) {
            String id = entry.getKey().toUpperCase().replaceAll("-", "_");
            String envVarPrefix = id + "_SERVICE_";
            ServiceSchema service = entry.getValue();

            // TODO should we allow different service ports?
            String host = ApiMasterService.getHostName();
            Integer port = service.getPort();
            if (port != null) {
                String hostEnvVar = envVarPrefix + "HOST";
                String portEnvVar = envVarPrefix + "PORT";

                map.put(hostEnvVar, host);
                map.put(portEnvVar, "" + port);
            }
        }
    }

    public static void deleteContainers(ProcessManager processManager, KubernetesModel model, PodSchema pod, CurrentState currentState, List<ManifestContainer> desiredContainers) throws Exception {
        for (ManifestContainer container : desiredContainers) {
            deleteContainer(processManager, model, container, pod, currentState);
        }
    }

    protected static void deleteContainer(ProcessManager processManager, KubernetesModel model, ManifestContainer container, PodSchema pod, CurrentState currentState) throws Exception {
        String containerName = container.getName();
        Installation installation = processManager.getInstallation(containerName);
        if (installation == null) {
            System.out.println("No such container: " + containerName);
            return;
        }
        ProcessController controller = installation.getController();
        controller.kill();
        controller.uninstall();
        model.deletePod(pod.getId());
    }


    public static void containerAlive(PodSchema pod, String id, boolean alive) {
        CurrentState currentState = getOrCreateCurrentState(pod);
        String status = currentState.getStatus();
        if (alive) {
            currentState.setStatus("Running");
        } else {
            if (Strings.isNullOrBlank(status)) {
                currentState.setStatus("Waiting");
            } else {
                currentState.setStatus("Terminated");
            }
        }
        State state = getOrCreateContainerState(pod, id);
        if (alive) {
            Running running = new Running();
            state.setRunning(running);
        } else {
            state.setRunning(null);
        }
    }

    public static ManifestContainer addOrUpdateDesiredContainer(PodSchema pod, String containerName, ManifestContainer container) {
        List<ManifestContainer> containers = getOrCreatePodDesiredContainers(pod);
        ManifestContainer oldContainer = findContainer(containers, containerName);
        if (oldContainer != null) {
            // lets update it just in case something changed...
            containers.remove(oldContainer);
        }
        ManifestContainer newContainer = new ManifestContainer();

        // TODO we should use bean utils or something to copy properties in case we miss one!
        newContainer.setCommand(container.getCommand());
        newContainer.setEnv(container.getEnv());
        newContainer.setImage(container.getImage());
        newContainer.setPorts(container.getPorts());
        newContainer.setVolumeMounts(container.getVolumeMounts());
        newContainer.setWorkingDir(container.getWorkingDir());
        newContainer.getAdditionalProperties().putAll(container.getAdditionalProperties());
        newContainer.setName(containerName);
        System.out.println("Added new container: " + containerName);

        containers.add(newContainer);
        return newContainer;
    }

    public static List<ManifestContainer> getOrCreatePodDesiredContainers(PodSchema pod) {
        DesiredState podDesiredState = NodeHelper.getOrCreateDesiredState(pod);
        Manifest manifest = podDesiredState.getManifest();
        if (manifest == null) {
            manifest = new Manifest();
            podDesiredState.setManifest(manifest);
        }
        List<ManifestContainer> containers = manifest.getContainers();
        if (containers == null) {
            containers = new ArrayList<>();
            manifest.setContainers(containers);
        }
        return containers;
    }

    public static ManifestContainer findContainer(List<ManifestContainer> containers, String name) {
        for (ManifestContainer container : containers) {
            if (Objects.equal(container.getName(), name)) {
                return container;
            }
        }
        return null;
    }

    public static ControllerCurrentState getOrCreateCurrentState(ReplicationControllerSchema replicationController) {
        ControllerCurrentState currentState = replicationController.getCurrentState();
        if (currentState == null) {
            currentState = new ControllerCurrentState();
            replicationController.setCurrentState(currentState);
        }
        return currentState;
    }

    public static void deletePod(ProcessManager processManager, KubernetesModel model, String podId) throws Exception {
        PodSchema pod = model.deletePod(podId);
        if (pod != null) {
            List<ManifestContainer> desiredContainers = NodeHelper.getOrCreatePodDesiredContainers(pod);
            NodeHelper.deleteContainers(processManager, model, pod, NodeHelper.getOrCreateCurrentState(pod), desiredContainers);
        }
    }

    /**
     * Performs a block of code and updates the pod model if its updated
     */
    public static void podTransaction(KubernetesModel model, PodSchema pod, Runnable task) {
        String oldJson = getPodJson(pod);
        task.run();
        String newJson = getPodJson(pod);

        // lets only update the model if we've really changed the pod
        if (!java.util.Objects.equals(oldJson, newJson)) {
            model.updatePod(pod.getId(), pod);
        }
    }

    protected static String getPodJson(PodSchema pod) {
        try {
            return KubernetesHelper.toJson(pod);
        } catch (JsonProcessingException e) {
            LOG.warn("Could not convert pod to json: " + e, e);
            return null;
        }
    }

}
