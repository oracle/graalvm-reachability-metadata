/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jboss_common_core;

import org.jboss.util.Classes;
import org.jboss.util.propertyeditor.IntegerEditor;
import org.junit.jupiter.api.Test;

import java.beans.PropertyEditor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ClassesTest {
    @Test
    void instantiatesConfiguredPropertyEditor() {
        PropertyEditor editor = (PropertyEditor) Classes.instantiate(
                PropertyEditor.class, "forge.jboss.integerEditor", IntegerEditor.class.getName());

        assertInstanceOf(IntegerEditor.class, editor);
        editor.setAsText("37");
        assertEquals(37, editor.getValue());
    }

    @Test
    void convertsConfiguredParameterTypesIncludingArrays() throws Exception {
        List<String> names = List.of(IntegerEditor.class.getName(), "int", "long[][]");
        Class<?>[] types = Classes.convertToJavaClasses(names.iterator(), ClassesTest.class.getClassLoader());

        assertArrayEquals(new Class<?>[]{IntegerEditor.class, int.class, long[][].class}, types);
    }
}
