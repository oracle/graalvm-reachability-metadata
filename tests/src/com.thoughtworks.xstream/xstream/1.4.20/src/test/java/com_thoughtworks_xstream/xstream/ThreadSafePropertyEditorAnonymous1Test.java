/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.beans.PropertyEditorSupport;
import java.util.Locale;

import com.thoughtworks.xstream.core.util.ThreadSafePropertyEditor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadSafePropertyEditorAnonymous1Test {
    @Test
    void createsPropertyEditorInstanceWhenEditorIsUsed() {
        ThreadSafePropertyEditor editor = new ThreadSafePropertyEditor(UppercasePropertyEditor.class, 1, 1);

        assertThat(editor.setAsText("graalvm")).isEqualTo("GRAALVM");
        assertThat(editor.getAsText("native-image")).isEqualTo("native-image");
    }

    public static class UppercasePropertyEditor extends PropertyEditorSupport {
        @Override
        public void setAsText(String text) {
            setValue(text.toUpperCase(Locale.ROOT));
        }

        @Override
        public String getAsText() {
            return String.valueOf(getValue());
        }
    }
}
