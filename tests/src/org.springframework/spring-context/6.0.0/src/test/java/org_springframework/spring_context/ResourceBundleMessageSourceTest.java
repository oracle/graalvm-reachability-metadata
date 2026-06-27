/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.Reader;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

import org.springframework.context.support.ResourceBundleMessageSource;

public class ResourceBundleMessageSourceTest {

    @Test
    void resolvesMessageWithConfiguredResourceBundleControl() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("org_springframework.spring_context.resource_bundle_control_messages");

        String message = messageSource.getMessage("greeting", null, Locale.ENGLISH);

        assertEquals("Hello through ResourceBundle.Control", message);
    }

    @Test
    void fallsBackToPlainResourceBundleLookupWhenControlLookupFails() {
        ResourceBundleMessageSource messageSource = new ControlFailingMessageSource();
        messageSource.setBasename("org_springframework.spring_context.resource_bundle_plain_messages");

        String message = messageSource.getMessage("greeting", null, Locale.ENGLISH);

        assertEquals("Hello through plain ResourceBundle lookup", message);
    }

    static class ControlFailingMessageSource extends ResourceBundleMessageSource {

        @Override
        protected ResourceBundle loadBundle(Reader reader) throws IOException {
            return null;
        }

        @Override
        protected Locale getDefaultLocale() {
            ClassLoader classLoader = getBundleClassLoader();
            if (classLoader != null) {
                ResourceBundle.clearCache(classLoader);
            }
            throw new UnsupportedOperationException("Simulate ResourceBundle.Control being unsupported");
        }
    }
}
