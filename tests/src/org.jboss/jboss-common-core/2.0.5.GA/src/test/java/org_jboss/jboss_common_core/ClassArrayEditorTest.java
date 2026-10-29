/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jboss_common_core;

import org.jboss.util.propertyeditor.ClassArrayEditor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClassArrayEditorTest {
    @Test
    void parsesClassNamesSeparatedByCommasAndWhitespace() {
        ClassArrayEditor editor = new ClassArrayEditor();
        editor.setAsText("java.lang.String,\tjava.lang.Integer\njava.util.HashMap");

        assertArrayEquals(new Class<?>[]{String.class, Integer.class, HashMap.class},
                (Class<?>[]) editor.getValue());
        assertEquals("java.lang.String,java.lang.Integer,java.util.HashMap", editor.getAsText());
    }
}
