/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_inject;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.MessageSource;
import io.micronaut.context.i18n.ResourceBundleMessageSource;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ResourceBundleMessageSourceTest {
    private static final String BUNDLE_NAME = "io_micronaut.micronaut_inject.messages";

    @Test
    void resolvesDefaultAndLocaleSpecificMessagesFromBundles() {
        ResourceBundleMessageSource defaultSource = new ResourceBundleMessageSource(BUNDLE_NAME);
        ResourceBundleMessageSource frenchSource =
                new ResourceBundleMessageSource(BUNDLE_NAME, Locale.FRENCH);

        assertThat(
                        defaultSource.getRawMessage(
                                "greeting", MessageSource.MessageContext.of(Locale.ENGLISH)))
                .contains("Hello from the bundle");
        assertThat(
                        frenchSource.getRawMessage(
                                "greeting", MessageSource.MessageContext.of(Locale.FRENCH)))
                .contains("Bonjour depuis le bundle");
        assertThat(
                        frenchSource.getRawMessage(
                                "greeting", MessageSource.MessageContext.of(Locale.ENGLISH)))
                .contains("Hello from the bundle");
    }
}
