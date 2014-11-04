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
package io.fabric8.jube.process.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.fabric8.utils.Objects;
import io.fabric8.utils.Strings;
import io.fabric8.utils.Zips;
import io.hawt.aether.OpenMavenURL;
import io.hawt.util.Closeables;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.apache.deltaspike.core.api.jmx.JmxManaged;
import org.apache.deltaspike.core.api.jmx.MBean;
import io.fabric8.jube.process.DownloadStrategy;
import io.fabric8.jube.process.InstallContext;
import io.fabric8.jube.process.InstallOptions;
import io.fabric8.jube.process.InstallTask;
import io.fabric8.jube.process.Installation;
import io.fabric8.jube.process.ProcessController;
import io.fabric8.jube.process.config.ConfigHelper;
import io.fabric8.jube.process.config.ProcessConfig;
import io.fabric8.jube.process.support.DefaultProcessController;
import io.fabric8.jube.process.support.command.Duration;
import io.fabric8.jube.util.InstallHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.io.ByteStreams.copy;
import static io.fabric8.jube.process.support.ProcessUtils.findInstallDir;

@Singleton
@MBean(objectName = "io.fabric8.jube:type=LocalProcesses", description = "Manages local processes on this node")
public class ProcessManagerService implements ProcessManagerServiceMBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessManagerService.class);
    private static final String INSTALLED_BINARY = "install.bin";

    private Executor executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("jube-process-manager-%s").build());
    private File storageLocation;
    private int lastId;
    private final Duration untarTimeout = Duration.valueOf("1h");
    private final Duration postUnpackTimeout = Duration.valueOf("1h");
    private final Duration postInstallTimeout = Duration.valueOf("1h");
    private SortedMap<String, Installation> installations = Maps.newTreeMap();

    private MBeanServer mbeanServer;
    private AtomicInteger fallbackPortGenerator = new AtomicInteger(30000);

    @Inject
    public ProcessManagerService(@ConfigProperty(name = "process_dir", defaultValue = "./processes") String storageLocation) throws MalformedObjectNameException, IOException {
        this(new File(storageLocation));
    }

    public ProcessManagerService(File storageLocation) throws MalformedObjectNameException, IOException {
        this.storageLocation = storageLocation;

        // lets find the largest number in the current directory as we are on startup
        lastId = 0;
        File[] files = storageLocation.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    String name = file.getName();
                    if (name.startsWith(".")) {
                        LOGGER.debug("Ignoring deleted installation at folder " + name);
                        continue;
                    }
                    if (name.matches("\\d+")) {
                        try {
                            int id = Integer.parseInt(name);
                            if (id > lastId) {
                                lastId = id;
                            }
                        } catch (NumberFormatException e) {
                            // should never happen :)
                        }
                    }
                    // TODO: we do not have the url this installation was created from
                    OpenMavenURL url = null;
                    ProcessConfig config = ConfigHelper.loadProcessConfig(file);
                    createInstallation(url, name, findInstallDir(file), config);
                }
            }
        }
    }


    @Override
    public String toString() {
        return "ProcessManager(" + storageLocation + ")";
    }

    @Override
    public ImmutableList<Installation> listInstallations() {
        return ImmutableList.copyOf(installations.values());
    }

    @Override
    public ImmutableMap<String, Installation> listInstallationMap() {
        return ImmutableMap.copyOf(installations);
    }

    @Override
    public Installation getInstallation(String id) {
        return installations.get(id);
    }

    @JmxManaged(description = "Returns the set of installed processes")
    public String getInstallationIds() {
        return listInstallationMap().keySet().toString();
    }

    @JmxManaged(description = "Returns the number of installed processes")
    public int getInstallationCount() {
        return listInstallationMap().keySet().size();
    }

    @Override
    public Installation install(final InstallOptions options, final InstallTask postInstall) throws Exception {
        @SuppressWarnings("serial")
        InstallTask installTask = new InstallTask() {
            @Override
            public void install(InstallContext installContext, ProcessConfig config, String id, File installDir) throws Exception {
                config.setName(options.getName());
                installDir.mkdirs();
                File archive = getDownloadStrategy(options).downloadContent(options.getUrl(), installDir);
                if (archive == null) {
                    archive = new File(installDir, INSTALLED_BINARY);
                }
                File nestedProcessDirectory = null;
                if (archive.exists()) {
                    Zips.unzip(new FileInputStream(archive), installDir);

                    InstallHelper.chmodAllScripts(installDir);
                    nestedProcessDirectory = findInstallDir(installDir);
                    allocatePorts(options, nestedProcessDirectory);
                    exportInstallDirEnvVar(options, nestedProcessDirectory, installContext, config);
                }
            }
        };
        return installViaScript(options, installTask);
    }

    protected DownloadStrategy getDownloadStrategy(InstallOptions options) {
        DownloadStrategy answer = options.getDownloadStrategy();
        if (answer == null) {
            answer = createDefaultDownloadStrategy();
        }
        return answer;
    }

    protected void allocatePorts(InstallOptions options, File nestedProcessDirectory) throws IOException {
        Map<String, String> ports = InstallHelper.readPortsFromDirectory(nestedProcessDirectory);
        Set<Map.Entry<String, String>> entries = ports.entrySet();
        if (!entries.isEmpty()) {
            // lets allocate ports and add them as env vars
            for (Map.Entry<String, String> entry : entries) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (Strings.isNotBlank(key) && Strings.isNotBlank(value)) {
                    String envVarName = InstallHelper.portNameToHostEnvVarName(key);
                    int port = allocatePortNumber(options, nestedProcessDirectory, key, value);
                    if (port <= 0) {
                        System.out.println("Could not allocate port " + envVarName + " has value: " + port);
                        LOGGER.warn("Could not allocate port " + envVarName + " has value: " + port);
                        continue;
                    }
                    options.getEnvironment().put(envVarName, "" + port);
                }
            }
            System.out.println("============ ports " + ports + " mapped to env vars: " + options.getEnvironment());
        }
    }

    /**
     * When using the {@link java.net.InetAddress#getHostName()} method in an
     * environment where neither a proper DNS lookup nor an <tt>/etc/hosts</tt>
     * entry exists for a given host, the following exception will be thrown:
     * <code>
     * java.net.UnknownHostException: &lt;hostname&gt;: &lt;hostname&gt;
     * at java.net.InetAddress.getLocalHost(InetAddress.java:1425)
     * ...
     * </code>
     * Instead of just throwing an UnknownHostException and giving up, this
     * method grabs a suitable hostname from the exception and prevents the
     * exception from being thrown. If a suitable hostname cannot be acquired
     * from the exception, only then is the <tt>UnknownHostException</tt> thrown.
     *
     * @return The hostname
     * @throws UnknownHostException
     * @see {@link java.net.InetAddress#getLocalHost()}
     * @see {@link java.net.InetAddress#getHostName()}
     */
    public static String getLocalHostName() throws UnknownHostException {
        try {
            return (InetAddress.getLocalHost()).getHostName();
        } catch (UnknownHostException uhe) {
            String host = uhe.getMessage(); // host = "hostname: hostname"
            if (host != null) {
                int colon = host.indexOf(':');
                if (colon > 0) {
                    return host.substring(0, colon);
                }
            }
            throw uhe;
        }
    }

    protected int allocatePortNumber(InstallOptions options, File nestedProcessDirectory, String key, String value) {
        ServerSocket ss = null;
        try {
            String hostName = getLocalHostName();
            int idGeneratorPort = 0;
            ss = new ServerSocket(idGeneratorPort);
            return ss.getLocalPort();
        } catch (Exception e) {
            LOGGER.warn("Failed to allocate port " + key + ". " + e, e);
            return fallbackPortGenerator.incrementAndGet();
        } finally {
            Closeables.closeQuitely(ss);
        }
    }

    protected void exportInstallDirEnvVar(InstallOptions options, File installDir, InstallContext installContext, ProcessConfig config) throws IOException {
        options.getEnvironment().put("APP_BASE", installDir.getAbsolutePath());
        substituteEnvironmentVariableExpressions(options.getEnvironment(), options.getEnvironment());
        config.getEnvironment().putAll(options.getEnvironment());
    }

    @Override
    public void uninstall(Installation installation) {
        installation.getController().uninstall();
        installations.remove(installation.getId());
    }

    @Override
    public ProcessConfig loadProcessConfig(File installDir, InstallOptions options) throws IOException {
        ProcessConfig config = loadControllerJson(installDir, options);
        Map<String, String> configEnv = config.getEnvironment();
        Map<String, String> optionsEnv = options.getEnvironment();
        if (optionsEnv != null) {
            configEnv.putAll(optionsEnv);
        }
        return config;
    }


    // Properties
    //-------------------------------------------------------------------------
    public File getStorageLocation() {
        return storageLocation;
    }

    public void setStorageLocation(File storageLocation) {
        this.storageLocation = storageLocation;
    }

    @Override
    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    // Implementation
    //-------------------------------------------------------------------------

    protected Installation installViaScript(InstallOptions options, InstallTask installTask) throws Exception {
        String id = createNextId(options);
        File installDir = createInstallDir(id);
        installDir.mkdirs();
        ProcessConfig config = loadProcessConfig(installDir, options);
        InstallContext installContext = new InstallContext(installDir, false);
        installTask.install(installContext, config, id, installDir);
        ConfigHelper.saveProcessConfig(config, installDir);

        Installation installation = createInstallation(options.getUrl(), id, installDir, config);
        installation.getController().install();
        return installation;
    }

    protected DownloadStrategy createDefaultDownloadStrategy() {
        return new DownloadStrategy() {
            @Override
            public File downloadContent(final OpenMavenURL sourceUrl, final File installDir) throws IOException {
                Objects.notNull(sourceUrl, "sourceUrl");
                // copy the URL to the install dir
                File archive = new File(installDir, INSTALLED_BINARY);
                InputStream from = sourceUrl.getInputStream();
                if (from == null) {
                    throw new FileNotFoundException("Could not open URL: " + sourceUrl);
                }
                copy(from, new FileOutputStream(archive));
                return archive;
            }
        };
    }

    protected ProcessConfig loadControllerJson(File installDir, InstallOptions options) throws IOException {
        return ConfigHelper.loadProcessConfig(installDir);
    }

    /**
     * Returns the next process ID
     */
    protected synchronized String createNextId(InstallOptions options) {
        String id = options.getId();
        if (Strings.isNotBlank(id)) {
            return id;
        }

        // lets double check it doesn't exist already
        File dir;
        String answer;
        do {
            lastId++;
            answer = "" + lastId;
            dir = createInstallDir(answer);
        }
        while (dir.exists());
        return answer;
    }

    protected File createInstallDir(String id) {
        return new File(storageLocation, id);
    }

    protected Installation createInstallation(OpenMavenURL url, String id, File rootDir, ProcessConfig config) {
        // TODO we should support different kinds of controller based on the kind of installation
        // we could maybe discover a descriptor file to describe how to control the process?
        // or generate this file on installation time?

        File installDir = findInstallDir(rootDir);
        ProcessController controller = createController(id, config, rootDir, installDir);
        // TODO need to read the URL from somewhere...
        Installation installation = new Installation(url, id, installDir, controller, config);
        installations.put(id, installation);
        return installation;
    }

    protected ProcessController createController(String id, ProcessConfig config, File rootDir, File installDir) {
        return new DefaultProcessController(id, config, rootDir, installDir);
    }

    // TODO. This is been ripped from io.fabric8.container.process.JolokiaAgentHelper.substituteEnvironmentVariableExpressions()
    // requires a refactoring to not introduce circular dependencies
    public static void substituteEnvironmentVariableExpressions(Map<String, String> map, Map<String, String> environmentVariables) {
        Set<Map.Entry<String, String>> envEntries = environmentVariables.entrySet();
        for (String key : map.keySet()) {
            String text = map.get(key);
            String oldText = text;
            if (Strings.isNotBlank(text)) {
                for (Map.Entry<String, String> envEntry : envEntries) {
                    String envKey = envEntry.getKey();
                    String envValue = envEntry.getValue();
                    if (Strings.isNotBlank(envKey) && Strings.isNotBlank(envValue)) {
                        text = text.replace("${env:" + envKey + "}", envValue);
                    }
                }
                if (!Objects.equal(oldText, text)) {
                    map.put(key, text);
                }
            }
        }
    }

}
