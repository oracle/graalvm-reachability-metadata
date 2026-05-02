/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.beans.swing.PropertyBoundTextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.beans.IntrospectionException;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyComponentBindingUtilityTest {
    @BeforeAll
    static void configureJavaHome() {
        String environmentJavaHome = System.getenv("JAVA_HOME");
        if (System.getProperty("java.home") == null && environmentJavaHome != null) {
            System.setProperty("java.home", environmentJavaHome);
        }
    }

    @Test
    void textFieldBindingReadsInitialBeanValueAndListensForBeanChanges()
            throws IntrospectionException, InvocationTargetException, InterruptedException {
        ObservableValueBean bean = new ObservableValueBean();
        bean.setValue("initial");

        PropertyBoundTextField textField = new PropertyBoundTextField(bean, "value", 20);
        flushSwingEvents();

        assertThat(textField.getText()).isEqualTo("initial");

        bean.setValue("from-bean");

        assertThat(textField.getText()).isEqualTo("from-bean");
    }

    @Test
    void textFieldActionWritesUserModificationBackToBean()
            throws IntrospectionException, InvocationTargetException, InterruptedException {
        ObservableValueBean bean = new ObservableValueBean();
        bean.setValue("initial");
        PropertyBoundTextField textField = new PropertyBoundTextField(bean, "value", 20);
        flushSwingEvents();

        textField.setText("from-user");
        textField.postActionEvent();

        assertThat(bean.getValue()).isEqualTo("from-user");
    }

    private static void flushSwingEvents() throws InvocationTargetException, InterruptedException {
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
