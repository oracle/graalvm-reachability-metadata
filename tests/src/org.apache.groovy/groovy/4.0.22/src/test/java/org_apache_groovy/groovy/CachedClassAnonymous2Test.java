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

public class CachedClassAnonymous2Test {
    @Test
    void discoversConstructorsForGroovyInstantiation() {
        Box box = (Box) InvokerHelper.invokeConstructorOf(Box.class, new Object[]{"books"});
        assertEquals("books", box.contents);
    }

    public static class Box {
        public final String contents;

        public Box(String contents) {
            this.contents = contents;
        }
    }
}
