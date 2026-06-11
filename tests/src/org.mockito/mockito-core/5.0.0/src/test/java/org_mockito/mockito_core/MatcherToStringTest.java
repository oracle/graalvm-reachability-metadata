/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;

public class MatcherToStringTest {
    @Test
    void verificationFailureRendersMatcherWithoutCustomToString() {
        TextSink sink = Mockito.mock(TextSink.class);
        sink.accept("actual");

        StartsWithMatcher matcher = new StartsWithMatcher("expected");

        assertThatThrownBy(() -> Mockito.verify(sink).accept(argThat(matcher)))
                .isInstanceOf(AssertionError.class);
    }

    interface TextSink {
        void accept(String value);
    }

    static class StartsWithMatcher implements ArgumentMatcher<String> {
        private final String prefix;

        StartsWithMatcher(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public boolean matches(String value) {
            return value != null && value.startsWith(prefix);
        }
    }
}
