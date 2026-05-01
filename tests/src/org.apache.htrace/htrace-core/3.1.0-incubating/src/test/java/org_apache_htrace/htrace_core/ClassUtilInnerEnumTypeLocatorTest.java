/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import java.util.EnumMap;
import java.util.EnumSet;

import org.apache.htrace.fasterxml.jackson.databind.util.ClassUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilInnerEnumTypeLocatorTest {
    @Test
    void findEnumTypeUsesLocatorForEmptyEnumSet() {
        EnumSet<TraceState> states = EnumSet.noneOf(TraceState.class);

        try {
            Class<? extends Enum<?>> enumType = ClassUtil.findEnumType(states);

            assertThat(enumType).isEqualTo(TraceState.class);
        } catch (IllegalArgumentException | IllegalStateException e) {
            assertExpectedJdkFieldAccessFailure(e);
        }
    }

    @Test
    void findEnumTypeUsesLocatorForEmptyEnumMap() {
        EnumMap<TraceState, String> statesByName = new EnumMap<>(TraceState.class);

        try {
            Class<? extends Enum<?>> enumType = ClassUtil.findEnumType(statesByName);

            assertThat(enumType).isEqualTo(TraceState.class);
        } catch (IllegalArgumentException | IllegalStateException e) {
            assertExpectedJdkFieldAccessFailure(e);
        }
    }

    private static void assertExpectedJdkFieldAccessFailure(RuntimeException exception) {
        if (exception instanceof IllegalArgumentException) {
            assertThat(exception).hasCauseInstanceOf(IllegalAccessException.class);
        } else {
            assertThat(exception).hasMessageContaining("Can not figure out type");
        }
    }

    private enum TraceState {
        STARTED,
        FINISHED
    }
}
