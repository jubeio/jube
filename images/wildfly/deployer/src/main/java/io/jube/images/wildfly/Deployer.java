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
import org.jboss.as.controller.client.helpers.standalone.AddDeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.DeploymentAction;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlan;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentActionResult;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentPlanResult;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class Deployer {
    public static void main(String[] args) throws Throwable {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("Missing arguments!");
        }

        File rootDir = new File(args[0]);
        File mavenDir = new File(rootDir, "maven");
        if (mavenDir.exists() == false) {
            System.out.println("No maven dir, nothing to deploy.");
            return;
        }

        String protocol = findArg(args, "wildfly.protocol", "http-remoting");
        String address = findArg(args, "wildfly.address", "127.0.0.1");
        String port = findArg(args, "wildfly.port", "9990");

        String username = findArg(args, "wildfly.username", null);
        String password = findArg(args, "wildfly.password", null);

        String timeout = findArg(args, "timeout", "5000");
        Thread.sleep(Integer.parseInt(timeout)); // lets wait for WF to boot up

        ModelControllerClient client;
        if (username != null && password != null) {
            System.out.println(String.format("Connecting with %s/%s", username, password));
            SimpleCallbackHandler.username = username;
            SimpleCallbackHandler.password = password;
            client = ModelControllerClient.Factory.create(protocol, address, Integer.parseInt(port), new SimpleCallbackHandler());
        } else {
            System.out.println("No auth used.");
            client = ModelControllerClient.Factory.create(protocol, address, Integer.parseInt(port));
        }
        ServerDeploymentManager deploymentManager = ServerDeploymentManager.Factory.create(client);

        for (File file : mavenDir.listFiles()) {
            try (InputStream fs = new FileInputStream(file)) {
                deploy(deploymentManager, file.getName(), fs);
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
