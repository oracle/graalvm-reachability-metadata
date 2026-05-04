/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xerces.xmlParserAPIs;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaxXmlParsersSecuritySupportTest {
    private static final String RESOURCE_NAME = "xerces/xmlParserAPIs/security-support-resource.txt";
    private static final String MISSING_RESOURCE_NAME = "xerces/xmlParserAPIs/missing-security-support-resource.txt";
    private static final String RESOURCE_CONTENT = "xmlParserAPIs SecuritySupport resource";

    @Test
    void getResourceAsStreamUsesProvidedClassLoader() throws Exception {
        try {
            final Object securitySupport = newSecuritySupport();
            final ClassLoader classLoader = JavaxXmlParsersSecuritySupportTest.class.getClassLoader();

            try (InputStream stream = invokeGetResourceAsStream(securitySupport, classLoader, RESOURCE_NAME)) {
                assertThat(stream).isNotNull();
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(RESOURCE_CONTENT);
            }
        } catch (InvocationTargetException exception) {
            rethrowUnlessUnsupportedFeatureError(exception.getCause());
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    @Test
    void getResourceAsStreamUsesSystemClassLoaderWhenProvidedClassLoaderIsNull() throws Exception {
        try {
            final Object securitySupport = newSecuritySupport();

            try (InputStream stream = invokeGetResourceAsStream(securitySupport, null, MISSING_RESOURCE_NAME)) {
                assertThat(stream).isNull();
            }
        } catch (InvocationTargetException exception) {
            rethrowUnlessUnsupportedFeatureError(exception.getCause());
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    private static Object newSecuritySupport() throws Exception {
        // The active class is package-private and hidden by the JDK's java.xml module on modern JDKs.
        final Class<?> securitySupportClass = Class.forName("javax.xml.parsers.SecuritySupport", true,
                new XmlParserApisClassLoader());
        final Constructor<?> constructor = securitySupportClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static InputStream invokeGetResourceAsStream(final Object securitySupport, final ClassLoader classLoader,
            final String name) throws Exception {
        final Method method = securitySupport.getClass().getDeclaredMethod("getResourceAsStream", ClassLoader.class,
                String.class);
        method.setAccessible(true);
        return (InputStream) method.invoke(securitySupport, classLoader, name);
    }

    private static void rethrowUnlessUnsupportedFeatureError(final Throwable throwable) {
        if (throwable instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(throwable);
    }

    private static final class XmlParserApisClassLoader extends ClassLoader {
        private static final String SECURITY_SUPPORT_CLASS_NAME = "javax.xml.parsers.SecuritySupport";
        private static final String SECURITY_SUPPORT_12_CLASS_NAME = "javax.xml.parsers.SecuritySupport12";

        private XmlParserApisClassLoader() {
            super(JavaxXmlParsersSecuritySupportTest.class.getClassLoader());
        }

        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            if (!name.equals(SECURITY_SUPPORT_CLASS_NAME) && !name.startsWith(SECURITY_SUPPORT_12_CLASS_NAME)) {
                return super.findClass(name);
            }

            final String resourceName = name.replace('.', '/') + ".class";
            try (InputStream stream = getParent().getResourceAsStream(resourceName)) {
                if (stream == null) {
                    throw new ClassNotFoundException(name);
                }
                final byte[] bytes = stream.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }
    }
}
