/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResourceBundleMessageSourceInnerMessageSourceControlTest {
    @Test
    void loadsPropertiesUsingPlatformEncodingAndRechecksExpiredCache() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("org_springframework.spring_context.control");
        source.setDefaultCharset(null);
        source.setFallbackToSystemLocale(false);
        source.setCacheMillis(0);
        assertEquals("Ready for Ada", source.getMessage("status", new Object[]{"Ada"}, Locale.ROOT));
        assertEquals("Ready for Lin", source.getMessage("status", new Object[]{"Lin"}, Locale.ROOT));
    }
}
