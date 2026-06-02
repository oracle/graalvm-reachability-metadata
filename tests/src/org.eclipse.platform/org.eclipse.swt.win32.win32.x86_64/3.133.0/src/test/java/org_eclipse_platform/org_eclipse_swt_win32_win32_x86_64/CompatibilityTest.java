/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_platform.org_eclipse_swt_win32_win32_x86_64;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.ResourceBundle;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.Compatibility;
import org.junit.jupiter.api.Test;

public class CompatibilityTest {
    @Test
    void getMessageLoadsResourceBundleForPlainAndFormattedMessages() throws ReflectiveOperationException {
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ROOT);

            resetMessagesBundle();
            assertThat(SWT.getMessage("SWT_OK")).isEqualTo("OK");

            resetMessagesBundle();
            assertThat(SWT.getMessage("SWT_Page_Mnemonic", new Object[] {"F"})).isEqualTo("Alt+F");
        } finally {
            resetMessagesBundle();
            Locale.setDefault(previousLocale);
        }
    }

    private static void resetMessagesBundle() throws ReflectiveOperationException {
        Field messages = Compatibility.class.getDeclaredField("msgs");
        messages.setAccessible(true);
        messages.set(null, null);
        ResourceBundle.clearCache(Compatibility.class.getClassLoader());
    }
}
