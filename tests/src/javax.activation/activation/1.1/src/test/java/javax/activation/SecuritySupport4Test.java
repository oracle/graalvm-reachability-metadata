/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax.activation;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecuritySupport4Test {
    private static final String RESOURCE_NAME = "META-INF/security-support-4.mailcap";
    private static final String RESOURCE_MARKER =
            "x-java-content-handler=javax.activation.SecuritySupport4Marker";

    @Test
    void getSystemResourcesReturnsResourcesVisibleToTheSystemClassLoader() {
        URL[] resourceUrls = SecuritySupport.getSystemResources(RESOURCE_NAME);

        assertThat(resourceUrls).isNotNull().isNotEmpty();
        assertThat(Arrays.stream(resourceUrls)
                .map(SecuritySupport4Test::readResource)
                .anyMatch(resourceContent -> resourceContent.contains(RESOURCE_MARKER)))
                .isTrue();
    }

    private static String readResource(URL resourceUrl) {
        try (InputStream inputStream = resourceUrl.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Failed to read resource " + resourceUrl, exception);
        }
    }
}
