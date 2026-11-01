/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.propertyeditors.ResourceBundleEditor;

import static org.junit.jupiter.api.Assertions.*;

public class ResourceBundleEditorTest {
    @Test
    void parsesBundleNamesWithAndWithoutLocale() {
        ResourceBundleEditor editor = new ResourceBundleEditor();
        for (String name : new String[]{"springtest.messages", "springtest.messages_", "springtest.messages_fr"}) {
            editor.setAsText(name);
            assertEquals("base", ((ResourceBundle) editor.getValue()).getString("marker"));
        }
        assertEquals("Bonjour {0}", ((ResourceBundle) editor.getValue()).getString("welcome"));
    }
}
