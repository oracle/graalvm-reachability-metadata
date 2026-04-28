/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.beans.PropertyEditorSupport;

import com.thoughtworks.xstream.core.util.ThreadSafePropertyEditor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class ThreadSafePropertyEditorTest {
    @Test
    void acceptsPropertyEditorImplementations() {
        ThreadSafePropertyEditor editor = new ThreadSafePropertyEditor(PropertyEditorSupport.class, 0, 1);

        assertThat(editor).isNotNull();
    }

    @Test
    void rejectsTypesThatAreNotPropertyEditors() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ThreadSafePropertyEditor(String.class, 0, 1))
                .withMessageContaining(String.class.getName())
                .withMessageContaining("java.beans.PropertyEditor");
    }
}
