/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.beans.swing.PropertyBoundTextField;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.beans.PropertyChangeSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyComponentBindingUtilityTest {
    @Test
    void propertyBoundTextFieldSyncsInitialAndUpdatedBeanValue() throws Exception {
        ObservableValueBean bean = new ObservableValueBean();
        bean.setValue("alpha");

        PropertyBoundTextField field = new PropertyBoundTextField(bean, "value", 12);
        flushEdt();

        assertThat(field.getText()).isEqualTo("alpha");

        bean.setValue("bravo");
        flushEdt();

        assertThat(field.getText()).isEqualTo("bravo");
    }

    @Test
    void propertyBoundTextFieldAppliesUserChangesBackToBean() throws Exception {
        ObservableValueBean bean = new ObservableValueBean();
        bean.setValue("before");

        PropertyBoundTextField field = new PropertyBoundTextField(bean, "value", 12);
        flushEdt();

        SwingUtilities.invokeAndWait(() -> {
            field.setText("after");
            field.postActionEvent();
        });
        flushEdt();

        assertThat(bean.getValue()).isEqualTo("after");
        assertThat(field.getText()).isEqualTo("after");
    }

    private static void flushEdt() throws Exception {
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

        public void addPropertyChangeListener(java.beans.PropertyChangeListener listener) {
            propertyChangeSupport.addPropertyChangeListener(listener);
        }

        public void removePropertyChangeListener(java.beans.PropertyChangeListener listener) {
            propertyChangeSupport.removePropertyChangeListener(listener);
        }
    }
}
