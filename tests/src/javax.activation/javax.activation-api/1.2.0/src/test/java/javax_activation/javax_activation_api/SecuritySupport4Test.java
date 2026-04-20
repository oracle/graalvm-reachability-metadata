/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.javax_activation_api;

import java.lang.reflect.Method;
import java.net.URL;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecuritySupport4Test {
    private static final String RESOURCE_NAME = "javax_activation/javax_activation_api/security-support-4.resource";

    @Test
    void getSystemResourcesFindsSystemClasspathResources() throws Exception {
        Class<?> securitySupportClass = Class.forName("javax.activation.SecuritySupport");
        Method getSystemResourcesMethod = securitySupportClass.getDeclaredMethod("getSystemResources", String.class);
        getSystemResourcesMethod.setAccessible(true);

        URL[] resources = (URL[]) getSystemResourcesMethod.invoke(null, RESOURCE_NAME);

        assertThat(resources).isNotNull().isNotEmpty();
        assertThat(resources)
                .extracting(URL::toExternalForm)
                .anySatisfy(url -> assertThat(url).contains("security-support-4.resource"));
    }
}
