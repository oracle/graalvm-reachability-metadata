/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.jcl_over_slf4j;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.PrivilegedAction;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.impl.SimpleLog;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class SimpleLogAnonymous1Test {
    private static final String SIMPLE_LOG_CLASS = "org.apache.commons.logging.impl.SimpleLog";
    private static final String SIMPLE_LOG_ANONYMOUS_CLASS = "org.apache.commons.logging.impl.SimpleLog$1";
    private static final byte[] NULL_CONTEXT_SIMPLE_LOG_BYTES = decode(
            "yv66vgAAADQADwoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClW" +
            "BwAIAQApb3JnL2FwYWNoZS9jb21tb25zL2xvZ2dpbmcvaW1wbC9TaW1wbGVMb2cBAARDb2RlAQAP" +
            "TGluZU51bWJlclRhYmxlAQAKYWNjZXNzJDAwMAEAGSgpTGphdmEvbGFuZy9DbGFzc0xvYWRlcjsB" +
            "AApTb3VyY2VGaWxlAQAOU2ltcGxlTG9nLmphdmEAMQAHAAIAAAAAAAIAAQAFAAYAAQAJAAAAHQAB" +
            "AAEAAAAFKrcAAbEAAAABAAoAAAAGAAEAAAACAAgACwAMAAEACQAAABoAAQAAAAAAAgGwAAAAAQAK" +
            "AAAABgABAAAABAABAA0AAAACAA4="
    );
    private static final byte[] SIMPLE_LOG_ANONYMOUS_BYTES = decode(
            "yv66vgAAADEALQkABgAdCgAHAB4KABsAHwoAIAAcCgAgACEHACIHACMHACQBAAh2YWwkbmFtZQEA" +
            "EkxqYXZhL2xhbmcvU3RyaW5nOwEABjxpbml0PgEAFShMamF2YS9sYW5nL1N0cmluZzspVgEABENv" +
            "ZGUBAA9MaW5lTnVtYmVyVGFibGUBABJMb2NhbFZhcmlhYmxlVGFibGUBAAR0aGlzAQAAAQAMSW5u" +
            "ZXJDbGFzc2VzAQAtTG9yZy9hcGFjaGUvY29tbW9ucy9sb2dnaW5nL2ltcGwvU2ltcGxlTG9nJDE7" +
            "AQADcnVuAQAUKClMamF2YS9sYW5nL09iamVjdDsBAAh0aHJlYWRDTAEAF0xqYXZhL2xhbmcvQ2xh" +
            "c3NMb2FkZXI7AQAKU291cmNlRmlsZQEADlNpbXBsZUxvZy5qYXZhAQAPRW5jbG9zaW5nTWV0aG9k" +
            "BwAlDAAmACcMAAkACgwACwAoDAApACoHACsMACwAJwEAK29yZy9hcGFjaGUvY29tbW9ucy9sb2dn" +
            "aW5nL2ltcGwvU2ltcGxlTG9nJDEBABBqYXZhL2xhbmcvT2JqZWN0AQAeamF2YS9zZWN1cml0eS9Q" +
            "cml2aWxlZ2VkQWN0aW9uAQApb3JnL2FwYWNoZS9jb21tb25zL2xvZ2dpbmcvaW1wbC9TaW1wbGVM" +
            "b2cBABNnZXRSZXNvdXJjZUFzU3RyZWFtAQApKExqYXZhL2xhbmcvU3RyaW5nOylMamF2YS9pby9J" +
            "bnB1dFN0cmVhbTsBAAMoKVYBAAphY2Nlc3MkMDAwAQAZKClMamF2YS9sYW5nL0NsYXNzTG9hZGVy" +
            "OwEAFWphdmEvbGFuZy9DbGFzc0xvYWRlcgEAGWdldFN5c3RlbVJlc291cmNlQXNTdHJlYW0AMAAG" +
            "AAcAAQAIAAEQEAAJAAoAAAACAAAACwAMAAEADQAAADQAAgACAAAACiortQABKrcAArEAAAACAA4A" +
            "AAAGAAEAAAKlAA8AAAAMAAEAAAAKABAAEwAAAAEAFAAVAAEADQAAAFkAAgACAAAAGbgAA0wrxgAM" +
            "Kyq0AAG2AASwKrQAAbgABbAAAAACAA4AAAASAAQAAAKnAAQCqQAIAqoAEQKsAA8AAAAWAAIAAAAZ" +
            "ABAAEwAAAAQAFQAWABcAAQADABgAAAACABkAGgAAAAQAGwAcABIAAAAKAAEABgAAAAAACA=="
    );
    private static final String TEST_RESOURCE = "simplelog.properties";
    private static final String TEST_RESOURCE_CONTENT = "org.apache.commons.logging.simplelog.defaultlog=debug";

    @Test
    void readsFromContextClassLoaderAndHandlesUnavailableContextClassLoader() throws Exception {
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        Method getResourceAsStream = SimpleLog.class.getDeclaredMethod("getResourceAsStream", String.class);
        getResourceAsStream.setAccessible(true);
        ClassLoader resourceClassLoader = new ClassLoader(null) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if (TEST_RESOURCE.equals(name)) {
                    return new ByteArrayInputStream(TEST_RESOURCE_CONTENT.getBytes(UTF_8));
                }
                return null;
            }
        };

        try {
            Thread.currentThread().setContextClassLoader(resourceClassLoader);
            try (InputStream resource = getResourceAsStream(getResourceAsStream, TEST_RESOURCE)) {
                assertThat(resource).isNotNull();
                assertThat(new String(resource.readAllBytes(), UTF_8)).isEqualTo(TEST_RESOURCE_CONTENT);
            }

            Thread.currentThread().setContextClassLoader(null);
            try (InputStream resource = getResourceAsStream(
                    getResourceAsStream,
                    "org_slf4j/jcl_over_slf4j/missing-simplelog.properties")) {
                assertThat(resource).isNull();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }

    @Test
    void readsFromSystemResourcesWhenTheOwningClassHasNoClassLoader() throws Exception {
        try {
            IsolatedSimpleLogClassLoader classLoader = new IsolatedSimpleLogClassLoader(
                    SimpleLogAnonymous1Test.class.getClassLoader()
            );
            Class<?> anonymousClass = classLoader.loadClass(SIMPLE_LOG_ANONYMOUS_CLASS);
            PrivilegedAction<?> action = newPrivilegedAction(
                    anonymousClass,
                    "org_slf4j/jcl_over_slf4j/missing-simplelog.properties"
            );

            assertThat(action.run()).isNull();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static InputStream getResourceAsStream(Method getResourceAsStream, String name) throws Exception {
        return (InputStream) getResourceAsStream.invoke(null, name);
    }

    private static byte[] decode(String content) {
        return Base64.getDecoder().decode(content);
    }

    private static PrivilegedAction<?> newPrivilegedAction(Class<?> actionClass, String resourceName) throws Exception {
        Constructor<?> constructor = actionClass.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        return (PrivilegedAction<?>) constructor.newInstance(resourceName);
    }

    private static final class IsolatedSimpleLogClassLoader extends ClassLoader {
        private final Map<String, Class<?>> definedClasses = new ConcurrentHashMap<>();

        private IsolatedSimpleLogClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (SIMPLE_LOG_CLASS.equals(name) || SIMPLE_LOG_ANONYMOUS_CLASS.equals(name)) {
                Class<?> loadedClass = definedClasses.computeIfAbsent(name, this::defineSimpleLogClass);
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
            return super.loadClass(name, resolve);
        }

        private Class<?> defineSimpleLogClass(String name) {
            byte[] bytes = SIMPLE_LOG_CLASS.equals(name) ? NULL_CONTEXT_SIMPLE_LOG_BYTES : SIMPLE_LOG_ANONYMOUS_BYTES;
            return defineClass(name, bytes, 0, bytes.length, SimpleLog.class.getProtectionDomain());
        }
    }
}
