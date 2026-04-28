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
    void resolveMessagesForOriginLoadsLocaleSpecificMessagesForOriginClassAndSuperclass() {
        ExposedStandardMessageResolver messageResolver = new ExposedStandardMessageResolver();

        Map<String, String> messages = messageResolver.resolveOriginMessages(
                StandardMessageResolutionUtilsTestChildOrigin.class,
                Locale.US);

        assertThat(messages)
                .containsEntry("shared", "child-en")
                .containsEntry("childLevel", "child-en-us")
                .containsEntry("baseLevel", "base-en-us");
    }

    private static final class ExposedStandardMessageResolver extends StandardMessageResolver {

        private Map<String, String> resolveOriginMessages(Class<?> origin, Locale locale) {
            return resolveMessagesForOrigin(origin, locale);
        }
    }
}

class StandardMessageResolutionUtilsTestBaseOrigin {
}

class StandardMessageResolutionUtilsTestChildOrigin extends StandardMessageResolutionUtilsTestBaseOrigin {
}
