/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_assertj.assertj_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.assertj.core.api.StringAssert;
import org.junit.jupiter.api.Test;

public class ClassBasedNavigableListAssertTest {
    @Test
    void navigatesAndFiltersAListWithTypedAssertions() {
        List<String> priorities = List.of("low", "normal", "urgent");
        assertThat(priorities, StringAssert.class).first().isEqualTo("low");
        assertThat(priorities, StringAssert.class).element(1).hasSize(6);
        assertThat(priorities, StringAssert.class).last().startsWith("urg");
        assertThat(priorities, StringAssert.class).filteredOn(priority -> priority.startsWith("n"))
                .singleElement().isEqualTo("normal");
    }

    @Test
    void includesTheElementDescriptionInFailures() {
        AssertionError failure = assertThrows(AssertionError.class,
                () -> assertThat(List.of("normal"), StringAssert.class).as("priorities")
                        .element(0).isEqualTo("urgent"));
        assertThat(failure).hasMessageContaining("priorities")
                .hasMessageContaining("normal").hasMessageContaining("urgent");
    }
}
