/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.beans.swing.PropertyBoundTextField;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyComponentBindingUtilityTest {
    @Test
    void textFieldBindingReadsInitialBeanValue() throws Exception {
        ObservableValueBean bean = new ObservableValueBean();
        bean.setValue("initial");

        PropertyBoundTextField textField = new PropertyBoundTextField(bean, "value", 20);
        flushSwingEvents();

        assertThat(textField.getText()).isEqualTo("initial");
    }

    @Test
    void textFieldActionWritesUserModificationToBean() throws Exception {
        ObservableValueBean bean = new ObservableValueBean();
        bean.setValue("before");
        PropertyBoundTextField textField = new PropertyBoundTextField(bean, "value", 20);
        flushSwingEvents();

        textField.setText("after");
        textField.postActionEvent();

        assertThat(bean.getValue()).isEqualTo("after");
    }

    private static void flushSwingEvents() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    public static class ObservableValueBean {
        private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
        private String value;

        public ObservableValueBean() {
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
