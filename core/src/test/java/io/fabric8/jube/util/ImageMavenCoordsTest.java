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

import io.fabric8.utils.Strings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 */
public class ImageMavenCoordsTest {

    @Test
    public void testParseMavenCoords() throws Exception {
        ImageMavenCoords coords = ImageMavenCoords.parse("fabric8/java");
        System.out.println("Parsed: " + coords);

        assertEquals("getGroupId()", "io.fabric8.jube.images.fabric8", coords.getGroupId());
        assertEquals("getArtifactId()", "java", coords.getArtifactId());
        assertTrue("Should have a valid version", Strings.isNotBlank(coords.getVersion()));
        assertEquals("getType()", "zip", coords.getType());
        assertEquals("getClassifier()", "image", coords.getClassifier());
    }

}
