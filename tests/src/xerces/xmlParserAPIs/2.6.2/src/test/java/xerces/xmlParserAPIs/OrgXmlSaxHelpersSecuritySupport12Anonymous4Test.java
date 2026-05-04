/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xerces.xmlParserAPIs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class OrgXmlSaxHelpersSecuritySupport12Anonymous4Test {
    private static final String SECURITY_SUPPORT12_CLASS = "org.xml.sax.helpers.SecuritySupport12";
    private static final String SECURITY_SUPPORT12_RESOURCE = "org/xml/sax/helpers/SecuritySupport12.class";
    private static final String RESOURCE_NAME = "xerces/xmlParserAPIs/org-xml-sax-security-support12-resource.txt";
    private static final String MISSING_RESOURCE_NAME = "xerces/xmlParserAPIs/"
            + "missing-org-xml-sax-security-support12-resource.txt";
    private static final String RESOURCE_CONTENT = "org.xml.sax.helpers security support 12 resource";

    @Test
    void getResourceAsStreamUsesProvidedClassLoader() throws Exception {
        try (SecuritySupportInstance instance = newSecuritySupport12()) {
            Object securitySupport = instance.securitySupport();
            Method getResourceAsStream = getResourceAsStreamMethod(securitySupport);
            ResourceClassLoader classLoader = new ResourceClassLoader();

            try (InputStream resource = (InputStream) getResourceAsStream.invoke(
                    securitySupport,
                    classLoader,
                    RESOURCE_NAME)) {
                assertNotNull(resource);
                assertEquals(RESOURCE_CONTENT, new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void getResourceAsStreamUsesSystemClassLoaderWhenClassLoaderIsNull() throws Exception {
        try (SecuritySupportInstance instance = newSecuritySupport12()) {
            Object securitySupport = instance.securitySupport();
            Method getResourceAsStream = getResourceAsStreamMethod(securitySupport);

            try (InputStream resource = (InputStream) getResourceAsStream.invoke(
                    securitySupport,
                    null,
                    MISSING_RESOURCE_NAME)) {
                assertNull(resource);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static SecuritySupportInstance newSecuritySupport12() throws Exception {
        try {
            return new SecuritySupportInstance(newSecuritySupport12(Class.forName(SECURITY_SUPPORT12_CLASS)), null);
        } catch (ClassNotFoundException classNotFoundException) {
            return newSecuritySupport12FromLibraryJar(classNotFoundException);
        }
    }

    private static Object newSecuritySupport12(Class<?> securitySupportClass) throws Exception {
        Constructor<?> constructor = securitySupportClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static SecuritySupportInstance newSecuritySupport12FromLibraryJar(
            ClassNotFoundException originalException) throws Exception {
        File libraryJar = findXmlParserApisJar(originalException);
        URLClassLoader classLoader = new URLClassLoader(new URL[] {libraryJar.toURI().toURL()}, null);
        Class<?> securitySupportClass = Class.forName(SECURITY_SUPPORT12_CLASS, true, classLoader);
        return new SecuritySupportInstance(newSecuritySupport12(securitySupportClass), classLoader);
    }

    private static File findXmlParserApisJar(ClassNotFoundException originalException) throws Exception {
        String classPath = System.getProperty("java.class.path", "");
        for (String entry : classPath.split(File.pathSeparator)) {
            File file = new File(entry);
            if (isXmlParserApisJar(file)) {
                return file;
            }
        }

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(SECURITY_SUPPORT12_RESOURCE);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            if ("jar".equals(resource.getProtocol())) {
                JarURLConnection connection = (JarURLConnection) resource.openConnection();
                File file = new File(connection.getJarFileURL().toURI());
                if (isXmlParserApisJar(file)) {
                    return file;
                }
            }
        }
        throw originalException;
    }

    private static boolean isXmlParserApisJar(File file) {
        return file.getName().startsWith("xmlParserAPIs-") && file.getName().endsWith(".jar");
    }

    private static Method getResourceAsStreamMethod(Object securitySupport) throws Exception {
        Method method = securitySupport.getClass().getDeclaredMethod(
                "getResourceAsStream",
                ClassLoader.class,
                String.class);
        method.setAccessible(true);
        return method;
    }

    private static final class SecuritySupportInstance implements AutoCloseable {
        private final Object securitySupport;
        private final URLClassLoader classLoader;

        private SecuritySupportInstance(Object securitySupport, URLClassLoader classLoader) {
            this.securitySupport = securitySupport;
            this.classLoader = classLoader;
        }

        private Object securitySupport() {
            return securitySupport;
        }

        @Override
        public void close() throws Exception {
            if (classLoader != null) {
                classLoader.close();
            }
        }
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private ResourceClassLoader() {
            super(OrgXmlSaxHelpersSecuritySupport12Anonymous4Test.class.getClassLoader());
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (RESOURCE_NAME.equals(name)) {
                return new ByteArrayInputStream(RESOURCE_CONTENT.getBytes(StandardCharsets.UTF_8));
            }
            return super.getResourceAsStream(name);
        }
    }
}
