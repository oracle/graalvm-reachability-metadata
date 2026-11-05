/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_core;

import com.sun.istack.localization.LocalizableMessage;
import com.sun.istack.localization.Localizer;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalizerTest {
    @Test
    void formatsMessagesFromALibraryBundle() {
        Localizer localizer = new Localizer(Locale.ENGLISH);
        LocalizableMessage message = new LocalizableMessage("com.sun.xml.bind.marshaller.Messages",
                "MarshallerImpl.UnsupportedEncoding", "example-encoding");

        assertThat(localizer.localize(message)).isEqualTo("unsupported encoding: example-encoding");
        assertThat(localizer.localize(message)).isEqualTo("unsupported encoding: example-encoding");
    }

    @Test
    void findsABundleRelocatedToTheClasspathRootAndLocalizesArguments() {
        Localizer localizer = new Localizer(Locale.ENGLISH);
        LocalizableMessage nested = new LocalizableMessage("com_sun_xml_bind.jaxb_core.LocalizationFallback",
                "item", "A17");
        LocalizableMessage message = new LocalizableMessage("com_sun_xml_bind.jaxb_core.LocalizationFallback",
                "greeting", nested);

        assertThat(localizer.localize(message)).isEqualTo("Selected item A17");
    }

    @Test
    void localizesMessagesFromTheContextClassLoader() {
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(new MessageResourceLoader(LocalizerTest.class.getClassLoader()));
            Localizer localizer = new Localizer(Locale.ENGLISH);
            LocalizableMessage message = new LocalizableMessage("application.messages.ContextMessages",
                    "greeting", "catalog entry A17");

            assertThat(localizer.localize(message)).isEqualTo("Selected catalog entry A17");
            assertThat(localizer.localize(message)).isEqualTo("Selected catalog entry A17");
        } finally {
            thread.setContextClassLoader(originalLoader);
        }
    }

    @Test
    void providesDiagnosticTextWhenNoBundleCanBeFound() {
        Localizer localizer = new Localizer(Locale.ENGLISH);
        LocalizableMessage message = new LocalizableMessage("com_sun_xml_bind.jaxb_core.UnavailableBundle",
                "notice", "A17", 3);

        assertThat(localizer.localize(message)).isEqualTo("[failed to localize] notice(A17, 3)");
    }

    private static final class MessageResourceLoader extends ClassLoader {
        private MessageResourceLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            // Expose an application message catalog without introducing any classes.
            if ("application/messages/ContextMessages.properties".equals(name)) {
                return super.getResource("LocalizationFallback.properties");
            }
            return super.getResource(name);
        }
    }
}
