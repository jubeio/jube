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

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import io.fabric8.jube.KubernetesModel;
import io.fabric8.jube.apimaster.ApiMasterKubernetesModel;
import io.fabric8.jube.process.Installation;
import io.fabric8.jube.process.ProcessManager;
import io.fabric8.jube.replicator.Replicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitors the current local processes and updates the local model to indicate started or stopped processes
 */
@Singleton
public class ProcessMonitor {
    private static final transient Logger LOG = LoggerFactory.getLogger(Replicator.class);

    private final KubernetesModel model;
    private final ProcessManager processManager;
    private final long pollTime;
    private Timer timer = new Timer();

    @Inject
    public ProcessMonitor(ApiMasterKubernetesModel model,
                          ProcessManager processManager,
                          @ConfigProperty(name = "processMonitor_pollTime", defaultValue = "2000")
                          long pollTime) {
        this.model = model;
        this.processManager = processManager;
        this.pollTime = pollTime;

        System.out.println("Starting the process monitor with poll time: " + pollTime);

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                LOG.debug("process monitor timer");
                processMonitor();
            }
        };
        timer.schedule(timerTask, pollTime, pollTime);
    }

    protected void processMonitor() {
        ImmutableMap<String, Installation> map = processManager.listInstallationMap();
        ImmutableSet<Map.Entry<String, Installation>> entries = map.entrySet();
        ImmutableMap<String, PodCurrentContainer> podRunningContainers = model.getPodRunningContainers(model);

        for (Map.Entry<String, Installation> entry : entries) {
            final String id = entry.getKey();
            Installation installation = entry.getValue();
            Long pid = null;
            try {
                pid = installation.getActivePid();
            } catch (IOException e) {
                LOG.warn("Failed to access pid for " + id + ". " + e, e);
            }
            final boolean alive = pid != null && pid.longValue() > 0;

            final PodCurrentContainer podCurrentContainer = podRunningContainers.get(id);
            if (podCurrentContainer == null) {
                File installDir = installation.getInstallDir();
                if (installDir.exists()) {
                    System.out.println("No pod container for id: " + id);
                } else {
                    processManager.uninstall(installation);
                }
            } else {
                // lets mark the container as running or not...
                NodeHelper.podTransaction(model, podCurrentContainer.getPod(), new Runnable() {
                    @Override
                    public void run() {
                        podCurrentContainer.containerAlive(id, alive);
                    }
                });
            }
        }
    }

    @PreDestroy
    public void destroy() {
        if (timer != null) {
            timer.purge();
            timer.cancel();
        }
    }

    public long getPollTime() {
        return pollTime;
    }

    public KubernetesModel getModel() {
        return model;
    }
}
