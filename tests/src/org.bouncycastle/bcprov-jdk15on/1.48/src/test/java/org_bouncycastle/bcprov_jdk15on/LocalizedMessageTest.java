/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15on;

import java.util.Locale;
import java.util.TimeZone;

import org.bouncycastle.i18n.LocalizedMessage;
import org.bouncycastle.i18n.MissingEntryException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class LocalizedMessageTest {
    private static final String UNAVAILABLE_BUNDLE = "org_bouncycastle.bcprov_jdk15on.UnavailableMessages";
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    void defaultClassLoaderReportsUnavailableResourceBundleEntry() {
        LocalizedMessage message = new LocalizedMessage(UNAVAILABLE_BUNDLE, "example");

        assertThatExceptionOfType(MissingEntryException.class)
                .isThrownBy(() -> message.getEntry("text", Locale.ROOT, UTC))
                .satisfies(exception -> {
                    assertThat(exception.getResource()).isEqualTo(UNAVAILABLE_BUNDLE);
                    assertThat(exception.getKey()).isEqualTo("example.text");
                    assertThat(exception.getLocale()).isEqualTo(Locale.ROOT);
                    assertThat(exception.getClassLoader()).isNull();
                });
    }

    @Test
    void configuredClassLoaderReportsUnavailableResourceBundleEntry() {
        LocalizedMessage message = new LocalizedMessage(UNAVAILABLE_BUNDLE, "example");
        ClassLoader classLoader = LocalizedMessageTest.class.getClassLoader();
        assertThat(classLoader).isNotNull();
        message.setClassLoader(classLoader);

        assertThatExceptionOfType(MissingEntryException.class)
                .isThrownBy(() -> message.getEntry("text", Locale.ROOT, UTC))
                .satisfies(exception -> {
                    assertThat(message.getClassLoader()).isSameAs(classLoader);
                    assertThat(exception.getResource()).isEqualTo(UNAVAILABLE_BUNDLE);
                    assertThat(exception.getKey()).isEqualTo("example.text");
                    assertThat(exception.getLocale()).isEqualTo(Locale.ROOT);
                    assertThat(exception.getClassLoader()).isSameAs(classLoader);
                });
    }
}
