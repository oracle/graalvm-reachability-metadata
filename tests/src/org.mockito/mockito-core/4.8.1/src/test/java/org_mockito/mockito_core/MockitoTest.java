/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class MockitoTest {
    @Test
    void test() {
        MyService myService = Mockito.mock(MyService.class);
        Mockito.when(myService.getGreeting()).thenReturn("Hello Mockito");
        assertThat(myService.getGreeting()).isEqualTo("Hello Mockito");
    }
}
