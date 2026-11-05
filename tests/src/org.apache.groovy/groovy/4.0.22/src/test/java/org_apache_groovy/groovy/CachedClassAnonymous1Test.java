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

public class CachedClassAnonymous1Test {
    @Test
    void discoversFieldsForPropertyAccess() {
        Stock stock = new Stock();
        assertEquals("available", InvokerHelper.getProperty(stock, "status"));
        InvokerHelper.setProperty(stock, "status", "reserved");
        assertEquals("reserved", stock.status);
    }

    public static class Stock {
        public String status = "available";
    }
}
