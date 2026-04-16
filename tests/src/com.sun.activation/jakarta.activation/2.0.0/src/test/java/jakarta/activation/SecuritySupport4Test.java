/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta.activation;

import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class SecuritySupport4Test {

    @Test
    void getSystemResourcesFindsTestResourceOnSystemClassLoader() {
        String resourceName = "security-support/system-resource.txt";

        URL[] resources = SecuritySupport.getSystemResources(resourceName);

        assertThat(resources).isNotNull().isNotEmpty();
        assertThat(resources)
                .extracting(URL::toExternalForm)
                .anySatisfy(location -> assertThat(location).contains(resourceName));
    }
}
