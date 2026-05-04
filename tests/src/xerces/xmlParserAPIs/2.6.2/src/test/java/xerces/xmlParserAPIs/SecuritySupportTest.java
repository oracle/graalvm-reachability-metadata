/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xerces.xmlParserAPIs;

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

public class SecuritySupportTest {
    private static final String SECURITY_SUPPORT_CLASS = "javax.xml.parsers.SecuritySupport";
    private static final String RESOURCE_NAME = "xerces/xmlParserAPIs/security-support-resource.txt";
    private static final String MISSING_RESOURCE_NAME = "xerces/xmlParserAPIs/missing-security-support-resource.txt";

    @Test
    void getResourceAsStreamUsesProvidedClassLoader() throws Exception {
        try {
            Object securitySupport = newSecuritySupport();
            Method getResourceAsStream = getResourceAsStreamMethod(securitySupport);
            ResourceClassLoader classLoader = new ResourceClassLoader();

            try (InputStream resource = (InputStream) getResourceAsStream.invoke(
                    securitySupport,
                    classLoader,
                    RESOURCE_NAME)) {
                assertNotNull(resource);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void getResourceAsStreamFallsBackToSystemClassLoader() throws Exception {
        try {
            Object securitySupport = newSecuritySupport();
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

    private static Object newSecuritySupport() throws Exception {
        try {
            return newSecuritySupport(Class.forName(SECURITY_SUPPORT_CLASS));
        } catch (ClassNotFoundException classNotFoundException) {
            return newSecuritySupportFromLibraryJar(classNotFoundException);
        }
    }

    private static Object newSecuritySupport(Class<?> securitySupportClass) throws Exception {
        Constructor<?> constructor = securitySupportClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Object newSecuritySupportFromLibraryJar(ClassNotFoundException originalException) throws Exception {
        File libraryJar = findXmlParserApisJar(originalException);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {libraryJar.toURI().toURL()}, null)) {
            return newSecuritySupport(Class.forName(SECURITY_SUPPORT_CLASS, true, classLoader));
        }
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
        Enumeration<URL> resources = classLoader.getResources("javax/xml/parsers/SecuritySupport.class");
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

    private static final class ResourceClassLoader extends ClassLoader {
        private ResourceClassLoader() {
            super(SecuritySupportTest.class.getClassLoader());
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (RESOURCE_NAME.equals(name)) {
                return new ByteArrayInputStream("security support resource".getBytes(StandardCharsets.UTF_8));
            }
            return super.getResourceAsStream(name);
        }
    }
}
