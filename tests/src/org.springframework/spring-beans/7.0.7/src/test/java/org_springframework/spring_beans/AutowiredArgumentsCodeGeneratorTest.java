/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.aot.AutowiredArgumentsCodeGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AutowiredArgumentsCodeGeneratorTest {
    @Test
    void generatesTypeQualifiedArgumentsForOverloadedConstructors() throws Exception {
        AutowiredArgumentsCodeGenerator overloaded = new AutowiredArgumentsCodeGenerator(
                Overloaded.class, Overloaded.class.getConstructor(String.class));
        assertEquals("args.get(0, java.lang.String.class)",
                overloaded.generateCode(new Class<?>[] {String.class}).toString());
        AutowiredArgumentsCodeGenerator unique = new AutowiredArgumentsCodeGenerator(
                Unique.class, Unique.class.getConstructor(String.class));
        assertEquals("args.get(0)", unique.generateCode(new Class<?>[] {String.class}).toString());
    }

    public static class Overloaded {
        public Overloaded(String value) {
        }

        public Overloaded(Integer value) {
        }
    }

    public static class Unique {
        public Unique(String value) {
        }
    }
}
