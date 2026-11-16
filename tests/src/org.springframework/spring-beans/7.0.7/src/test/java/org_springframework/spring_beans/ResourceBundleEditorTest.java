/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.propertyeditors.ResourceBundleEditor;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResourceBundleEditorTest {
    @Test
    void loadsDefaultExplicitAndEmptyLocaleBundles() {
        ResourceBundleEditor editor = new ResourceBundleEditor();
        editor.setAsText("springbeansmessages");
        assertEquals("ready", ((ResourceBundle) editor.getValue()).getString("status"));
        editor.setAsText("springbeansmessages_fr");
        assertEquals("pret", ((ResourceBundle) editor.getValue()).getString("status"));
        editor.setAsText("springbeansmessages_");
        assertEquals("ready", ((ResourceBundle) editor.getValue()).getString("status"));
    }
}
