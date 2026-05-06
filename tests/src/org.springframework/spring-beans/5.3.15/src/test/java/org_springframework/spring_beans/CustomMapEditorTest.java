/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.propertyeditors.CustomMapEditor;

public class CustomMapEditorTest {

    @Test
    public void setValueInstantiatesConcreteMapType() {
        RecordingMap.constructorCalls = 0;
        Map<String, String> source = new LinkedHashMap<>();
        source.put("alpha", "one");
        source.put("beta", "two");
        CustomMapEditor editor = new CustomMapEditor(RecordingMap.class);

        editor.setValue(source);

        assertThat(editor.getValue()).isInstanceOf(RecordingMap.class);
        assertThat((RecordingMap) editor.getValue()).containsEntry("alpha", "one").containsEntry("beta", "two");
        assertThat(RecordingMap.constructorCalls).isEqualTo(1);
    }

    public static class RecordingMap extends LinkedHashMap<Object, Object> {
        private static final long serialVersionUID = 1L;

        private static int constructorCalls;

        public RecordingMap() {
            constructorCalls++;
        }
    }
}
