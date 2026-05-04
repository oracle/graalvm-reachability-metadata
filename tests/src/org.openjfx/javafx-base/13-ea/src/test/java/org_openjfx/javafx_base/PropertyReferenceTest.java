/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjfx.javafx_base;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.javafx.property.PropertyReference;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.junit.jupiter.api.Test;

public class PropertyReferenceTest {

    @Test
    void reflectsReadableWritablePropertyAndObservablePropertyGetter() {
        PropertyReference<String> reference = new PropertyReference<>(StandardBean.class, "name");

        assertThat(reference.isReadable()).isTrue();
        assertThat(reference.isWritable()).isTrue();
        assertThat(reference.hasProperty()).isTrue();
        assertThat(reference.getType()).isEqualTo(String.class);
    }

    @Test
    void reflectsBooleanPropertyUsingIsGetter() {
        PropertyReference<Boolean> reference = new PropertyReference<>(BooleanBean.class, "active");

        assertThat(reference.isReadable()).isTrue();
        assertThat(reference.isWritable()).isTrue();
        assertThat(reference.hasProperty()).isFalse();
        assertThat(reference.getType()).isEqualTo(boolean.class);
    }

    @Test
    void reflectsWriteOnlyPropertyByScanningPublicMethods() {
        PropertyReference<Integer> reference = new PropertyReference<>(WriteOnlyBean.class, "score");

        assertThat(reference.isReadable()).isFalse();
        assertThat(reference.isWritable()).isTrue();
        assertThat(reference.hasProperty()).isFalse();
        assertThat(reference.getType()).isEqualTo(int.class);
    }

    public static final class StandardBean {
        private final StringProperty name = new SimpleStringProperty(this, "name", "Ada");

        public String getName() {
            return name.get();
        }

        public void setName(String newName) {
            name.set(newName);
        }

        public StringProperty nameProperty() {
            return name;
        }
    }

    public static final class BooleanBean {
        private boolean active = true;

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }

    public static final class WriteOnlyBean {
        private int score;

        public void setScore(int score) {
            this.score = score;
        }
    }
}
