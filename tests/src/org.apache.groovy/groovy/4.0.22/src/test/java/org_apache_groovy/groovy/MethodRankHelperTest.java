/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.GroovyRuntimeException;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.MethodRankHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MethodRankHelperTest {
    @Test
    void suggestsAnExistingConstructorForInvalidArguments() {
        GroovyRuntimeException error = assertThrows(GroovyRuntimeException.class,
                () -> InvokerHelper.invokeConstructorOf(Quantity.class, new Object[]{"many"}));
        assertTrue(error.getMessage().contains(Quantity.class.getName()));
        String suggestions = MethodRankHelper.getConstructorSuggestionString(Quantity.class, new Object[]{"many"});
        assertTrue(suggestions.contains("Possible solutions:"));
        assertTrue(suggestions.contains("java.lang.Integer"));
    }

    public static class Quantity {
        public Quantity(Integer amount) {
        }
    }
}
