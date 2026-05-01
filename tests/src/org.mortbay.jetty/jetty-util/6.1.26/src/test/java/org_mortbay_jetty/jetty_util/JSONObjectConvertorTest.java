/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.mortbay.util.ajax.JSON;
import org.mortbay.util.ajax.JSONObjectConvertor;

import static org.assertj.core.api.Assertions.assertThat;

public class JSONObjectConvertorTest {
    private static final String CONVERTOR_CLASS_NAME = "org.mortbay.util.ajax.JSONObjectConvertor";

    @Test
    void includesClassNameWhenConfiguredForJsonObjectRestoration() {
        JSON json = new JSON();
        json.addConvertor(WidgetSnapshot.class, new JSONObjectConvertor(true));
        WidgetSnapshot snapshot = new WidgetSnapshot("jetty", 7, true, "hidden");

        String serialized = json.toJSON(snapshot);

        assertThat(serialized).contains("\"class\":\"" + WidgetSnapshot.class.getName() + "\"");
        assertThat(serialized).contains("\"name\":\"jetty\"");
    }

    @Test
    void serializesWithConvertorLoadedByFreshRuntimeClassLoader() throws Exception {
        try {
            URL libraryUrl = JSONObjectConvertor.class.getProtectionDomain().getCodeSource().getLocation();
            try (URLClassLoader classLoader = new ChildFirstJSONObjectConvertorClassLoader(
                    new URL[] {libraryUrl}, JSONObjectConvertorTest.class.getClassLoader())) {
                Class<? extends JSON.Convertor> convertorType = classLoader.loadClass(CONVERTOR_CLASS_NAME)
                        .asSubclass(JSON.Convertor.class);
                JSON.Convertor convertor = convertorType.getConstructor(boolean.class, String[].class)
                        .newInstance(Boolean.FALSE, new String[] {"secret"});
                JSON json = new JSON();
                json.addConvertor(WidgetSnapshot.class, convertor);

                Object parsed = JSON.parse(json.toJSON(new WidgetSnapshot("jetty", 7, true, "hidden")));

                assertThat(parsed).isInstanceOf(Map.class);
                Map<?, ?> properties = (Map<?, ?>) parsed;
                assertThat(properties.get("name")).isEqualTo("jetty");
                assertThat(properties.get("count")).isEqualTo(Long.valueOf(7L));
                assertThat(properties.get("ready")).isEqualTo(Boolean.TRUE);
                assertThat(properties.containsKey("secret")).isFalse();
            }
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    @Test
    void serializesPublicBeanGettersAsJsonObjectFields() {
        JSON json = new JSON();
        json.addConvertor(WidgetSnapshot.class, new JSONObjectConvertor(false, new String[] {"secret"}));
        WidgetSnapshot snapshot = new WidgetSnapshot("jetty", 7, true, "hidden");

        Object parsed = JSON.parse(json.toJSON(snapshot));

        assertThat(parsed).isInstanceOf(Map.class);
        Map<?, ?> properties = (Map<?, ?>) parsed;
        assertThat(properties.get("name")).isEqualTo("jetty");
        assertThat(properties.get("count")).isEqualTo(Long.valueOf(7L));
        assertThat(properties.get("ready")).isEqualTo(Boolean.TRUE);
        assertThat(properties.containsKey("secret")).isFalse();
        assertThat(properties.containsKey("description")).isFalse();
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static class ChildFirstJSONObjectConvertorClassLoader extends URLClassLoader {
        ChildFirstJSONObjectConvertorClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!CONVERTOR_CLASS_NAME.equals(name)) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = findClass(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }
    }

    public static class WidgetSnapshot {
        private final String name;
        private final int count;
        private final boolean ready;
        private final String secret;

        public WidgetSnapshot(String name, int count, boolean ready, String secret) {
            this.name = name;
            this.count = count;
            this.ready = ready;
            this.secret = secret;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }

        public boolean isReady() {
            return ready;
        }

        public String getSecret() {
            return secret;
        }

        public String description() {
            return name + ':' + count;
        }
    }
}
