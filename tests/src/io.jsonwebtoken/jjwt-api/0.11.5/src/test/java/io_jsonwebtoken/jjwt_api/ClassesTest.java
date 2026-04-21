/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_api;

import io.jsonwebtoken.lang.Classes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassesTest {

    @Test
    void newInstanceUsesPublicNoArgConstructor() {
        NoArgTarget target = Classes.newInstance(NoArgTarget.class.getName());

        assertThat(target.initializedByDefaultConstructor).isTrue();
    }

    @Test
    void newInstanceWithConstructorArgumentsUsesMatchingPublicConstructor() {
        ConstructorTarget target = Classes.newInstance(
            ConstructorTarget.class.getName(),
            new Class<?>[]{String.class, Integer.class},
            "jwt",
            12
        );

        assertThat(target.value).isEqualTo("jwt-12");
    }

    @Test
    void invokeStaticCallsPrivateDeclaredMethod() {
        String value = Classes.invokeStatic(
            StaticMethodTarget.class.getName(),
            "hiddenValue",
            new Class<?>[]{String.class, Integer.class},
            "claims",
            7
        );

        assertThat(value).isEqualTo("claims-7");
    }

    public static class NoArgTarget {
        private final boolean initializedByDefaultConstructor;

        public NoArgTarget() {
            this.initializedByDefaultConstructor = true;
        }
    }

    public static class ConstructorTarget {
        private final String value;

        public ConstructorTarget(String prefix, Integer number) {
            this.value = prefix + "-" + number;
        }
    }

    public static class StaticMethodTarget {

        private static String hiddenValue(String prefix, Integer number) {
            return prefix + "-" + number;
        }
    }
}
