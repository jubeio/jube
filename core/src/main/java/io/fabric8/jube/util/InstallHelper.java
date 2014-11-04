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
package io.fabric8.jube.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import io.fabric8.utils.Closeables;
import io.fabric8.utils.Files;
import io.fabric8.utils.Objects;

/**
 * Utilities for install apps.
 */
public final class InstallHelper {
    public static final String PORTS_PROPERTIES_FILE = "ports.properties";
    public static final String ENVIRONMENT_VARIABLE_SCRIPT = "env.sh";

    private static final Logger LOG = Logger.getLogger(InstallHelper.class.getName());
    private static final Matcher DEFAULT_MATCHER = new DefaultMatcher();

    private InstallHelper() {
        // utulity class
    }

    /**
     * chmods the various scripts in the installation
     */
    public static void chmodAllScripts(File installDir) {
        if (installDir == null) {
            System.out.println("WARN: installDir is null!");
            return;
        }
        chmodScripts(installDir, DEFAULT_MATCHER);

        // all bin directories (also in sub directories) should have their bin scripts as executable
        chmodBinScriptsRecursive(installDir);

        File executables = new File(installDir, "executables.properties");
        if (executables.exists()) {
            try {
                Properties properties = new Properties();
                try (InputStream stream = new FileInputStream(executables)) {
                    properties.load(stream);
                }
                for (String dir : properties.stringPropertyNames()) {
                    String property = properties.getProperty(dir);
                    Matcher matcher = new RegexpMatcher(property);
                    LOG.info(String.format("CHMOD %s with pattern %s", dir, property));
                    chmodScripts(new File(installDir, dir), matcher);
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Lets make sure all shell scripts are executable
     */
    protected static void chmodScripts(File dir, Matcher matcher) {
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (matcher.match(file)) {
                        //noinspection ResultOfMethodCallIgnored
                        file.setExecutable(true);
                    }
                }
            }
        }
    }

    /**
     * Lets make sure all the shell scripts are executable in all <tt>bin</tt> directories
     * <p/>
     * For example the karaf image has a bin directory with karaf bin scripts we need to ensure are executable
     */
    public static void chmodBinScriptsRecursive(File dir) {
        // check children
        File[] dirs = dir.listFiles();
        if (dirs != null && dirs.length > 0) {
            for (File child : dirs) {
                if ("bin".equals(child.getName())) {
                    chmodScripts(child, DEFAULT_MATCHER);
                }
                // recursive
                chmodBinScriptsRecursive(child);
            }
        }
    }

    /**
     * Appends the environment variables to the env.sh script file
     */
    public static void writeEnvironmentVariables(File envScriptFile, Map<String, String> environmentVariables) throws IOException {
        PrintStream writer = new PrintStream(new FileOutputStream(envScriptFile, true));
        try {
            writer.println();

            Set<Map.Entry<String, String>> entries = environmentVariables.entrySet();
            for (Map.Entry<String, String> entry : entries) {
                String name = entry.getKey();
                String value = entry.getValue();

                writer.println("export " + name + "=\"" + value + "\"");
            }
            writer.println();
        } finally {
            Closeables.closeQuietly(writer);
        }
    }

    /**
     * Reads the {@link #PORTS_PROPERTIES_FILE} file in the given directory
     * for the map of port name to default value
     */
    public static Map<String, String> readPortsFromDirectory(File directory) throws IOException {
        File propertiesFile = new File(directory, PORTS_PROPERTIES_FILE);
        return readPorts(propertiesFile);
    }

    /**
     * Reads the properties file returning a map of port name to default port value
     */
    public static Map<String, String> readPorts(File propertiesFile) throws IOException {
        Map<String, String> answer = new Hashtable<>();
        Properties properties = new Properties();
        if (propertiesFile.exists() && propertiesFile.isFile()) {
            properties.load(new FileInputStream(propertiesFile));
            Set<Map.Entry<Object, Object>> entries = properties.entrySet();
            for (Map.Entry<Object, Object> entry : entries) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                if (key != null && value != null) {
                    answer.put(key.toString(), value.toString());
                }
            }
        }
        return answer;
    }


    /**
     * Writes the ports to the ports.properties file
     */
    public static void writePorts(File portFile, Map<String, String> portMap) throws FileNotFoundException {
        PrintStream writer = new PrintStream(new FileOutputStream(portFile));
        try {
            writer.println();

            Set<Map.Entry<String, String>> entries = portMap.entrySet();
            for (Map.Entry<String, String> entry : entries) {
                String name = entry.getKey();
                String value = entry.getValue();

                writer.println(name.toUpperCase() + " = " + value);
            }
            writer.println();
        } finally {
            Closeables.closeQuietly(writer);
        }
    }

    /**
     * Converts the given port name to a host environment variable name
     */
    public static String portNameToHostEnvVarName(String portName) {
        return portName.toUpperCase() + "_PORT";
    }

    private interface Matcher {
        boolean match(File file);
    }

    private static final class DefaultMatcher implements Matcher {

        public boolean match(File file) {
            String name = file.getName();
            String extension = Files.getFileExtension(name);
            if (extension != null) {
                return Objects.equal(name, "launcher") || Objects.equal(extension, "sh") || Objects.equal(extension, "bat") || Objects.equal(extension, "cmd");
            } else {
                // assume no extension is a script file (Apache Karaf does that unfortunately)
                return true;
            }
        }

    }

    private static final class RegexpMatcher implements Matcher {
        private final Pattern pattern;

        private RegexpMatcher(String regexp) {
            pattern = Pattern.compile(regexp);
        }

        public boolean match(File file) {
            return pattern.matcher(file.getName()).matches();
        }
    }

}
