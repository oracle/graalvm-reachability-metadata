/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_activation.jakarta_activation_api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class SecuritySupportSystemResourcesTest {
    private static final String RESOURCE_NAME =
            "jakarta_activation/jakarta_activation_api/security-support-4.resource";

    @Test
    void getSystemResourcesFindsSystemClasspathResources() throws Exception {
        // SecuritySupport is package-private and this branch is not reachable through the public API
        // while the library is loaded by an application class loader.
        Class<?> securitySupportClass = Class.forName("jakarta.activation.SecuritySupport");
        Method getSystemResourcesMethod = securitySupportClass.getDeclaredMethod("getSystemResources", String.class);
        getSystemResourcesMethod.setAccessible(true);

        URL[] resources = (URL[]) getSystemResourcesMethod.invoke(null, RESOURCE_NAME);

        assertThat(resources).isNotNull().isNotEmpty();
        assertThat(resources)
                .extracting(URL::toExternalForm)
                .anySatisfy(url -> assertThat(url).contains("security-support-4.resource"));
    }
}
