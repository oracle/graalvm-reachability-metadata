/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_assertj.assertj_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicReferenceArray;

import org.junit.jupiter.api.Test;

public class ArraysTest {
    @Test
    void assertsAtomicReferenceArrayContentsIncludingNullElements() {
        AtomicReferenceArray<String> stages = new AtomicReferenceArray<>(new String[]{"queued", null, "sent"});
        assertThat(stages).containsExactly("queued", null, "sent").containsNull();
        stages.set(1, "packed");
        assertThat(stages).containsExactly("queued", "packed", "sent").doesNotContainNull();
        AssertionError failure = assertThrows(AssertionError.class,
                () -> assertThat(stages).containsExactly("queued", "sent", "packed"));
        assertThat(failure).hasMessageContaining("packed").hasMessageContaining("sent");
    }
}
