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
package io.jube.images.wildfly;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.as.controller.client.helpers.standalone.AddDeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.DeploymentAction;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentActionResult;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentPlanResult;
import org.jboss.as.controller.client.impl.ClientConfigurationImpl;

public final class Deployer {

    private Deployer() {
        // run as main
    }

    public static void main(String[] args) throws Throwable {
        if (args == null || args.length == 0) {
            String app_base = System.getenv("APP_BASE");
            if (app_base == null) {
                throw new IllegalArgumentException("Missing arguments and no $APP_BASE exported!");
            }
            args = new String[1];
            args[0] = app_base;
        }

        File rootDir = new File(args[0]);
        File mavenDir = new File(rootDir, "maven");
        if (!mavenDir.exists()) {
            System.out.println(String.format("No maven dir [%s], nothing to deploy.", mavenDir));
            return;
        }

        String protocol = findArg(args, "wildfly.protocol", "http-remoting");
        String address = findArg(args, "wildfly.address", "127.0.0.1");
        int port = Integer.parseInt(findArg(args, "wildfly.port", System.getenv("MANAGEMENT_PORT")));

        String username = findArg(args, "wildfly.username", null);
        String password = findArg(args, "wildfly.password", null);

        int sleep = Integer.parseInt(findArg(args, "sleep", "3000"));
        Thread.sleep(sleep); // lets wait for WF to boot up

        int timeout = Integer.parseInt(findArg(args, "timeout", String.valueOf(60 * 1000)));

        ModelControllerClient client = null;
        try {
            ModelControllerClientConfiguration configuration;
            if (username != null && password != null) {
                System.out.println(String.format("Connecting with %s/%s", username, password));
                configuration = ClientConfigurationImpl.create(protocol, address, port, new SimpleCallbackHandler(username, password), null, timeout);
            } else {
                System.out.println("No auth used.");
                configuration = ClientConfigurationImpl.create(protocol, address, port, null, null, timeout);
            }
            client = ModelControllerClient.Factory.create(configuration);
            ServerDeploymentManager deploymentManager = ServerDeploymentManager.Factory.create(client);

            File[] files = mavenDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    try (InputStream fs = new FileInputStream(file)) {
                        deploy(deploymentManager, file.getName(), fs);
                    }
                }
            }
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    private static void deploy(ServerDeploymentManager deploymentManager, String runtimeName, InputStream input) throws Throwable {
        ServerDeploymentPlanResult planResult;
        List<DeploymentAction> actions = new ArrayList<>();
        DeploymentPlanBuilder builder = deploymentManager.newDeploymentPlan();
        AddDeploymentPlanBuilder addBuilder = builder.add(runtimeName, input);
        actions.add(addBuilder.getLastAction());
        builder = addBuilder.andDeploy();
        actions.add(builder.getLastAction());
        DeploymentPlan plan = builder.build();
        Future<ServerDeploymentPlanResult> future = deploymentManager.execute(plan);
        planResult = future.get();
        for (DeploymentAction action : actions) {
            ServerDeploymentActionResult actionResult = planResult.getDeploymentActionResult(action.getId());
            if (actionResult.getDeploymentException() != null) {
                throw actionResult.getDeploymentException();
            }
        }
    }

    private static String findArg(String[] args, String key, String defaultValue) {
        for (String arg : args) {
            if (arg.startsWith(key)) {
                return arg.split("=")[1];
            }
        }
        return defaultValue;
    }
}
