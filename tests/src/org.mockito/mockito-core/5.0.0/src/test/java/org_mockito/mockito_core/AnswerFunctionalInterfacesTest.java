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
import org.mockito.stubbing.Answer1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

public class AnswerFunctionalInterfacesTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void stronglyTypedAnswerStubsMockThroughPublicApi() {
        GreetingService service = Mockito.mock(GreetingService.class);
        Answer1<String, String> answer = name -> "Hello, " + name;

        Mockito.when(service.greet(anyString())).thenAnswer(AdditionalAnswers.answer(answer));

        assertThat(service.greet("Mockito")).isEqualTo("Hello, Mockito");
    }

    private interface GreetingService {
        String greet(String name);
    }
}
