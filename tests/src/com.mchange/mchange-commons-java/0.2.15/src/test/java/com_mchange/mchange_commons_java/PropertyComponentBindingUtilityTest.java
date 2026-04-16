/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import com.mchange.v2.beans.swing.PropertyBoundTextField;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyComponentBindingUtilityTest {
    @Test
    void propertyBoundTextFieldSyncsFromTheBean() throws Exception {
        MutableBoundBean bean = new MutableBoundBean("alpha");
        PropertyBoundTextField field = callOnEdt(() -> new PropertyBoundTextField(bean, "value", 12));

        flushEdt();

        String initialText = callOnEdt(() -> field.getText());
        assertThat(initialText).isEqualTo("alpha");

        runOnEdt(() -> bean.setValue("beta"));

        String updatedText = callOnEdt(() -> field.getText());
        assertThat(updatedText).isEqualTo("beta");
    }

    @Test
    void propertyBoundTextFieldWritesUserChangesBackToTheBean() throws Exception {
        MutableBoundBean bean = new MutableBoundBean("before");
        PropertyBoundTextField field = callOnEdt(() -> new PropertyBoundTextField(bean, "value", 12));

        flushEdt();

        runOnEdt(() -> {
            field.setText("after");
            field.postActionEvent();
        });

        assertThat(bean.getValue()).isEqualTo("after");
    }

    private static void flushEdt() throws Exception {
        runOnEdt(() -> {
        });
    }

    private static void runOnEdt(ThrowingRunnable action) throws Exception {
        callOnEdt(() -> {
            action.run();
            return null;
        });
    }

    private static <T> T callOnEdt(ThrowingSupplier<T> supplier) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return supplier.get();
        }

        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(supplier.get());
            } catch (Exception exception) {
                failure.set(exception);
            }
        });

        Exception exception = failure.get();
        if (exception != null) {
            throw exception;
        }
        return result.get();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    public static final class MutableBoundBean {
        private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
        private String value;

        public MutableBoundBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            String oldValue = this.value;
            this.value = value;
            propertyChangeSupport.firePropertyChange("value", oldValue, value);
        }

        public void addPropertyChangeListener(PropertyChangeListener listener) {
            propertyChangeSupport.addPropertyChangeListener(listener);
        }

        public void removePropertyChangeListener(PropertyChangeListener listener) {
            propertyChangeSupport.removePropertyChangeListener(listener);
        }
    }
}
