/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.spi.ThreadContextMap;

/// Thread context map that ThreadContext instantiates from the `log4j2.threadContextMap` property.
public class RecordingThreadContextMap implements ThreadContextMap {

    static final List<String> OPERATIONS = new CopyOnWriteArrayList<>();

    private final ThreadLocal<Map<String, String>> values = ThreadLocal.withInitial(HashMap::new);

    public RecordingThreadContextMap() {
        OPERATIONS.add("created");
    }

    @Override
    public void put(String key, String value) {
        OPERATIONS.add("put:" + key);
        values.get().put(key, value);
    }

    @Override
    public String get(String key) {
        return values.get().get(key);
    }

    @Override
    public void remove(String key) {
        OPERATIONS.add("remove:" + key);
        values.get().remove(key);
    }

    @Override
    public void clear() {
        values.get().clear();
    }

    @Override
    public boolean containsKey(String key) {
        return values.get().containsKey(key);
    }

    @Override
    public Map<String, String> getCopy() {
        return new HashMap<>(values.get());
    }

    @Override
    public Map<String, String> getImmutableMapOrNull() {
        Map<String, String> current = values.get();
        return current.isEmpty() ? null : Collections.unmodifiableMap(new HashMap<>(current));
    }

    @Override
    public boolean isEmpty() {
        return values.get().isEmpty();
    }
}
