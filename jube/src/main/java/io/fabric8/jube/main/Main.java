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
package io.fabric8.jube.main;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import io.fabric8.jube.apimaster.ApiMasterService;
import io.fabric8.jube.util.JubeVersionUtils;
import io.fabric8.kubernetes.api.KubernetesClient;
import io.fabric8.kubernetes.api.KubernetesFactory;
import io.fabric8.kubernetes.api.KubernetesManager;
import io.fabric8.kubernetes.template.TemplateManager;
import io.fabric8.utils.Systems;
import io.hawt.aether.AetherFacade;
import io.hawt.git.GitFacade;
import io.hawt.kubernetes.KubernetesService;
import org.apache.cxf.cdi.CXFCdiServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.Slf4jLog;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jboss.weld.environment.servlet.BeanManagerResourceBindingListener;
import org.jboss.weld.environment.servlet.Listener;

/**
 * A main class to run jube.
 */
public final class Main {

    private static final String LOGO =
            "\t________         ______\n" +
            "\t______(_)____  _____  /_ _____\n" +
            "\t_____  / _  / / /__  __ \\_  _ \\\n" +
            "\t____  /  / /_/ / _  /_/ //  __/\n" +
            "\t___  /   \\__,_/  /_.___/ \\___/\n" +
            "\t/___/\n";

    private static boolean hawtioEnabled;

    private Main() {
        // run as main class
    }

