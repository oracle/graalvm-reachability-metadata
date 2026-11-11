/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.util.ArrayDeque;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.propertyeditors.CustomCollectionEditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class CustomCollectionEditorTest {
    @Test
    void convertsArrayToConcreteCollection() {
        CustomCollectionEditor editor = new CustomCollectionEditor(ArrayDeque.class);
        editor.setValue(new String[] {"one", "two"});
        ArrayDeque<?> result = assertInstanceOf(ArrayDeque.class, editor.getValue());
        assertEquals(List.of("one", "two"), List.copyOf(result));
    }
}
