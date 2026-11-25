/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.propertyeditors.CustomMapEditor;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomMapEditorTest {
    @Test
    void createsConfiguredConcreteMap() {
        CustomMapEditor editor = new CustomMapEditor(StringMap.class);

        editor.setValue(Map.of("key", "value"));

        assertThat(editor.getValue()).isInstanceOf(StringMap.class);
        assertThat((StringMap) editor.getValue()).containsEntry("key", "value");
    }

    public static class StringMap extends HashMap<String, String> {
        private static final long serialVersionUID = 1L;
    }
}
