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

import io.fabric8.gateway.loadbalancer.LoadBalancer;
import io.fabric8.kubernetes.api.model.PodSchema;
import io.fabric8.jube.local.EntityListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;

/**
 * Represents a Kubernetes proxy for a single service on a single port
 */
public class ServiceProxy implements EntityListener<PodSchema> {
    private static final transient Logger LOG = LoggerFactory.getLogger(ServiceProxy.class);

    private final Vertx vertx;
    private final Service service;
    private final int port;
    private final Handler<NetSocket> handler;
    private String host;
    private NetServer server;

    public ServiceProxy(Vertx vertx, Service service, int port, LoadBalancer loadBalancer) {
        this.vertx = vertx;
        this.service = service;
        this.port = port;
        this.handler = new ServiceProxyHandler(vertx, service, loadBalancer);
    }

    @Override
    public String toString() {
        return "TcpGateway{"
                + "service='" + service + '\''
                + ", port=" + port
                + ", host='" + host + '\''
                + '}';
    }

    public void init() {
        server = vertx.createNetServer().connectHandler(handler);
        if (host != null) {
            LOG.info("Listening on port " + port + " and host " + host + " for service: " + service);
            System.out.println("Listening on port " + port + " and host " + host + " for service: " + service);
            server = server.listen(port, host);
        } else {
            LOG.info("Listening on port " + port + " for service: " + service);
            System.out.println("Listening on port " + port + " for service: " + service);
            server = server.listen(port);
        }

    }

    public void destroy() {
        server.close();
    }

    @Override
    public void entityChanged(String id, PodSchema entity) {
        service.entityChanged(id, entity);
    }

    @Override
    public void entityDeleted(String id) {
        service.entityDeleted(id);
    }

    // Properties
    //-------------------------------------------------------------------------

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Vertx getVertx() {
        return vertx;
    }

    public Service getService() {
        return service;
    }
}
