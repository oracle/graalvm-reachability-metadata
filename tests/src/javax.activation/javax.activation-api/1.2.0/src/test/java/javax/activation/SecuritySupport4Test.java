/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax.activation;

import java.net.URL;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers dynamic access in {@code javax.activation.SecuritySupport$4}.
 */
public final class SecuritySupport4Test {

    @Test
    void getSystemResourcesEnumeratesResourcesFromSystemClassLoader() {
        URL[] urls = SecuritySupport.getSystemResources("META-INF/securitysupport4-test.resource");

        assertThat(urls).isNotNull();
        assertThat(urls).isNotEmpty();
        assertThat(urls)
                .extracting(URL::toExternalForm)
                .anySatisfy(url -> assertThat(url).contains("META-INF/securitysupport4-test.resource"));
    }
}
