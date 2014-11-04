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
package io.fabric8.jube.process;

import java.net.MalformedURLException;

import org.junit.Before;
import org.junit.Test;

import static io.fabric8.jube.process.InstallOptions.InstallOptionsBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class InstallOptionsTest {

    InstallOptions options;

    @Before
    public void setUp() throws MalformedURLException {
        System.setProperty("java.protocol.handler.pkgs", "org.ops4j.pax.url");
        options = new InstallOptionsBuilder().
                groupId("org.apache.camel").artifactId("camel-core").version("2.13.0").
                optionalDependencyPatterns(null).
                excludeDependencyFilterPatterns(null).
                build();
    }

    @Test
    public void shouldBuildParametersUrl() throws MalformedURLException {
        // TODO re-enable when we release hawtio 1.4.27
/*
        assertNotNull(options.getUrl());
        assertEquals(new OpenMavenURL("org.apache.camel/camel-core/2.13.0/jar"), options.getUrl());
*/
    }

    @Test
    public void shouldSetEmptyOptionalDependencyPatterns() {
        assertNotNull(options.getOptionalDependencyPatterns());
        assertEquals(0, options.getOptionalDependencyPatterns().length);
    }

    @Test
    public void shouldSetEmptyExcludeDependencyFilterPatterns() {
        assertNotNull(options.getExcludeDependencyFilterPatterns());
        assertEquals(0, options.getExcludeDependencyFilterPatterns().length);
    }

}
