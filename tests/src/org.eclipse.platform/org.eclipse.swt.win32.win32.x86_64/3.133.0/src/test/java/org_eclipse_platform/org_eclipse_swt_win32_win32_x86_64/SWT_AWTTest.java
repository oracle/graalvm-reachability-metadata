/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_platform.org_eclipse_swt_win32_win32_x86_64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.awt.EventQueue;
import java.awt.Frame;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

public class SWT_AWTTest {
    @Test
    void rejectsNullCompositeWhenGettingEmbeddedFrame() {
        assertThatIllegalArgumentException().isThrownBy(() -> SWT_AWT.getFrame(null));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void createsEmbeddedAwtFrame() throws Exception {
        String originalEmbeddedFrameClass = SWT_AWT.embeddedFrameClass;
        SWT_AWT.embeddedFrameClass = "sun.awt.windows.WEmbeddedFrame";
        try {
            EventQueue.invokeAndWait(() -> {
                Display display = new Display();
                Shell shell = new Shell(display);
                try {
                    shell.setSize(200, 200);
                    shell.open();
                    Composite composite = new Composite(shell, SWT.EMBEDDED);

                    Frame frame = SWT_AWT.new_Frame(composite);

                    assertThat(frame).isNotNull();
                    assertThat(SWT_AWT.getFrame(composite)).isSameAs(frame);
                    frame.dispose();
                } finally {
                    shell.dispose();
                    display.dispose();
                }
            });
        } finally {
            SWT_AWT.embeddedFrameClass = originalEmbeddedFrameClass;
        }
    }
}
