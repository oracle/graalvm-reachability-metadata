/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_formdev.flatlaf_extras;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.extras.FlatUIDefaultsInspector;
import com.formdev.flatlaf.util.DerivedColor;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

public class FlatUIDefaultsInspectorTest {
    private static final String DERIVED_COLOR_KEY = "Button.background";
    private static final String INSPECTOR_PREFS_NODE = "flatlaf-uidefaults-inspector";

    @Test
    void inspectorPanelResolvesDerivedUiDefaults() throws Exception {
        Object previousValue = UIManager.get(DERIVED_COLOR_KEY);
        clearInspectorPreferences();

        try {
            UIManager.put(DERIVED_COLOR_KEY, new DerivedColor(Color.BLUE));

            JComponent inspectorPanel = invokeOnEventDispatchThread(FlatUIDefaultsInspector::createInspectorPanel);
            JTable defaultsTable = findTable(inspectorPanel);

            assertThat(defaultsTable).isNotNull();
            assertThat(defaultsTable.getRowCount()).isPositive();
        } finally {
            UIManager.put(DERIVED_COLOR_KEY, previousValue);
        }
    }

    private static void clearInspectorPreferences() throws BackingStoreException {
        Preferences.userRoot().node(INSPECTOR_PREFS_NODE).clear();
    }

    private static <T> T invokeOnEventDispatchThread(ThrowingSupplier<T> supplier) throws Exception {
        if (SwingUtilities.isEventDispatchThread())
            return supplier.get();

        Result<T> result = new Result<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.value = supplier.get();
            } catch (Exception exception) {
                result.exception = exception;
            }
        });
        if (result.exception != null)
            throw result.exception;
        return result.value;
    }

    private static JTable findTable(Component component) {
        if (component instanceof JTable table)
            return table;
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JTable table = findTable(child);
                if (table != null)
                    return table;
            }
        }
        return null;
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final class Result<T> {
        private T value;
        private Exception exception;
    }
}
