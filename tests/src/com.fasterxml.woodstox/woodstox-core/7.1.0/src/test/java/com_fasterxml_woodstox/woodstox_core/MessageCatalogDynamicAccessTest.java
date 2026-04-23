/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.scanner.dtd.MessageCatalog;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class MessageCatalogDynamicAccessTest {
    @Test
    void loadsMessagesAndDiscoversSupportedLocales() {
        TestMessageCatalog catalog = new TestMessageCatalog();
        Locale previousDefault = Locale.getDefault();
        Locale.setDefault(Locale.ITALIAN);
        try {
            assertThat(catalog.getMessage(Locale.ENGLISH, "greeting")).isEqualTo("Hello from Woodstox");
            assertThat(catalog.getMessage(Locale.GERMAN, "greeting")).isEqualTo("Hello from Woodstox");
            assertThat(catalog.getMessage(Locale.GERMAN, "welcome", new Object[]{"native image"}))
                    .isEqualTo("Hello, native image!");
            assertThat(catalog.isLocaleSupported("en_US")).isTrue();
            assertThat(catalog.isLocaleSupported("zz_ZZ")).isFalse();
            assertThat(catalog.chooseLocale(new String[]{"en_US", "zz_ZZ"})).isEqualTo(Locale.US);
        } finally {
            Locale.setDefault(previousDefault);
        }
    }

    public static final class TestMessageCatalog extends MessageCatalog {
        public TestMessageCatalog() {
            super(TestMessageCatalog.class);
        }
    }
}
