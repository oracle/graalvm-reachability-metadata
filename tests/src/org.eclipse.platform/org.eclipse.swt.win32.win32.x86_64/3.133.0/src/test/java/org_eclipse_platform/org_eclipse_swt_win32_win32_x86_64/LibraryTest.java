/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_platform.org_eclipse_swt_win32_win32_x86_64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.TimeUnit;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

public class LibraryTest {
    @Test
    void determinesWhetherSwtCanLoad() {
        assertThatCode(SWT::isLoadable).doesNotThrowAnyException();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void createsAndDisposesWindow() {
        Display display = new Display();
        Shell shell = new Shell(display);
        try {
            shell.setSize(200, 200);
            shell.open();

            assertThat(shell.isVisible()).isTrue();
        } finally {
            shell.dispose();
            display.dispose();
        }
    }
}
