/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_activation.jakarta_activation_api;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecuritySupportAnonymous3Test {
    private static final String SECURITY_SUPPORT_CLASS_NAME = "javax.activation.SecuritySupport";

    @Test
    void getResourcesLoadsUrlsThroughPrivilegedAction() throws Throwable {
        URL expectedUrl = new URL("file:/security-support-anonymous-3-test-resource");
        ClassLoader classLoader = new ResourceClassLoader(expectedUrl);
        MethodHandle getResources = securitySupportLookup().findStatic(securitySupportType(), "getResources",
            MethodType.methodType(URL[].class, ClassLoader.class, String.class));

        URL[] urls = (URL[]) getResources.invoke(classLoader, "META-INF/mime.types");

        assertThat(urls).containsExactly(expectedUrl);
    }

    private static MethodHandles.Lookup securitySupportLookup() throws ClassNotFoundException, IllegalAccessException {
        return MethodHandles.privateLookupIn(securitySupportType(), MethodHandles.lookup());
    }

    private static Class<?> securitySupportType() throws ClassNotFoundException {
        return Class.forName(SECURITY_SUPPORT_CLASS_NAME);
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private final URL resourceUrl;

        private ResourceClassLoader(URL resourceUrl) {
            super(null);
            this.resourceUrl = resourceUrl;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            assertThat(name).isEqualTo("META-INF/mime.types");
            return Collections.enumeration(Collections.singleton(resourceUrl));
        }
    }
}
