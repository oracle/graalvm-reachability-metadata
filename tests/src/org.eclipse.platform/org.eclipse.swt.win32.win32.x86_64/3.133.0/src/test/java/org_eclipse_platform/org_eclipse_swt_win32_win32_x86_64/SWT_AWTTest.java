/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_platform.org_eclipse_swt_win32_win32_x86_64;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Frame;

import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

public class SWT_AWTTest {
    @Test
    @EnabledOnOs(OS.WINDOWS)
    void newFrameUsesConfiguredEmbeddedFrameClassAndInitializesSwing() {
        SWT_AWTTestSupport.newFrameUsesConfiguredEmbeddedFrameClassAndInitializesSwing();
    }
}

final class SWT_AWTTestSupport {
    private SWT_AWTTestSupport() {
    }

    static void newFrameUsesConfiguredEmbeddedFrameClassAndInitializesSwing() {
        String previousEmbeddedFrameClass = SWT_AWT.embeddedFrameClass;
        Display display = new Display();
        Frame frame = null;
        try {
            SWT_AWT.embeddedFrameClass = "sun.awt.windows.WEmbeddedFrame";
            Shell shell = new Shell(display);
            Composite composite = new Composite(shell, SWT.EMBEDDED);
            shell.setSize(160, 120);
            shell.open();

            frame = SWT_AWT.new_Frame(composite);

            assertThat(frame).isNotNull();
            assertThat(SWT_AWT.getFrame(composite)).isSameAs(frame);
        } finally {
            if (frame != null) {
                frame.dispose();
            }
            SWT_AWT.embeddedFrameClass = previousEmbeddedFrameClass;
            display.dispose();
        }
    }
}
