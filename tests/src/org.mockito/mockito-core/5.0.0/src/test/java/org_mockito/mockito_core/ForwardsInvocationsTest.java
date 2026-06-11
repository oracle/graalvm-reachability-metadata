/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

public class ForwardsInvocationsTest {
    @Test
    void mockCanDelegateInvocationsToAnotherPublicObject() {
        Greeting mock = Mockito.mock(Greeting.class, AdditionalAnswers.delegatesTo(new Delegate()));

        assertThat(mock.greet("Mockito")).isEqualTo("Hello Mockito");
        Mockito.verify(mock).greet("Mockito");
    }

    interface Greeting {
        String greet(String name);
    }

    public static class Delegate {
        public String greet(String name) {
            return "Hello " + name;
        }
    }
}
