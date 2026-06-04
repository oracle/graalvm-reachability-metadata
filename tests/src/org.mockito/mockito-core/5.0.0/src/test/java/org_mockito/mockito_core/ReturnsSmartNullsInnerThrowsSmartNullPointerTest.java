/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.Mockito;
import org.mockito.exceptions.verification.SmartNullPointerException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ReturnsSmartNullsInnerThrowsSmartNullPointerTest {
    public interface Repository {
        Entity findEntity();
    }

    public static class Entity {
        public String name() {
            return "entity";
        }
    }

    @Test
    void unstubbedMethodOnSmartNullThrowsSmartNullPointerException() {
        Repository repository =
                Mockito.mock(
                        Repository.class,
                        Mockito.withSettings()
                                .defaultAnswer(Mockito.RETURNS_SMART_NULLS)
                                .mockMaker(MockMakers.SUBCLASS));

        Entity smartNull = repository.findEntity();
        SmartNullPointerException exception =
                assertThrows(SmartNullPointerException.class, smartNull::name);

        assertThat(exception)
                .hasMessageContaining("You have a NullPointerException here")
                .hasMessageContaining("findEntity");
    }
}
