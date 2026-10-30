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

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceBundleEditorTest {
    @Test
    void loadsDefaultAndLocalizedBundles() {
        ResourceBundleEditor editor = new ResourceBundleEditor();
        editor.setAsText("springbeansdefault");
        ResourceBundle defaultBundle = (ResourceBundle) editor.getValue();

        editor.setAsText("springbeansdefault_");
        ResourceBundle emptyLocaleBundle = (ResourceBundle) editor.getValue();

        editor.setAsText("springbeansmessages_en");
        ResourceBundle englishBundle = (ResourceBundle) editor.getValue();

        assertThat(defaultBundle.getString("greeting")).isEqualTo("hello");
        assertThat(emptyLocaleBundle.getString("greeting")).isEqualTo("hello");
        assertThat(englishBundle.getString("greeting")).isEqualTo("hello-en");
    }
}
