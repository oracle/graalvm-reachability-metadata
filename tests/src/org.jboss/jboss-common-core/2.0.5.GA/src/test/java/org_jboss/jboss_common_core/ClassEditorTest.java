/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jboss_common_core;

import org.jboss.util.propertyeditor.ClassEditor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertSame;

public class ClassEditorTest {
    @Test
    void convertsConfiguredClassNamesAndCanBeReused() {
        ClassEditor editor = new ClassEditor();
        editor.setAsText("java.util.ArrayList");
        assertSame(ArrayList.class, editor.getValue());

        editor.setAsText("java.lang.String");
        assertSame(String.class, editor.getValue());
    }
}
