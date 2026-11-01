/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.propertyeditors.CustomCollectionEditor;

import static org.junit.jupiter.api.Assertions.*;

public class CustomCollectionEditorTest {
    @Test
    void instantiatesConcreteCollectionAndCopiesElements() {
        CustomCollectionEditor editor = new CustomCollectionEditor(LinkedList.class);
        editor.setValue(new String[]{"alpha", "beta"});
        assertInstanceOf(LinkedList.class, editor.getValue());
        assertEquals(List.of("alpha", "beta"), editor.getValue());
    }
}
