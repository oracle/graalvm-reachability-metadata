/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.propertyeditors.CustomCollectionEditor;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomCollectionEditorTest {
    @Test
    void createsConfiguredConcreteCollection() {
        CustomCollectionEditor editor = new CustomCollectionEditor(StringList.class);

        editor.setValue(List.of("one", "two"));

        assertThat(editor.getValue()).isInstanceOf(StringList.class);
        assertThat((StringList) editor.getValue()).containsExactly("one", "two");
    }

    public static class StringList extends ArrayList<String> {
        private static final long serialVersionUID = 1L;
    }
}
