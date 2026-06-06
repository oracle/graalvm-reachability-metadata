/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.lang.builder.EqualsBuilder;
import org.junit.jupiter.api.Test;

public class EqualsBuilderTest {

    @Test
    public void reflectionEqualsComparesPrivateInstanceFields() {
        EqualityRecord first = new EqualityRecord("alpha", 7, "ignored-one");
        EqualityRecord sameValues = new EqualityRecord("alpha", 7, "ignored-two");
        EqualityRecord differentName = new EqualityRecord("bravo", 7, "ignored-one");
        EqualityRecord differentPriority = new EqualityRecord("alpha", 8, "ignored-one");

        assertThat(EqualsBuilder.reflectionEquals(first, sameValues)).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(first, differentName)).isFalse();
        assertThat(EqualsBuilder.reflectionEquals(first, differentPriority)).isFalse();
    }

    private static final class EqualityRecord {
        private static final String TYPE = "record";

        private final String name;
        private final int priority;
        private transient String runtimeNote;

        private EqualityRecord(String name, int priority, String runtimeNote) {
            assertThat(TYPE).isEqualTo("record");
            this.name = name;
            this.priority = priority;
            this.runtimeNote = runtimeNote;
        }
    }
}
