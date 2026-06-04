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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;

public class MatcherToStringTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void verificationFailureDescribesCustomArgumentMatcherByItsClassName() {
        GreetingSink sink = Mockito.mock(GreetingSink.class);
        sink.accept("hello");

        AssertionError error =
                assertThrows(
                        AssertionError.class,
                        () -> Mockito.verify(sink).accept(argThat(new StartsWithCapitalLetter())));

        assertThat(error).hasMessageContaining("<Starts with capital letter>");
        assertThat(error).hasMessageContaining("hello");
    }

    private interface GreetingSink {
        void accept(String value);
    }

    private static final class StartsWithCapitalLetter implements ArgumentMatcher<String> {
        @Override
        public boolean matches(String value) {
            return value != null
                    && !value.isEmpty()
                    && Character.isUpperCase(value.codePointAt(0));
        }
    }
}
