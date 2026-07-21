/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_platform.org_eclipse_swt_win32_win32_x86_64;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.swt.SWT;
import org.junit.jupiter.api.Test;

public class CompatibilityTest {
    @Test
    void resolvesLocalizedMessagesWithAndWithoutArguments() {
        assertThat(SWT.getMessage("SWT_OK"))
                .isNotBlank()
                .isNotEqualTo("SWT_OK");
        assertThat(SWT.getMessage("SWT_Page_Mnemonic", new Object[] {"P"}))
                .contains("P")
                .isNotEqualTo("SWT_Page_Mnemonic");
    }
}
