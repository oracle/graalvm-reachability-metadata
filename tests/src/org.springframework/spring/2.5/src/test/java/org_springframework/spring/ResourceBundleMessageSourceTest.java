/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.junit.jupiter.api.Assertions.*;

public class ResourceBundleMessageSourceTest {
    @Test
    void resolvesLocalizedMessagesAndFormatsArguments() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("springtest.messages");
        assertEquals("Bonjour Ada", source.getMessage("welcome", new Object[]{"Ada"}, Locale.FRENCH));
        assertEquals("base", source.getMessage("marker", null, Locale.FRENCH));
        assertEquals("fallback", source.getMessage("absent", null, "fallback", Locale.FRENCH));
    }
}
