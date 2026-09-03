/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.beans.swing.PropertyBoundComboBox;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyComponentBindingUtilityTest {
    @Test
    void comboBoxBindingReadsAndWritesBeanPropertyThroughPublicSwingBinding() throws Exception {
        ChoiceBean bean = new ChoiceBean("alpha");

        PropertyBoundComboBox comboBox = onEventDispatchThread(() -> new PropertyBoundComboBox(
                bean,
                "choice",
                new String[] {"alpha", "bravo", "none"},
                "none"));
        flushSwingEvents();

        assertThat(bean.getPropertyChangeListenerAddCount()).isEqualTo(1);
        assertThat(bean.getChoiceGetterInvocationCount()).isGreaterThanOrEqualTo(1);
        assertThat(comboBox.getSelectedItem()).isEqualTo("alpha");

        bean.resetAccessCounts();
        onEventDispatchThread(() -> comboBox.setSelectedItem("bravo"));

        assertThat(bean.choice()).isEqualTo("bravo");
        assertThat(bean.getChoiceGetterInvocationCount()).isGreaterThanOrEqualTo(1);
        assertThat(bean.getChoiceSetterInvocationCount()).isGreaterThanOrEqualTo(1);
    }

    private static void flushSwingEvents() throws Exception {
        onEventDispatchThread(() -> {
        });
    }

    private static <T> T onEventDispatchThread(ThrowingSupplier<T> supplier) throws Exception {
        Result<T> result = new Result<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.value = supplier.get();
            } catch (Exception e) {
                result.exception = e;
            }
        });
        if (result.exception != null) {
            throw result.exception;
        }
        return result.value;
    }

    private static void onEventDispatchThread(ThrowingRunnable runnable) throws Exception {
        onEventDispatchThread(() -> {
            runnable.run();
            return null;
        });
    }

    public static class ChoiceBean {
        private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
        private String choice;
        private int propertyChangeListenerAddCount;
        private int choiceGetterInvocationCount;
        private int choiceSetterInvocationCount;

        public ChoiceBean(String choice) {
            this.choice = choice;
        }

        public String getChoice() {
            choiceGetterInvocationCount++;
            return choice;
        }

        public void setChoice(String choice) {
            choiceSetterInvocationCount++;
            String oldChoice = this.choice;
            this.choice = choice;
            propertyChangeSupport.firePropertyChange("choice", oldChoice, choice);
        }

        public void addPropertyChangeListener(PropertyChangeListener listener) {
            propertyChangeListenerAddCount++;
            propertyChangeSupport.addPropertyChangeListener(listener);
        }

        public void removePropertyChangeListener(PropertyChangeListener listener) {
            propertyChangeSupport.removePropertyChangeListener(listener);
        }

        public String choice() {
            return choice;
        }

        public int getPropertyChangeListenerAddCount() {
            return propertyChangeListenerAddCount;
        }

        public int getChoiceGetterInvocationCount() {
            return choiceGetterInvocationCount;
        }

        public int getChoiceSetterInvocationCount() {
            return choiceSetterInvocationCount;
        }

        public void resetAccessCounts() {
            choiceGetterInvocationCount = 0;
            choiceSetterInvocationCount = 0;
        }
    }

    private static final class Result<T> {
        private T value;
        private Exception exception;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
