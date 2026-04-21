/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.activation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecuritySupport4Test {
    @Test
    void getSystemResourcesReturnsActivationDefaultResources() throws Exception {
        final URL[] urls = invokeGetSystemResources("META-INF/mailcap.default");

        assertThat(urls).isNotNull().isNotEmpty();
    }

    private static URL[] invokeGetSystemResources(final String name) throws ClassNotFoundException,
            NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // SecuritySupport is package-private and this branch is not reachable through the public API
        // while the library is loaded by an application class loader.
        final Class<?> securitySupportClass = Class.forName("javax.activation.SecuritySupport");
        final Method getSystemResources = securitySupportClass.getDeclaredMethod("getSystemResources", String.class);
        getSystemResources.setAccessible(true);
        return (URL[]) getSystemResources.invoke(null, name);
    }
}
