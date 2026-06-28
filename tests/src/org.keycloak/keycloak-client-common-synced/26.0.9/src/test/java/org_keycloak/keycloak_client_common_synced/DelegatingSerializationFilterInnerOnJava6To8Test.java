/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class DelegatingSerializationFilterInnerOnJava6To8Test {
    private static final String ADAPTER_CLASS_NAME =
            "org.keycloak.common.util.DelegatingSerializationFilter$OnJava6To8";

    @Test
    void invokesConfiguredJavaEightObjectInputFilterMethods() throws Exception {
        Method getObjectInputFilter = LegacyObjectInputFilterConfig.class.getDeclaredMethod(
                "getObjectInputFilter", ObjectInputStream.class);
        Method setObjectInputFilter = LegacyObjectInputFilterConfig.class.getDeclaredMethod(
                "setObjectInputFilter", ObjectInputStream.class, LegacyObjectInputFilter.class);
        Method createFilter = LegacyObjectInputFilterConfig.class.getDeclaredMethod("createFilter", String.class);
        Object adapter = newOnJava6To8Adapter(getObjectInputFilter, setObjectInputFilter, createFilter);

        try (ObjectInputStream objectInputStream = newObjectInputStream()) {
            assertThat(invokeGetObjectInputFilter(adapter, objectInputStream)).isNull();

            invokeSetObjectInputFilter(adapter, objectInputStream, "java.lang.String;!*");

            assertThat(invokeGetObjectInputFilter(adapter, objectInputStream))
                    .isInstanceOfSatisfying(LegacyObjectInputFilter.class, filter ->
                            assertThat(filter.pattern()).isEqualTo("java.lang.String;!*"));
        }
    }

    private static Object newOnJava6To8Adapter(Method getObjectInputFilter, Method setObjectInputFilter,
            Method createFilter) throws Exception {
        Class<?> adapterClass = Class.forName(ADAPTER_CLASS_NAME);
        Constructor<?> constructor = adapterClass.getDeclaredConstructor(Method.class, Method.class, Method.class);
        constructor.setAccessible(true);
        return constructor.newInstance(getObjectInputFilter, setObjectInputFilter, createFilter);
    }

    private static Object invokeGetObjectInputFilter(Object adapter, ObjectInputStream objectInputStream)
            throws Exception {
        Method method = adapter.getClass().getDeclaredMethod("getObjectInputFilter", ObjectInputStream.class);
        method.setAccessible(true);
        return method.invoke(adapter, objectInputStream);
    }

    private static void invokeSetObjectInputFilter(Object adapter, ObjectInputStream objectInputStream,
            String filterPattern) throws Exception {
        Method method = adapter.getClass().getDeclaredMethod("setObjectInputFilter", ObjectInputStream.class,
                String.class);
        method.setAccessible(true);
        method.invoke(adapter, objectInputStream, filterPattern);
    }

    private static ObjectInputStream newObjectInputStream() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(bytes)) {
            objectOutputStream.flush();
        }
        return new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    public static final class LegacyObjectInputFilterConfig {
        private static final Map<ObjectInputStream, LegacyObjectInputFilter> FILTERS = new IdentityHashMap<>();

        private LegacyObjectInputFilterConfig() {
        }

        public static LegacyObjectInputFilter getObjectInputFilter(ObjectInputStream objectInputStream) {
            return FILTERS.get(objectInputStream);
        }

        public static void setObjectInputFilter(ObjectInputStream objectInputStream,
                LegacyObjectInputFilter objectInputFilter) {
            FILTERS.put(objectInputStream, objectInputFilter);
        }

        public static LegacyObjectInputFilter createFilter(String pattern) {
            return new LegacyObjectInputFilter(pattern);
        }
    }

    public static final class LegacyObjectInputFilter {
        private final String pattern;

        private LegacyObjectInputFilter(String pattern) {
            this.pattern = pattern;
        }

        public String pattern() {
            return pattern;
        }
    }
}
