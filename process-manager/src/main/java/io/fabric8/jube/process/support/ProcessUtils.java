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
package io.fabric8.jube.process.support;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessUtils {

    private static final transient Logger LOGGER = LoggerFactory.getLogger(ProcessUtils.class);

    private ProcessUtils() {
        // utility class
    }

    /**
     * Lets find the install dir, which may be the root dir or could be a child directory (as typically untarring will create a new child directory)
     */
    public static File findInstallDir(File rootDir) {
        if (installExists(rootDir)) {
            return rootDir;
        }
        File[] files = rootDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (installExists(file)) {
                    return file;
                }
            }
        }
        return rootDir;
    }

    public static boolean installExists(File file) {
        // skip hidden files
        if (file.getName().startsWith(".")) {
            return false;
        }
        if (file.isDirectory()) {
            File binDir = new File(file, "bin");
            return binDir.exists() && binDir.isDirectory();
        }
        return false;
    }

}
