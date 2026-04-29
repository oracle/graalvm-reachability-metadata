/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_xml_bind.jakarta_xml_bind_api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.Permission;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.ServiceLoaderUtilInvoker;
import jakarta_xml_bind.jakarta_xml_bind_api.servicebound.ServiceBoundType;
import jakarta_xml_bind.jakarta_xml_bind_api.support.FactoryBackedContextFactory;
import jakarta_xml_bind.jakarta_xml_bind_api.support.StubJaxbContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceLoaderUtilTest {
    private static final String OSGI_CONTEXT_PATH =
            "jakarta_xml_bind.jakarta_xml_bind_api.contextpath.osgi";
    private static final String JAXB_CONTEXT_FACTORY_SERVICE_RESOURCE =
            "META-INF/services/jakarta.xml.bind.JAXBContextFactory";

    @Test
    public void loadsProviderClassUsingContextClassLoader() throws Exception {
        JAXBContext context = withContextClassLoader(
                getClass().getClassLoader(),
                () -> JAXBContext.newInstance(ServiceBoundType.class));

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("service-context-factory-classes");
    }

    @Test
    public void loadsProviderClassWithoutContextClassLoader() throws Exception {
        JAXBContext context = withContextClassLoader(null, () -> JAXBContext.newInstance(ServiceBoundType.class));

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("service-context-factory-classes");
    }

    @Test
    public void instantiatesProviderClassByName() throws Exception {
        Object provider = withContextClassLoader(
                null,
                ServiceLoaderUtilInvoker::instantiateFactoryBackedContextFactory);

        assertThat(provider).isInstanceOf(FactoryBackedContextFactory.class);
    }

    @Test
    @SuppressWarnings("removal")
    public void fallsBackToClassForNameWhenDefaultImplementationPackageAccessIsDenied() throws Exception {
        SecurityManager previousSecurityManager = System.getSecurityManager();
        PackageAccessDenyingSecurityManager securityManager =
                new PackageAccessDenyingSecurityManager(FactoryBackedContextFactory.class.getPackage().getName());
        boolean securityManagerInstalled = installSecurityManagerIfSupported(securityManager);

        try {
            Class<?> providerClass = ServiceLoaderUtilInvoker.loadFactoryBackedContextFactory(
                    getClass().getClassLoader());

            assertThat(providerClass).isEqualTo(FactoryBackedContextFactory.class);
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
    public void loadsContextPathProviderFromOsgiLocatorWhenServiceResourceIsAbsent() throws Exception {
        ClassLoader classLoader = new ServiceResourceHidingClassLoader(getClass().getClassLoader());

        JAXBContext context = withContextClassLoader(
                classLoader,
                () -> JAXBContext.newInstance(OSGI_CONTEXT_PATH, classLoader, Map.of()));

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("factory-backed-context-path-factory");
    }

    @Test
    public void loadsClassProviderFromOsgiLocatorWhenServiceResourceIsAbsent() throws Exception {
        ClassLoader classLoader = new ServiceResourceHidingClassLoader(getClass().getClassLoader());

        JAXBContext context = withContextClassLoader(
                classLoader,
                () -> JAXBContext.newInstance(ServiceBoundType.class));

        assertThat(context).isInstanceOf(StubJaxbContext.class);
        assertThat(((StubJaxbContext) context).getSource()).isEqualTo("factory-backed-classes-factory");
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

    private static <T> T withContextClassLoader(
            ClassLoader classLoader,
            ThrowingSupplier<T> supplier) throws Exception {
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
                throw new SecurityException(packageName);
            }
        }

        private boolean wasCheckPackageAccessCalled() {
            return checkPackageAccessCalled;
        }
    }

    private static final class ServiceResourceHidingClassLoader extends ClassLoader {
        private ServiceResourceHidingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            if (JAXB_CONTEXT_FACTORY_SERVICE_RESOURCE.equals(name)) {
                return null;
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (JAXB_CONTEXT_FACTORY_SERVICE_RESOURCE.equals(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getResources(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (JAXB_CONTEXT_FACTORY_SERVICE_RESOURCE.equals(name)) {
                return null;
            }
            return super.getResourceAsStream(name);
        }
    }
}
