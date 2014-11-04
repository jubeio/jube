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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Allows listeners to be added and removed
 */
public class EntityListenerList<T> implements EntityListener<T> {
    private List<EntityListener<T>> listeners = new CopyOnWriteArrayList<>();

    public void addListener(EntityListener<T> listener) {
        listeners.add(listener);
    }

    public void removeListener(EntityListener<T> listener) {
        listeners.remove(listener);
    }

    @Override
    public void entityChanged(String id, T entity) {
        for (EntityListener<T> listener : listeners) {
            listener.entityChanged(id, entity);
        }
    }

    @Override
    public void entityDeleted(String id) {
        for (EntityListener<T> listener : listeners) {
            listener.entityDeleted(id);
        }
    }
}
