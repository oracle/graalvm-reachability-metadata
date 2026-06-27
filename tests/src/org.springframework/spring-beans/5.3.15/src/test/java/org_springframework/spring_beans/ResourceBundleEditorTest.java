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

    private static final String BASE_NAME = "springbeans.messages.ResourceBundleEditorMessages";

    private final ResourceBundleEditor editor = new ResourceBundleEditor();

    @Test
    void loadsResourceBundleFromBaseName() {
        editor.setAsText(BASE_NAME);

        ResourceBundle bundle = (ResourceBundle) editor.getValue();

        assertThat(bundle.getString("baseOnly")).isEqualTo("base bundle");
    }

    @Test
    void loadsResourceBundleFromBaseNameAndLocale() {
        editor.setAsText(BASE_NAME + "_en_GB");

        ResourceBundle bundle = (ResourceBundle) editor.getValue();

        assertThat(bundle.getString("message")).isEqualTo("localized British English bundle");
        assertThat(bundle.getString("localeOnly")).isEqualTo("locale-specific value");
    }

    @Test
    void fallsBackToBaseNameWhenLocaleSuffixIsEmpty() {
        editor.setAsText(BASE_NAME + "_");

        ResourceBundle bundle = (ResourceBundle) editor.getValue();

        assertThat(bundle.getString("baseOnly")).isEqualTo("base bundle");
    }
}
