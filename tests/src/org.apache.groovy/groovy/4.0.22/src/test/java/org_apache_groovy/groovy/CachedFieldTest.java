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

public class CachedFieldTest {
    @Test
    void readsAndWritesAFieldThroughGroovyProperties() {
        Counter counter = new Counter();
        InvokerHelper.setProperty(counter, "total", 23);
        assertEquals(23, counter.total);
        assertEquals(23, InvokerHelper.getProperty(counter, "total"));
    }

    public static class Counter {
        public int total;
    }
}
