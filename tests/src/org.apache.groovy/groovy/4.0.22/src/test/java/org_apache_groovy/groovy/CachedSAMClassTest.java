/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CachedSAMClassTest {
    @Test
    void coercesAClosureToASingleAbstractMethodInterface() {
        Closure<String> body = new Closure<String>(this) {
            public String doCall(String value) {
                return value + "!";
            }
        };
        Formatter formatter = DefaultGroovyMethods.asType(body, Formatter.class);
        assertEquals("done!", formatter.format("done"));
    }

    public interface Formatter {
        String format(String value);

        boolean equals(Object other);
    }
}
