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
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import io.fabric8.gateway.loadbalancer.LoadBalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.streams.Pump;
import org.vertx.java.core.streams.ReadStream;

/**
 * A handler for a single service proxy
 */
public class ServiceProxyHandler implements Handler<NetSocket> {
    private static final transient Logger LOG = LoggerFactory.getLogger(ServiceProxyHandler.class);

    private final Vertx vertx;
    private final Service service;
    private final LoadBalancer loadBalancer;
    private final AtomicLong failedConnectionAttempts = new AtomicLong();

    public ServiceProxyHandler(Vertx vertx, Service service, LoadBalancer loadBalancer) {
        this.vertx = vertx;
        this.service = service;
        this.loadBalancer = loadBalancer;
    }

    @Override
    public void handle(final NetSocket clientSocket) {
        clientSocket.exceptionHandler(new Handler<Throwable>() {
            @Override
            public void handle(Throwable e) {
                handleConnectFailure(clientSocket, String.format("Failed to route to service '%s' from client '%s' due to: %s", service, clientSocket.remoteAddress(), e));
            }
        });
        clientSocket.endHandler(new Handler<Void>() {
            @Override
            public void handle(Void event) {
                handleConnectFailure(clientSocket, String.format("Client '%s' for service '%s' closed the connection before it could be routed.", clientSocket.remoteAddress(), service));
            }
        });
        clientSocket.pause();

        TcpClientRequestFacade requestFacade = new TcpClientRequestFacade(clientSocket);
        List<ContainerService> services = service.getContainerServices();
        if (!services.isEmpty()) {
            ContainerService containerService = loadBalancer.choose(services, requestFacade);
            if (containerService != null) {

                URI uri = containerService.getURI();

                NetClient netClient = vertx.createNetClient();
                final String host = uri.getHost();
                final int port = uri.getPort();
                LOG.info(String.format("Connecting client '%s' to service '%s' at %s:%d.", clientSocket.remoteAddress(), service, host, port));

                netClient.connect(port, host, new Handler<AsyncResult<NetSocket>>() {
                    public void handle(final AsyncResult<NetSocket> asyncSocket) {
                        final NetSocket serverSocket = asyncSocket.result();
                        if (serverSocket == null) {
                            handleConnectFailure(clientSocket, String.format("Could not connect client '%s' to service '%s' at %s:%d.", clientSocket.remoteAddress(), service, host, port));
                        } else {

                            Handler endHandler = new Handler() {
                                boolean closed;

                                @Override
                                public synchronized void handle(Object event) {
                                    if (!closed) {
                                        LOG.info(String.format("Disconnected client '%s' from service '%s' at %s:%d.", clientSocket.remoteAddress(), service, host, port));
                                        closed = true;
                                        clientSocket.close();
                                        serverSocket.close();
                                    }
                                }
                            };

                            serverSocket.endHandler(endHandler);
                            serverSocket.exceptionHandler(endHandler);
                            clientSocket.endHandler(endHandler);
                            clientSocket.exceptionHandler(endHandler);

                            Pump.createPump(logging(clientSocket, "From " + clientSocket.remoteAddress()), serverSocket).start();
                            Pump.createPump(logging(serverSocket, "To " + clientSocket.remoteAddress()), clientSocket).start();
                            clientSocket.resume();

                            LOG.info(String.format("Connected client '%s' to service '%s' at %s:%d.", clientSocket.remoteAddress(), service, host, port));
                        }
                    }
                });

                return;
            }
        }
        handleConnectFailure(clientSocket, String.format("Client '%s' could not be routed: No service implementation available for '%s'.", clientSocket.remoteAddress(), service));
    }

    private ReadStream<?> logging(final ReadStream<?> stream, final String prefix) {
        if (true) { // set to false to enable proxy data logging..
            return stream;
        }
        return new ReadStream<Object>() {

            @Override
            public Object endHandler(Handler<Void> handler) {
                stream.endHandler(handler);
                return this;
            }

            @Override
            public Object exceptionHandler(Handler handler) {
                stream.endHandler(handler);
                return this;
            }

            @Override
            public Object dataHandler(final Handler<Buffer> handler) {
                stream.dataHandler(new Handler<Buffer>() {
                    @Override
                    public void handle(Buffer event) {
                        LOG.info(prefix + ": [" + event.toString() + "]");
                        handler.handle(event);
                    }
                });
                return this;
            }

            @Override
            public Object pause() {
                stream.pause();
                return this;
            }

            @Override
            public Object resume() {
                stream.pause();
                return this;
            }

        };
    }

    private void handleConnectFailure(NetSocket socket, String reason) {
        if (reason != null) {
            LOG.info(reason);
        }
        failedConnectionAttempts.incrementAndGet();
        socket.close();
    }

}
