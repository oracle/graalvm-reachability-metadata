/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_runtime;

import org.apache.jasper.runtime.HttpJspBase;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.Permission;
import java.util.Collections;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpJspBaseTest {
    private static final String HTTP_JSP_BASE_CLASS_NAME = "org.apache.jasper.runtime.HttpJspBase";
    private static final String MISSING_PRELOAD_CLASS_NAME = "org.apache.jasper.servlet.JspServletWrapper";

    @Test
    @SuppressWarnings("removal")
    void preloadsProtectedRuntimeClassesThroughSecurityManager() throws Exception {
        final JspFactory previousFactory = JspFactory.getDefaultFactory();
        final SecurityManager previousSecurityManager = System.getSecurityManager();
        final boolean securityManagerInstalled = installPermissiveSecurityManager(previousSecurityManager);
        JspFactory.setDefaultFactory(null);

        try {
            final URL jasperRuntimeLocation = HttpJspBase.class.getProtectionDomain().getCodeSource().getLocation();
            try (FilteringJasperClassLoader classLoader = new FilteringJasperClassLoader(jasperRuntimeLocation)) {
                Class.forName(HTTP_JSP_BASE_CLASS_NAME, true, classLoader);
            }

            if (securityManagerInstalled || previousSecurityManager != null) {
                assertThat(JspFactory.getDefaultFactory()).isNotNull();
            }
        } catch (final Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            JspFactory.setDefaultFactory(previousFactory);
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    @Test
    @SuppressWarnings("removal")
    void initializesJspFactoryAndDelegatesGeneratedJspLifecycleMethods() throws Exception {
        final JspFactory previousFactory = JspFactory.getDefaultFactory();
        final SecurityManager previousSecurityManager = System.getSecurityManager();
        final boolean securityManagerInstalled = installPermissiveSecurityManager(previousSecurityManager);
        JspFactory.setDefaultFactory(null);

        try {
            final TestJspPage page = new TestJspPage();

            page.init(new TestServletConfig());
            page.service(null, null);
            page.destroy();

            assertThat(page.getServletInfo()).contains("Jasper");
            assertThat(page.events).isEqualTo("jspInit,_jspInit,_jspService,jspDestroy,_jspDestroy");
        } finally {
            JspFactory.setDefaultFactory(previousFactory);
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    @SuppressWarnings("removal")
    private static boolean installPermissiveSecurityManager(final SecurityManager previousSecurityManager) {
        if (previousSecurityManager != null) {
            return false;
        }
        try {
            System.setSecurityManager(new PermissiveSecurityManager());
            return true;
        } catch (final UnsupportedOperationException unsupportedOperationException) {
            return false;
        }
    }

    private static final class FilteringJasperClassLoader extends URLClassLoader {
        private FilteringJasperClassLoader(final URL jasperRuntimeLocation) {
            super(new URL[] {jasperRuntimeLocation}, HttpJspBaseTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            if (MISSING_PRELOAD_CLASS_NAME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            if (!name.startsWith("org.apache.jasper.")) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    try {
                        loadedClass = findClass(name);
                    } catch (final ClassNotFoundException classNotFoundException) {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }
    }

    private static final class TestJspPage extends HttpJspBase {
        private String events = "";

        @Override
        public void jspInit() {
            record("jspInit");
        }

        @Override
        public void _jspInit() {
            record("_jspInit");
        }

        @Override
        public void jspDestroy() {
            record("jspDestroy");
        }

        @Override
        protected void _jspDestroy() {
            record("_jspDestroy");
        }

        @Override
        public void _jspService(final HttpServletRequest request, final HttpServletResponse response)
                throws ServletException, IOException {
            record("_jspService");
        }

        private void record(final String event) {
            if (events.isEmpty()) {
                events = event;
            } else {
                events += "," + event;
            }
        }
    }

    private static final class TestServletConfig implements ServletConfig {
        @Override
        public String getServletName() {
            return "http-jsp-base-test";
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public String getInitParameter(final String name) {
            return null;
        }

        @Override
        public Enumeration getInitParameterNames() {
            return Collections.enumeration(Collections.emptyList());
        }
    }

    @SuppressWarnings("removal")
    private static final class PermissiveSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(final Permission permission) {
        }

        @Override
        public void checkPermission(final Permission permission, final Object context) {
        }
    }
}
