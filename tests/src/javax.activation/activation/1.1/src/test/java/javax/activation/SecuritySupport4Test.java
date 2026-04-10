/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax.activation;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import org.junit.jupiter.api.Test;

class SecuritySupport4Test {
    private static final String RESOURCE_NAME = "javax/activation/security-support-4-test.resource";

    @Test
    void getSystemResourcesFindsClasspathResources() {
        URL[] resourceUrls = SecuritySupport.getSystemResources(RESOURCE_NAME);

        assertThat(resourceUrls).isNotNull().isNotEmpty();
        assertThat(resourceUrls)
                .extracting(URL::toExternalForm)
                .anyMatch(url -> url.endsWith(RESOURCE_NAME));
    }
}
