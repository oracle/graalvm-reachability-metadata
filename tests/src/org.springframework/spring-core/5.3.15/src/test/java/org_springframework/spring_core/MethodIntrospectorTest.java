/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodIntrospector;

/** Verifies invocable method selection on interfaces and concrete targets. */
public class MethodIntrospectorTest {
    @Test
    void selectsMethodsFromTargetInterfaceAndTargetClass() throws Exception {
        Method sourceMethod = SourceType.class.getMethod("handle", String.class);

        Method interfaceMethod = MethodIntrospector.selectInvocableMethod(sourceMethod, InterfaceTarget.class);
        Method concreteMethod = MethodIntrospector.selectInvocableMethod(sourceMethod, ConcreteTarget.class);

        assertThat(interfaceMethod.getDeclaringClass()).isEqualTo(Handler.class);
        assertThat(concreteMethod.getDeclaringClass()).isEqualTo(ConcreteTarget.class);
    }

    public static class SourceType {
        public String handle(String value) {
            return "source:" + value;
        }
    }

    public interface Handler {
        String handle(String value);
    }

    public static final class InterfaceTarget implements Handler {
        @Override
        public String handle(String value) {
            return "interface:" + value;
        }
    }

    public static final class ConcreteTarget {
        public String handle(String value) {
            return "concrete:" + value;
        }
    }
}
