/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jboss_common_core;

import org.jboss.util.propertyeditor.PropertyEditors;
import org.junit.jupiter.api.Test;

import java.beans.PropertyEditor;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PropertyEditorsInnerInitializeTest {
    @Test
    void discoversRegisteredArrayEditorsAndConvertsValues() {
        PropertyEditor strings = PropertyEditors.getEditor(String[].class);
        strings.setAsText("east,west\nnorth");
        assertArrayEquals(new String[]{"east", "west", "north"}, (String[]) strings.getValue());
        assertEquals("east,west,north", strings.getAsText());

        PropertyEditor integers = PropertyEditors.getEditor(int[].class);
        integers.setAsText("10,0x20\n-3");
        assertArrayEquals(new int[]{10, 32, -3}, (int[]) integers.getValue());
        assertEquals("10,32,-3", integers.getAsText());

        PropertyEditor bytes = PropertyEditors.getEditor(byte[].class);
        bytes.setAsText("queue");
        assertArrayEquals("queue".getBytes(StandardCharsets.UTF_8), (byte[]) bytes.getValue());
    }

    @Test
    void discoversPrimitiveCharacterEditor() {
        PropertyEditor character = PropertyEditors.getEditor(char.class);
        character.setAsText("Q");
        assertEquals('Q', character.getValue());
    }
}
