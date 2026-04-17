/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.javax_activation_api;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecuritySupport4Test {
    @Test
    void getSystemResourcesFindsClasspathResources() throws Exception {
        Method getSystemResources = Class.forName("javax.activation.SecuritySupport")
                .getDeclaredMethod("getSystemResources", String.class);
        getSystemResources.setAccessible(true);

        URL[] resources = (URL[]) getSystemResources.invoke(null, "META-INF/mimetypes.default");

        assertThat(resources).isNotNull();
        assertThat(Arrays.stream(resources)
                .map(URL::toExternalForm)
                .anyMatch(url -> url.contains("META-INF/mimetypes.default")))
                .isTrue();
    }
}
