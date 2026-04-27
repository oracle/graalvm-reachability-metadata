/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.awt.Component;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

import org.apache.log4j.Level;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ControlPanelTest {

    @Test
    void buildsTheChainsawControlPanelWithItsDefaultFiltersAndActions() throws Exception {
        AtomicReference<JPanel> controlPanelReference = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> controlPanelReference.set(newControlPanel()));

        JPanel controlPanel = controlPanelReference.get();
        assertThat(controlPanel).isNotNull();
        assertThat(controlPanel.getBorder())
                .isInstanceOfSatisfying(TitledBorder.class, border -> assertThat(border.getTitle()).isEqualTo("Controls: "));
        assertThat(findPrioritySelector(controlPanel).isEditable()).isFalse();
        assertThat(findPrioritySelector(controlPanel).getSelectedItem()).isEqualTo(Level.TRACE);
        assertThat(findComponents(controlPanel, JTextField.class)).hasSize(4);
        assertThat(buttonTexts(controlPanel)).containsExactly("Exit", "Clear", "Pause");
    }

    private static JPanel newControlPanel() {
        try {
            Class<?> modelClass = loadChainsawClass("org.apache.log4j.chainsaw.MyTableModel");
            Constructor<?> modelConstructor = modelClass.getDeclaredConstructor();
            modelConstructor.setAccessible(true);
            Object model = modelConstructor.newInstance();

            Class<?> controlPanelClass = loadChainsawClass("org.apache.log4j.chainsaw.ControlPanel");
            Constructor<?> controlPanelConstructor = controlPanelClass.getDeclaredConstructor(modelClass);
            controlPanelConstructor.setAccessible(true);
            return (JPanel) controlPanelConstructor.newInstance(model);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Class<?> loadChainsawClass(String className) throws ClassNotFoundException {
        return Class.forName(className, true, ControlPanelTest.class.getClassLoader());
    }

    private static JComboBox<?> findPrioritySelector(JPanel controlPanel) {
        return findComponents(controlPanel, JComboBox.class).get(0);
    }

    private static List<String> buttonTexts(JPanel controlPanel) {
        return findComponents(controlPanel, JButton.class).stream()
                .map(JButton::getText)
                .collect(Collectors.toList());
    }

    private static <T extends Component> List<T> findComponents(JPanel controlPanel, Class<T> componentType) {
        return Arrays.stream(controlPanel.getComponents())
                .filter(componentType::isInstance)
                .map(componentType::cast)
                .collect(Collectors.toList());
    }
}
