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
    void providesDiagnosticTextWhenNoBundleCanBeFound() {
        Localizer localizer = new Localizer(Locale.ENGLISH);
        LocalizableMessage message = new LocalizableMessage("com_sun_xml_bind.jaxb_core.UnavailableBundle",
                "notice", "A17", 3);

        assertThat(localizer.localize(message)).isEqualTo("[failed to localize] notice(A17, 3)");
    }
}
