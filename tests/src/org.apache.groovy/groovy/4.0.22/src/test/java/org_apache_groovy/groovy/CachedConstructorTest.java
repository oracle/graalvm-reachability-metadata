/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CachedConstructorTest {
    @Test
    void selectsAndInvokesAnArgumentTakingConstructor() {
        Ticket ticket = (Ticket) InvokerHelper.invokeConstructorOf(Ticket.class, new Object[]{"west", 4});
        assertEquals("west:4", ticket.description());
    }

    public static class Ticket {
        private final String destination;
        private final int seat;

        public Ticket(String destination, int seat) {
            this.destination = destination;
            this.seat = seat;
        }

        public String description() {
            return destination + ":" + seat;
        }
    }
}
