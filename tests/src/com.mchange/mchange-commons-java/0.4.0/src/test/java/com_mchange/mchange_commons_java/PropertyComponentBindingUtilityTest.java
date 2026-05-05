/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.beans.swing.PropertyBoundComboBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class PropertyComponentBindingUtilityTest {
    @BeforeAll
    static void skipWhenNativeImageCannotSupportSwingFixture() {
        // The Swing fixture is not stable under either the metadata agent or the native runtime.
        assumeFalse(isNativeImageAgentActive(), "native-image-agent crashes on the AWT event queue for this test");
        assumeFalse(isNativeImageRuntime(), "AWT event queue initialization is not supported in this native-image test");
    }

    @Test
    void comboBoxBindingReadsInitialValueAndWritesUserSelectionToBean() throws Exception {
        BoundChoiceBean bean = new BoundChoiceBean();
        bean.setChoice("alpha");

        PropertyBoundComboBox comboBox = createComboBox(bean);
        flushEventDispatchThread();

        assertThat(selectedItem(comboBox)).isEqualTo("alpha");

        onEventDispatchThread(() -> comboBox.setSelectedItem("bravo"));

        assertThat(bean.getChoice()).isEqualTo("bravo");
    }

    @Test
    void propertyChangeEventSynchronizesBoundComboBoxFromBean() throws Exception {
        BoundChoiceBean bean = new BoundChoiceBean();
        PropertyBoundComboBox comboBox = createComboBox(bean);
        flushEventDispatchThread();

        onEventDispatchThread(() -> bean.setChoice("charlie"));

        assertThat(selectedItem(comboBox)).isEqualTo("charlie");
    }

    private static PropertyBoundComboBox createComboBox(BoundChoiceBean bean) throws Exception {
        AtomicReference<PropertyBoundComboBox> comboBox = new AtomicReference<>();
        onEventDispatchThread(() -> comboBox.set(new PropertyBoundComboBox(
                bean,
                "choice",
                new String[] {"alpha", "bravo", "charlie"},
                null)));
        return comboBox.get();
    }

    private static Object selectedItem(PropertyBoundComboBox comboBox) throws Exception {
        AtomicReference<Object> selectedItem = new AtomicReference<>();
        onEventDispatchThread(() -> selectedItem.set(comboBox.getSelectedItem()));
        return selectedItem.get();
    }

    private static void flushEventDispatchThread() throws Exception {
        onEventDispatchThread(() -> assertThat(SwingUtilities.isEventDispatchThread()).isTrue());
    }

    private static void onEventDispatchThread(ThrowingRunnable runnable) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
            return;
        }

        try {
            SwingUtilities.invokeAndWait(() -> runUnchecked(runnable));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException && cause.getCause() != null) {
                Throwable wrappedCause = cause.getCause();
                if (wrappedCause instanceof Exception) {
                    throw (Exception) wrappedCause;
                }
                if (wrappedCause instanceof Error) {
                    throw (Error) wrappedCause;
                }
            }
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw e;
        }
    }

    private static void runUnchecked(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isNativeImageAgentActive() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .anyMatch(argument -> argument.contains("native-image-agent"));
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static class BoundChoiceBean {
        private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
        private String choice;

        public BoundChoiceBean() {
        }

        public String getChoice() {
            return choice;
        }

        public void setChoice(String choice) {
            String oldChoice = this.choice;
            this.choice = choice;
            propertyChangeSupport.firePropertyChange("choice", oldChoice, choice);
        }

        public void addPropertyChangeListener(PropertyChangeListener listener) {
            propertyChangeSupport.addPropertyChangeListener(listener);
        }

        public void removePropertyChangeListener(PropertyChangeListener listener) {
            propertyChangeSupport.removePropertyChangeListener(listener);
        }
    }
}
