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

import org.junit.jupiter.api.Test;

public class IterableUtilTest {
    @Test
    void acceptsIterableValuesInStringAssertions() {
        Iterable<CharSequence> fragments = () -> List.<CharSequence>of("header-", "payload-").iterator();
        assertThat("header-payload-footer").contains(fragments)
                .containsSequence(fragments)
                .containsSubsequence(List.of("head", "load", "foot"))
                .doesNotContain(List.of("error", "missing"));
    }

    @Test
    void rejectsFragmentsThatAreInTheWrongOrder() {
        AssertionError failure = assertThrows(AssertionError.class,
                () -> assertThat("header-payload-footer").containsSequence(List.of("footer", "header")));
        assertThat(failure).hasMessageContaining("footer").hasMessageContaining("header");
    }
}
