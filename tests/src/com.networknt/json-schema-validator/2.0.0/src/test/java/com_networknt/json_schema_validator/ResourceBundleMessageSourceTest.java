/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_networknt.json_schema_validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import com.networknt.schema.i18n.DefaultMessageSource;
import com.networknt.schema.i18n.ResourceBundleMessageSource;
import org.junit.jupiter.api.Test;

public class ResourceBundleMessageSourceTest {
    @Test
    void resolvesMessagesFromBundledResourceBundle() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource(
                DefaultMessageSource.BUNDLE_BASE_NAME);

        String message = messageSource.getMessage("type", Locale.ROOT, "$.enabled", "boolean", "string");

        assertThat(message).isEqualTo("$.enabled: boolean found, string expected");
    }

    @Test
    void usesDefaultMessageWhenResourceBundleDoesNotContainKey() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource(
                DefaultMessageSource.BUNDLE_BASE_NAME);

        String message = messageSource.getMessage("custom-key", "fallback {0}", Locale.ROOT, "message");

        assertThat(message).isEqualTo("fallback message");
    }
}
