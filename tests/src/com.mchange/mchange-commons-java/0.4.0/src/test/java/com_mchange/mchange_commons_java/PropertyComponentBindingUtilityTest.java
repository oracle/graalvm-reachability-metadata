/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.beans.swing.PropertyBoundTextField;
import org.junit.jupiter.api.Test;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.beans.IntrospectionException;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyComponentBindingUtilityTest {
    @Test
    void propertyBoundTextFieldSynchronizesBeanPropertyAndUserEdits() throws Exception {
        BoundBean bean = new BoundBean();
        bean.setName("initial");

        PropertyBoundTextField[] fieldHolder = new PropertyBoundTextField[1];
        AtomicReference<IntrospectionException> creationFailure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                fieldHolder[0] = new PropertyBoundTextField(bean, "name", 20);
            } catch (IntrospectionException e) {
                creationFailure.set(e);
            }
        });
        if (creationFailure.get() != null) {
            throw creationFailure.get();
        }
        drainSwingEvents();

        PropertyBoundTextField field = fieldHolder[0];
        assertThat(readText(field)).isEqualTo("initial");

        SwingUtilities.invokeAndWait(() -> {
            field.setText("updated");
            field.postActionEvent();
        });

        assertThat(bean.getName()).isEqualTo("updated");

        bean.setName("external");
        drainSwingEvents();

        assertThat(readText(field)).isEqualTo("external");
    }

    private static void drainSwingEvents() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private static String readText(JTextField textField) throws Exception {
        AtomicReference<String> text = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> text.set(textField.getText()));
        return text.get();
    }

    public static class BoundBean {
        private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            String oldName = this.name;
            this.name = name;
            propertyChangeSupport.firePropertyChange("name", oldName, name);
        }

        public void addPropertyChangeListener(PropertyChangeListener listener) {
            propertyChangeSupport.addPropertyChangeListener(listener);
        }

        public void removePropertyChangeListener(PropertyChangeListener listener) {
            propertyChangeSupport.removePropertyChangeListener(listener);
        }
    }
}
