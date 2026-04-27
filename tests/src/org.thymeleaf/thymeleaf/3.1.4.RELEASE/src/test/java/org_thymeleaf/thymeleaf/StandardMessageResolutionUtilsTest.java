/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.thymeleaf.messageresolver.StandardMessageResolver;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardMessageResolutionUtilsTest {

    @Test
    void resolveMessagesForOriginLoadsClasspathResourcesForOriginHierarchy() {
        ExposedStandardMessageResolver resolver = new ExposedStandardMessageResolver();

        Map<String, String> messages = resolver.resolveOriginMessages(StandardMessageResolutionUtilsChildOrigin.class, Locale.US);

        assertThat(messages)
                .containsEntry("shared", "child-us")
                .containsEntry("childDefaultOnly", "child-default")
                .containsEntry("childLocaleOnly", "child-us")
                .containsEntry("baseDefaultOnly", "base-default")
                .containsEntry("baseLocaleOnly", "base-us")
                .hasSize(5);
    }

    private static final class ExposedStandardMessageResolver extends StandardMessageResolver {

        private Map<String, String> resolveOriginMessages(final Class<?> origin, final Locale locale) {
            return super.resolveMessagesForOrigin(origin, locale);
        }
    }
}

class StandardMessageResolutionUtilsBaseOrigin {
}

final class StandardMessageResolutionUtilsChildOrigin extends StandardMessageResolutionUtilsBaseOrigin {
}
