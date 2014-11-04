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
package io.fabric8.jube.local;

import io.fabric8.kubernetes.api.model.PodSchema;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 */
public class LocalKubernetesModelTest {
    protected LocalKubernetesModel model = new LocalKubernetesModel();

    @Test
    public void testUpdatePodIfNotExist() throws Exception {
        String a = "a";
        String b = "b";
        PodSchema podA = new PodSchema();
        podA.setId("a");

        // first time should create it for "a"
        assertTrue(model.updatePodIfNotExist(a, podA));
        assertEquals("should have created an 'a'", podA, model.getPod(a));
        assertTrue("should have created an 'a'", podA == model.getPod(a));

        PodSchema podB = new PodSchema();
        podB.setId("b");
        // if we try and use 'a' we should not change the map
        assertFalse(model.updatePodIfNotExist(a, podB));
        assertTrue(model.getPod("a") != podB);

        assertTrue(model.updatePodIfNotExist(b, podB));
        assertEquals("should have created an 'b'", podB, model.getPod(b));
        assertTrue("should have created an 'b'", podB == model.getPod(b));

        System.out.println("Created pods: " + model.getPodMap());
    }
}
