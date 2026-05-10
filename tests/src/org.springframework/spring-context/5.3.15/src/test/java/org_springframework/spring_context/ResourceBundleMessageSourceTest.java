/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;

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
    void resolvesMessageWithPlatformResourceBundleEncoding() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setDefaultEncoding(null);
        messageSource.setBasename("org_springframework.spring_context.resource_bundle_plain_messages");

        String message = messageSource.getMessage("greeting", null, Locale.ENGLISH);

        assertEquals("Hello through platform ResourceBundle encoding", message);
    }
}
