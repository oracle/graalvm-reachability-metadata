/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.propertyeditors.CustomCollectionEditor;

public class CustomCollectionEditorTest {

    @Test
    public void setValueInstantiatesConcreteCollectionType() {
        RecordingCollection.constructorCalls = 0;
        CustomCollectionEditor editor = new CustomCollectionEditor(RecordingCollection.class);

        editor.setValue(List.of("alpha", "beta"));

        assertThat(editor.getValue()).isInstanceOf(RecordingCollection.class);
        assertThat((RecordingCollection) editor.getValue()).containsExactly("alpha", "beta");
        assertThat(RecordingCollection.constructorCalls).isEqualTo(1);
    }

    public static class RecordingCollection extends ArrayList<Object> {
        private static final long serialVersionUID = 1L;

        private static int constructorCalls;

        public RecordingCollection() {
            constructorCalls++;
        }
    }
}
