/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.exceptions.verification.SmartNullPointerException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_SMART_NULLS;

public class ReturnsSmartNullsInnerThrowsSmartNullPointerTest {
    @Test
    void smartNullReportsTheUnstubbedInvocationWhenUsed() {
        Parent parent = Mockito.mock(Parent.class, RETURNS_SMART_NULLS);

        assertThatThrownBy(() -> parent.child().value())
                .isInstanceOf(SmartNullPointerException.class);
    }

    interface Parent {
        Child child();
    }

    interface Child {
        String value();
    }
}
