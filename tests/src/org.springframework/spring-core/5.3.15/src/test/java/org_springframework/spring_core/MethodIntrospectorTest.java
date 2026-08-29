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

/** Verifies selection of methods exposed by target interfaces and classes. */
public class MethodIntrospectorTest {
    @Test
    void selectsInvocableMethodsFromInterfaceAndTargetClass() throws Exception {
        Method sourceMethod = SourceOperations.class.getMethod("operation", String.class);

        Method interfaceMethod =
                MethodIntrospector.selectInvocableMethod(sourceMethod, InterfaceTarget.class);
        Method classMethod = MethodIntrospector.selectInvocableMethod(sourceMethod, ClassTarget.class);

        assertThat(interfaceMethod.getDeclaringClass()).isEqualTo(Operations.class);
        assertThat(classMethod.getDeclaringClass()).isEqualTo(ClassTarget.class);
    }

    public static class SourceOperations {
        public String operation(String value) {
            return value;
        }
    }

    public interface Operations {
        String operation(String value);
    }

    public static final class InterfaceTarget implements Operations {
        @Override
        public String operation(String value) {
            return value;
        }
    }

    public static final class ClassTarget {
        public String operation(String value) {
            return value;
        }
    }
}
