/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.beans.swing.PropertyBoundComboBox;
import com.mchange.v2.beans.swing.TestBean;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.beans.IntrospectionException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyComponentBindingUtilityTest {
    @Test
    void comboBoxSynchronizesBeanChangesAndUserSelections() throws Exception {
        TestBean bean = new TestBean();
        bean.setTheString("alpha");
        AtomicReference<PropertyBoundComboBox> comboBoxReference = new AtomicReference<>();

        runOnEventDispatchThread(() -> comboBoxReference.set(newComboBox(bean)));
        flushEventDispatchThread();

        PropertyBoundComboBox comboBox = comboBoxReference.get();
        assertThat(comboBox.getSelectedItem()).isEqualTo("alpha");

        runOnEventDispatchThread(() -> bean.setTheString("charlie"));
        assertThat(comboBox.getSelectedItem()).isEqualTo("charlie");

        runOnEventDispatchThread(() -> comboBox.setSelectedItem("bravo"));
        assertThat(bean.getTheString()).isEqualTo("bravo");
    }

    private static PropertyBoundComboBox newComboBox(TestBean bean) {
        try {
            return new PropertyBoundComboBox(bean, "theString", new String[] {"alpha", "bravo", "charlie"}, null);
        } catch (IntrospectionException e) {
            throw new AssertionError(e);
        }
    }

    private static void flushEventDispatchThread() throws InvocationTargetException, InterruptedException {
        runOnEventDispatchThread(() -> {
        });
    }

    private static void runOnEventDispatchThread(Runnable action) throws InvocationTargetException, InterruptedException {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeAndWait(action);
        }
    }
}
