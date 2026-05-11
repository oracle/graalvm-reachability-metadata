/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_formdev.flatlaf_extras;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.extras.FlatInspector;
import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
@ResourceLock("com.formdev.flatlaf.util.SystemInfo")
public class FlatInspectorTest {
    private static final String UI_ROW = "UI:</td><td>";
    private static final String TEST_UI_CLASS_NAME = TestComponentUi.class.getSimpleName();

    @Test
    void buildsTooltipUsingPublicJComponentUiMethodOnModernJava() throws Exception {
        String text = buildToolTipTextWithJavaVersion(true);

        assertThat(text).contains(UI_ROW).contains(TEST_UI_CLASS_NAME);
    }

    @Test
    void buildsTooltipUsingJComponentUiFieldOnLegacyJava() throws Exception {
        String text = buildToolTipTextWithJavaVersion(false);

        assertThat(text).contains(UI_ROW).contains(TEST_UI_CLASS_NAME);
    }

    private static String buildToolTipTextWithJavaVersion(boolean java9OrLater) throws Exception {
        String originalJavaHome = System.getProperty("java.home");
        try (SystemInfoOverride ignored = SystemInfoOverride.java9OrLater(java9OrLater)) {
            ensureJavaHomeSet();
            assertThat(SystemInfo.isJava_9_orLater).isEqualTo(java9OrLater);
            Method buildToolTipText = FlatInspector.class.getDeclaredMethod(
                    "buildToolTipText", Component.class, int.class, boolean.class);
            buildToolTipText.setAccessible(true);

            TestComponent component = new TestComponent();
            component.setSize(component.getPreferredSize());
            return (String) buildToolTipText.invoke(null, component, 0, false);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception nestedException) {
                throw nestedException;
            }
            if (cause instanceof Error nestedError) {
                throw nestedError;
            }
            throw exception;
        } finally {
            restoreProperty("java.home", originalJavaHome);
        }
    }

    private static void ensureJavaHomeSet() {
        if (System.getProperty("java.home") != null) {
            return;
        }

        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) {
            System.setProperty("java.home", javaHome);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value != null) {
            System.setProperty(key, value);
        } else {
            System.clearProperty(key);
        }
    }

    private static final class TestComponent extends JComponent {
        private TestComponent() {
            setUI(new TestComponentUi());
            setBackground(Color.WHITE);
            setForeground(Color.BLACK);
            setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
            setMinimumSize(new Dimension(16, 8));
            setPreferredSize(new Dimension(64, 24));
            setMaximumSize(new Dimension(128, 48));
        }
    }

    private static final class TestComponentUi extends ComponentUI {
    }
}
