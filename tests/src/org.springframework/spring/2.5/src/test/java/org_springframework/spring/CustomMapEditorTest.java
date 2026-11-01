/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.propertyeditors.CustomMapEditor;

import static org.junit.jupiter.api.Assertions.*;

public class CustomMapEditorTest {
    @Test
    void instantiatesSortedMapAndCopiesEntries() {
        CustomMapEditor editor = new CustomMapEditor(TreeMap.class);
        editor.setValue(Map.of("second", 2, "first", 1));
        TreeMap<?, ?> result = assertInstanceOf(TreeMap.class, editor.getValue());
        assertEquals("first", result.firstKey());
        assertEquals(2, result.get("second"));
    }
}
