/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_assertj.assertj_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashSet;
import java.util.List;

import org.assertj.core.api.StringAssert;
import org.junit.jupiter.api.Test;

public class ClassBasedNavigableIterableAssertTest {
    @Test
    void navigatesAnIterableWithTypedElementAssertions() {
        Iterable<String> stages = new LinkedHashSet<>(List.of("received", "packed", "shipped"));
        assertThat(stages, StringAssert.class).first().startsWith("rece").endsWith("ived");
        assertThat(stages, StringAssert.class).last().isEqualTo("shipped");
        assertThat(stages, StringAssert.class).element(1).isEqualTo("packed");
        assertThat(stages, StringAssert.class).filteredOn(stage -> stage.startsWith("p"))
                .singleElement().isEqualTo("packed");
    }

    @Test
    void reportsTypedElementAssertionFailures() {
        Iterable<String> stages = new LinkedHashSet<>(List.of("received", "packed"));
        AssertionError failure = assertThrows(AssertionError.class,
                () -> assertThat(stages, StringAssert.class).last().startsWith("ship"));
        assertThat(failure).hasMessageContaining("packed").hasMessageContaining("ship");
    }
}
