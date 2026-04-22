/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml_bind.jaxb_api;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.Permission;
import java.util.Collections;
import java.util.Enumeration;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBContextFactory;
import javax.xml.bind.JAXBException;
import javax_xml_bind.jaxb_api.classproperties.PropertiesBoundType;
import javax_xml_bind.jaxb_api.noprovider.NoProviderBoundType;
import javax_xml_bind.jaxb_api.support.InterfaceContextFactory;
import javax_xml_bind.jaxb_api.support.StubJaxbContext;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ServiceLoaderUtilTest {
    private static final String DEPRECATED_SERVICE_RESOURCE = "META-INF/services/javax.xml.bind.JAXBContext";
    private static final String STANDARD_SERVICE_RESOURCE = "META-INF/services/" + JAXBContextFactory.class.getName();

    @Test
    public void loadsProviderClassesWithContextClassLoader() throws Exception {
        JAXBContext context = JAXBContext.newInstance(PropertiesBoundType.class);

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("classes-context-factory");
    }

    @Test
    public void loadsProviderClassesWithClassForNameWhenContextClassLoaderIsNull() throws Exception {
        JAXBContext context = withContextClassLoader(null, () -> JAXBContext.newInstance(NoProviderBoundType.class));

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("deprecated-service-context-factory-classes");
    }

    @Test
    public void instantiatesProviderClassesViaLegacyNewInstanceHelper() throws Exception {
        Object provider = invokeLegacyNewInstance(InterfaceContextFactory.class.getName());

        assertThat(provider).isInstanceOf(InterfaceContextFactory.class);
    }

    @Test
    @SuppressWarnings("removal")
    public void fallsBackToClassForNameWhenDefaultImplementationMatchesRequestedClass() throws Exception {
        SecurityManager previousSecurityManager = System.getSecurityManager();
        PackageAccessDenyingSecurityManager securityManager =
                new PackageAccessDenyingSecurityManager(InterfaceContextFactory.class.getPackage().getName());
        boolean securityManagerInstalled = installSecurityManagerIfSupported(securityManager);

        try {
            Class<?> providerClass = invokeSafeLoadClass(
                    InterfaceContextFactory.class.getName(),
                    InterfaceContextFactory.class.getName(),
                    getClass().getClassLoader());

            assertThat(providerClass).isEqualTo(InterfaceContextFactory.class);
            if (securityManagerInstalled) {
                assertThat(securityManager.wasCheckPackageAccessCalled()).isTrue();
            }
        } finally {
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    @Test
    public void attemptsOsgiLookupWhenNoOtherProviderIsAvailable() {
        ResourceHidingClassLoader classLoader = new ResourceHidingClassLoader(getClass().getClassLoader());

        assertThatThrownBy(() -> withContextClassLoader(classLoader, () -> JAXBContext.newInstance(NoProviderBoundType.class)))
                .isInstanceOf(JAXBException.class);
    }

    @SuppressWarnings("removal")
    private static boolean installSecurityManagerIfSupported(SecurityManager securityManager) {
        try {
            System.setSecurityManager(securityManager);
            return System.getSecurityManager() == securityManager;
        } catch (UnsupportedOperationException unsupportedOperationException) {
            return false;
        }
    }

    private static Object invokeLegacyNewInstance(String className) throws Exception {
        Class<?> serviceLoaderUtilClass = Class.forName("javax.xml.bind.ServiceLoaderUtil");
        Class<?> exceptionHandlerClass = Class.forName("javax.xml.bind.ServiceLoaderUtil$ExceptionHandler");
        Method newInstanceMethod = serviceLoaderUtilClass.getDeclaredMethod(
                "newInstance",
                String.class,
                String.class,
                exceptionHandlerClass);
        newInstanceMethod.setAccessible(true);
        return newInstanceMethod.invoke(null, className, className, null);
    }

    private static Class<?> invokeSafeLoadClass(String className, String defaultImplClassName, ClassLoader classLoader)
            throws Exception {
        Class<?> serviceLoaderUtilClass = Class.forName("javax.xml.bind.ServiceLoaderUtil");
        Method safeLoadClassMethod = serviceLoaderUtilClass.getDeclaredMethod(
                "safeLoadClass",
                String.class,
                String.class,
                ClassLoader.class);
        safeLoadClassMethod.setAccessible(true);
        return (Class<?>) safeLoadClassMethod.invoke(null, className, defaultImplClassName, classLoader);
    }

    private static <T> T withContextClassLoader(ClassLoader classLoader, ThrowingSupplier<T> supplier) throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(classLoader);
        try {
            return supplier.get();
        } finally {
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @SuppressWarnings("removal")
    private static final class PackageAccessDenyingSecurityManager extends SecurityManager {
        private final String deniedPackageName;
        private boolean checkPackageAccessCalled;

        private PackageAccessDenyingSecurityManager(String deniedPackageName) {
            this.deniedPackageName = deniedPackageName;
        }

        @Override
        public void checkPermission(Permission permission) {
        }

        @Override
        public void checkPackageAccess(String packageName) {
            if (deniedPackageName.equals(packageName)) {
                checkPackageAccessCalled = true;
                throw new SecurityException("package access denied for coverage test");
            }
        }

        private boolean wasCheckPackageAccessCalled() {
            return checkPackageAccessCalled;
        }
    }

    private static final class ResourceHidingClassLoader extends ClassLoader {
        private ResourceHidingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            if (shouldHide(name)) {
                return null;
            }
            return super.getResource(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (shouldHide(name)) {
                return null;
            }
            return super.getResourceAsStream(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (shouldHide(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getResources(name);
        }

        private boolean shouldHide(String name) {
            return DEPRECATED_SERVICE_RESOURCE.equals(name) || STANDARD_SERVICE_RESOURCE.equals(name);
        }
    }
}
