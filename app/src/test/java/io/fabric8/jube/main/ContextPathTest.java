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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 */
public class ContextPathTest {

    @Test
    public void testContextPath() throws Exception {
        assertContextPath("file:/projects/jube/app/target/jube/maven/hawtio-web-1.4.27.war!/WEB-INF/web.xml", "hawtio");
    }


    @Test
    public void testFilePath() throws Exception {
        assertFilePath("file:/projects/jube/app/target/jube/maven/hawtio-web-1.4.27.war!/WEB-INF/web.xml", "/projects/jube/app/target/jube/maven/hawtio-web-1.4.27.war");
        assertFilePath("file:/projects/jube/app/target/jube/maven/kubernetes-war-2.0.0-SNAPSHOT.war!/WEB-INF/web.xml", "/projects/jube/app/target/jube/maven/kubernetes-war-2.0.0-SNAPSHOT.war");
        assertFilePath("file:///projects/jube/app/target/jube/maven/kubernetes-war-2.0.0-SNAPSHOT.war!/WEB-INF/web.xml", "/projects/jube/app/target/jube/maven/kubernetes-war-2.0.0-SNAPSHOT.war");
    }

    protected void assertContextPath(String path, String expected) {
        String actual = Main.createContextPath(path);
        assertEquals("Wrong contextPath for " + path, expected, actual);
    }

    protected void assertFilePath(String path, String expected) {
        String actual = Main.createFilePath(path);
        assertEquals("Wrong filePath for " + path, expected, actual);
    }

}