    public static void main(final String[] args) throws Exception {
        try {
            // turn off annoying WELD noise
            System.setProperty("org.jboss.weld.xml.disableValidating", "true");
            System.setProperty("hawtio.authenticationEnabled", "false");
            System.setProperty("org.eclipse.jetty.util.log.class", Slf4jLog.class.getName());
            System.setProperty("java.util.logging.config.file", "jube-logging.properties");
            String port = Systems.getEnvVarOrSystemProperty("HTTP_PORT", "HTTP_PORT", "8585");
            Integer portNumber = Integer.parseInt(port);

            System.out.println("Starting REST server on port: " + port);
            final Server server = new Server(portNumber);

            HandlerCollection handlers = new HandlerCollection();
            server.setHandler(handlers);

            initaliseGitStuff();

            // lets find wars on the classpath
            Set<String> foundURLs = new HashSet<>();
            findWarsOnClassPath(server, handlers, Thread.currentThread().getContextClassLoader(), foundURLs, portNumber);

            ClassLoader classLoader = Main.class.getClassLoader();
            Thread.currentThread().setContextClassLoader(classLoader);

            findWarsOnClassPath(server, handlers, classLoader, foundURLs, portNumber);

            // In case you want to run in an IDE, and it does not setup the classpath right.. lets
            // find the .war files in the maven dir.  Assumes you set the working dir to the target/jube dir.
            if (foundURLs.isEmpty()) {
                File[] files = new File("maven").listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().endsWith(".war")) {
                            createWebapp(handlers, foundURLs, portNumber, file.getAbsolutePath());
                        }
                    }
                }
            }

            if (foundURLs.isEmpty()) {
                System.out.println("WARNING: did not find any war files on the classpath to embed!");
            }

            initialiseHawtioStuff();

            // Register and map the dispatcher servlet
            final ServletHolder servletHolder = new ServletHolder(new CXFCdiServlet());

            // change default service list URI
            servletHolder.setInitParameter("service-list-path", "/cxf/servicesList");

            final ServletContextHandler context = new ServletContextHandler();
            context.setClassLoader(classLoader);
            context.setContextPath("/");
            context.addEventListener(new Listener());
            context.addEventListener(new BeanManagerResourceBindingListener());
            context.addServlet(servletHolder, "/api/*");

            handlers.addHandler(context);

            server.start();

            System.out.println("\n\n");
            System.out.println(LOGO);

            String version = JubeVersionUtils.getReleaseVersion();
            if (version != null) {
                System.out.println("\t    Version " + version + "\n");
            }
            if (hawtioEnabled) {
                System.out.println("\t    Web console: http://" + ApiMasterService.getHostName() + ":" + port + "/hawtio/");
            }
            System.out.println("\t    REST api:    http://" + ApiMasterService.getHostName() + ":" + port + "/api/");
            System.out.println("\n\n");

            server.join();
        } catch (Throwable e) {
            logException(e);
        }
    }

    protected static void initialiseHawtioStuff() throws Exception {
        final String kubernetesAddress = "http://" + ApiMasterService.getHostName() + ":8585/";
        KubernetesClient kubernetesClient = new KubernetesClient(new KubernetesFactory(kubernetesAddress));

        KubernetesService kubernetesService = new KubernetesService() {
            @Override
            public String getKubernetesAddress() {
                return kubernetesAddress;
            }
        };
        kubernetesService.init();

        AetherFacade aetherFacade = new AetherFacade();
        aetherFacade.init();

        KubernetesManager kubernetesManager = new KubernetesManager();
        kubernetesManager.setKubernetes(kubernetesClient);
        kubernetesManager.init();

        TemplateManager templateManager = new TemplateManager();
        templateManager.init();
    }

    private static void initaliseGitStuff() throws Exception {
        GitFacade gitFacade = new GitFacade();
        gitFacade.setCloneRemoteRepoOnStartup(false);
        gitFacade.setPullOnStartup(false);
        gitFacade.setConfigDirName("hawtio-config");

        String importUrls = System.getenv("JUBE_IMPORT_URLS");
        if (importUrls != null) {
            gitFacade.setInitialImportURLs(importUrls);
        }

        gitFacade.init();
    }

    protected static void logException(Throwable e) {
        if (e instanceof InvocationTargetException) {
            InvocationTargetException invocationTargetException = (InvocationTargetException) e;
            e = invocationTargetException.getTargetException();
            System.out.println("=== was InvocationTargetException caught: " + e);
        }
        Set<Throwable> exceptions = new HashSet<>();
        exceptions.add(e);
        System.out.println("CAUGHT: " + e);
        e.printStackTrace();

        // show all causes
        Throwable t = e;
        while (true) {
            Throwable cause = t.getCause();
            if (cause != null && exceptions.add(cause)) {
                System.out.println();
                System.out.println("Caused by: " + cause);
                cause.printStackTrace();
                t = cause;
            } else {
                break;
            }
        }
    }

    protected static void findWarsOnClassPath(Server server, HandlerCollection handlers, ClassLoader classLoader, Set<String> foundURLs, Integer port) {
        try {
            Enumeration<URL> resources = classLoader.getResources("WEB-INF/web.xml");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String text = url.toString();
                if (text.startsWith("jar:")) {
                    text = text.substring(4);
                }
                createWebapp(handlers, foundURLs, port, text);
            }
        } catch (Exception e) {
            System.out.println("Failed to find web.xml on classpath: " + e);
            e.printStackTrace();
        }
    }

    private static void createWebapp(HandlerCollection handlers, Set<String> foundURLs, Integer port, String war) {
        if (foundURLs.add(war)) {
            String contextPath = createContextPath(war);
            String filePath = createFilePath(war);
            if (contextPath.equals("hawtio")) {
                System.out.println();
                System.out.println("==================================================");
                System.out.println("hawtio is running on http://" + ApiMasterService.getHostName() + ":" + port + "/" + contextPath + "/");
                System.out.println("==================================================");
                System.out.println();
                hawtioEnabled = true;
            } else {
                System.out.println("adding web context path: /" + contextPath + " war: " + filePath);
            }
            WebAppContext webapp = new WebAppContext();
            webapp.setContextPath("/" + contextPath);
            webapp.setWar("file://" + filePath);
            handlers.addHandler(webapp);
            webapp.setThrowUnavailableOnStartupException(true);
            try {
                System.out.println("Starting web app: " + contextPath);
                webapp.start();
                System.out.println("Started web app: " + contextPath + " without any exceptions!");
            } catch (Throwable e) {
                logException(e);
            }
        }
    }

    /**
     * Returns the web context path for the given URL
     */
    public static String createContextPath(String uri) {
        String contextPath = trimUpToLastIndexOf(uri, '!', '.');
        contextPath = trimFromAfterLastIndexOf(contextPath, '/', '\\');
        if (contextPath.startsWith("hawtio-") || contextPath.startsWith("console-")) {
            contextPath = "hawtio";
        }
        return contextPath;
    }

    /**
     * Returns the file path of the given URL
     */
    public static String createFilePath(String uri) {
        String answer = trimUpToLastIndexOf(uri, '!');
        int idx = answer.indexOf(':');
        if (idx > 0) {
            answer = answer.substring(idx + 1);
        }
        if (answer.startsWith("///")) {
            answer = answer.substring(2);
        }
        return answer;
    }

    public static String trimFromAfterLastIndexOf(String text, char... characters) {
        String answer = text;
        for (char ch : characters) {
            int idx = answer.lastIndexOf(ch);
            if (idx > 0) {
                answer = answer.substring(idx + 1, answer.length());
            }
        }
        return answer;
    }

    public static String trimUpToLastIndexOf(String text, char... characters) {
        String answer = text;
        for (char ch : characters) {
            int idx = answer.lastIndexOf(ch);
            if (idx > 0) {
                answer = answer.substring(0, idx);
            }
        }
        return answer;
    }

}
