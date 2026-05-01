/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_activation.jakarta_activation_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.net.URL;

import org.junit.jupiter.api.Test;

public class SecuritySupportAnonymous4Test {

    private static final String RESOURCE_NAME =
            "jakarta_activation/jakarta_activation_api/security-support-anonymous-4.resource";

    @Test
    void getSystemResourcesFindsTestClasspathResource() throws Exception {
        URL[] resources = invokeGetSystemResources(RESOURCE_NAME);

        assertThat(resources).isNotNull().isNotEmpty();
        assertThat(resources)
                .extracting(URL::toExternalForm)
                .anySatisfy(url -> assertThat(url)
                        .contains("security-support-anonymous-4.resource"));
    }

    private static URL[] invokeGetSystemResources(String name) throws Exception {
        // SecuritySupport is package-private, and this fallback branch is not reachable through
        // public APIs when the library is loaded by an application class loader.
        Class<?> securitySupportClass = Class.forName("javax.activation.SecuritySupport");
        Method getSystemResourcesMethod = securitySupportClass.getDeclaredMethod(
                "getSystemResources",
                String.class);
        getSystemResourcesMethod.setAccessible(true);
        return (URL[]) getSystemResourcesMethod.invoke(null, name);
    }
}
