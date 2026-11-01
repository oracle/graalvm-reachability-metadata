/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.core.ReflectiveVisitorHelper;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReflectiveVisitorHelperTest {
    public static class Visitor {
        public String visit(String value) {
            return value.toUpperCase();
        }
    }

    @Test
    void dispatchesToTypedVisitorAndCachesMethod() {
        ReflectiveVisitorHelper helper = new ReflectiveVisitorHelper();
        Visitor visitor = new Visitor();
        assertEquals("ALPHA", helper.invokeVisit(visitor, "alpha"));
        assertEquals("BETA", helper.invokeVisit(visitor, "beta"));
    }
}
