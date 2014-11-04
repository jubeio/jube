/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.jube.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.KubernetesFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A base implementation of {@link EntityModel}
 */
public abstract class EntityModelSupport<T> implements EntityModel<T> {
    private final ConcurrentHashMap<String, T> map = new ConcurrentHashMap<>();
    private final Class<T> clazz;
    private final ObjectMapper objectMapper;

    protected EntityModelSupport(Class<T> clazz) {
        this(clazz, KubernetesFactory.createObjectMapper());
    }

    protected EntityModelSupport(Class<T> clazz, ObjectMapper objectMapper) {
        this.clazz = clazz;
        this.objectMapper = objectMapper;
    }

    @Override
    public T deleteEntity(String id) {
        return map.remove(id);
    }

    public T getEntity(String id) {
        return map.get(id);
    }

    public Map<String, T> getMap() {
        return new HashMap<>(map);
    }

    @Override
    public T updateEntity(String id, byte[] data) throws IOException {
        T entity = unmarshal(data);
        map.put(id, entity);
        return entity;
    }

    @Override
    public String marshal(T entity) throws IOException {
        return objectMapper.writerWithType(clazz).writeValueAsString(entity);
    }

    @Override
    public T unmarshal(byte[] data) throws IOException {
        return objectMapper.reader(clazz).readValue(data);
    }
}
