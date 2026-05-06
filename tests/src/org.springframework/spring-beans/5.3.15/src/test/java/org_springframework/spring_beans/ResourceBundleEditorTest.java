/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.propertyeditors.ResourceBundleEditor;

public class ResourceBundleEditorTest {

    private static final String BUNDLE_NAME = "ResourceBundleEditorMessages";

    @Test
    public void setAsTextLoadsBundleWithoutLocaleSeparator() {
        ResourceBundleEditor editor = new ResourceBundleEditor();

        editor.setAsText(BUNDLE_NAME);

        ResourceBundle bundle = (ResourceBundle) editor.getValue();
        assertThat(bundle.getString("common")).isEqualTo("available");
    }

    @Test
    public void setAsTextLoadsBundleWithParsedLocale() {
        ResourceBundleEditor editor = new ResourceBundleEditor();

        editor.setAsText(BUNDLE_NAME + "_en");

        ResourceBundle bundle = (ResourceBundle) editor.getValue();
        assertThat(bundle.getString("localized")).isEqualTo("english");
        assertThat(bundle.getString("common")).isEqualTo("available");
    }

    @Test
    public void setAsTextFallsBackToBaseBundleWhenLocaleTextIsEmpty() {
        ResourceBundleEditor editor = new ResourceBundleEditor();

        editor.setAsText(BUNDLE_NAME + "_");

        ResourceBundle bundle = (ResourceBundle) editor.getValue();
        assertThat(bundle.getString("common")).isEqualTo("available");
    }
}
