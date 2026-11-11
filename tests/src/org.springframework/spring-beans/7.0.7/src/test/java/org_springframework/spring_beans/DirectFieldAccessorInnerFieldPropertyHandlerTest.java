/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.DirectFieldAccessor;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DirectFieldAccessorInnerFieldPropertyHandlerTest {
    @Test
    void bindsPrivateFieldsWithoutBeanAccessors() {
        Credentials credentials = new Credentials();
        DirectFieldAccessor accessor = new DirectFieldAccessor(credentials);
        assertEquals("guest", accessor.getPropertyValue("username"));
        accessor.setPropertyValue("username", "operator");
        accessor.setPropertyValue("attempts", "5");
        assertEquals("operator", credentials.username);
        assertEquals(5, accessor.getPropertyValue("attempts"));
    }

    public static class Credentials {
        private String username = "guest";
        private int attempts;
    }
}
