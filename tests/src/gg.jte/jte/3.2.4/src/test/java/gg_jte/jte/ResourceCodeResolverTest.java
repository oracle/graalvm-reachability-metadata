/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package gg_jte.jte;

import static org.assertj.core.api.Assertions.assertThat;

import gg.jte.resolve.ResourceCodeResolver;
import org.junit.jupiter.api.Test;

public class ResourceCodeResolverTest {
    private static final String TEMPLATE_ROOT = "gg_jte/jte/templates";

    @Test
    void resolvesResourcesFromConfiguredClasspathRoot() {
        ResourceCodeResolver resolver =
                new ResourceCodeResolver(TEMPLATE_ROOT, ResourceCodeResolverTest.class.getClassLoader());

        assertThat(resolver.exists("welcome.jte")).isTrue();
        assertThat(resolver.resolve("welcome.jte")).isEqualTo("""
                @param String visitor
                <p>Hello, ${visitor}!</p>
                """);
        assertThat(resolver.getLastModified("missing.jte")).isZero();
    }
}
