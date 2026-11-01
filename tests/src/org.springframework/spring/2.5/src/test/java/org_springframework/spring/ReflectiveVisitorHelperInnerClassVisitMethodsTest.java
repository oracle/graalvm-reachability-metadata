/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.core.ReflectiveVisitorHelper;

import static org.junit.jupiter.api.Assertions.*;

public class ReflectiveVisitorHelperInnerClassVisitMethodsTest {
    public static class Visitor {
        public String visit(Number value) {
            return "number:" + value;
        }

        public String visit(Object value) {
            return "other";
        }

        public String visitNull() {
            return "empty";
        }
    }

    @Test
    void selectsSuperclassNullAndDefaultVisitorMethods() {
        ReflectiveVisitorHelper helper = new ReflectiveVisitorHelper();
        Visitor visitor = new Visitor();
        assertEquals("number:4", helper.invokeVisit(visitor, 4));
        assertEquals("empty", helper.invokeVisit(visitor, null));
        assertEquals("other", helper.invokeVisit(visitor, new Object()));
    }
}
