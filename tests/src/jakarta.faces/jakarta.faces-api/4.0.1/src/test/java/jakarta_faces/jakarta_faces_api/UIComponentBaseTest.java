/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_faces.jakarta_faces_api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.faces.component.UICommand;
import jakarta.faces.component.UIComponentBase;
import jakarta.faces.event.ActionEvent;
import jakarta.faces.event.ActionListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class UIComponentBaseTest {
    @Test
    void restoresAttachedCollectionsAndMaps() {
        try (TestFacesContext context = new TestFacesContext()) {
            List<String> values = new ArrayList<>(List.of("first", "second"));
            Object listState = UIComponentBase.saveAttachedState(context, values);
            Object restoredList = UIComponentBase.restoreAttachedState(context, listState);
            assertNotSame(values, restoredList);
            assertEquals(values, restoredList);

            Map<String, String> options = new LinkedHashMap<>();
            options.put("theme", "dark");
            options.put("layout", "compact");
            Object mapState = UIComponentBase.saveAttachedState(context, options);
            Object restoredMap = UIComponentBase.restoreAttachedState(context, mapState);
            assertNotSame(options, restoredMap);
            assertEquals(options, restoredMap);
        }
    }

    @Test
    void actionListenersHaveTypedArraysBeforeAndAfterRegistration() {
        try (TestFacesContext context = new TestFacesContext()) {
            UICommand command = new UICommand();
            assertEquals(ActionListener[].class, command.getActionListeners().getClass());
            assertEquals(0, command.getActionListeners().length);
            RecordingActionListener listener = new RecordingActionListener();
            command.addActionListener(listener);
            assertArrayEquals(new ActionListener[]{listener}, command.getActionListeners());
            command.removeActionListener(listener);
            assertEquals(0, command.getActionListeners().length);
            assertEquals(ActionListener[].class, command.getActionListeners().getClass());
        }
    }

    private static final class RecordingActionListener implements ActionListener {
        @Override
        public void processAction(ActionEvent event) {
            event.getFacesContext().renderResponse();
        }
    }
}
