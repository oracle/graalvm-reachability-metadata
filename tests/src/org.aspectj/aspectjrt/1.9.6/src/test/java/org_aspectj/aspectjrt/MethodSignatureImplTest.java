/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

public class MethodSignatureImplTest {
    @Test
    void resolvesDeclaredMethodOnDeclaringType() {
        Factory factory = new Factory("MethodSignatureImplTest.java", MethodSignatureImplTest.class);
        MethodSignature signature = factory.makeMethodSig(
                Modifier.PUBLIC,
                "echo",
                DeclaringService.class,
                new Class[] {String.class},
                new String[] {"value"},
                new Class[] {IllegalStateException.class},
                String.class);

        Method method = signature.getMethod();

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(DeclaringService.class);
        assertThat(method.getName()).isEqualTo("echo");
        assertThat(method.getParameterTypes()).containsExactly(String.class);
        assertThat(method.getReturnType()).isEqualTo(String.class);
        assertThat(method.getExceptionTypes()).containsExactly(IllegalStateException.class);
    }

    @Test
    void searchesSuperclassWhenDeclaringTypeDoesNotDefineMethod() {
        Factory factory = new Factory("MethodSignatureImplTest.java", MethodSignatureImplTest.class);
        MethodSignature signature = factory.makeMethodSig(
                Modifier.PUBLIC,
                "inheritedEcho",
                ChildService.class,
                new Class[] {String.class},
                new String[] {"value"},
                new Class[0],
                String.class);

        Method method = signature.getMethod();

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(ParentService.class);
        assertThat(method.getName()).isEqualTo("inheritedEcho");
        assertThat(method.getParameterTypes()).containsExactly(String.class);
        assertThat(method.getReturnType()).isEqualTo(String.class);
    }

    public static class DeclaringService {
        public String echo(String value) throws IllegalStateException {
            return value;
        }
    }

    public static class ParentService {
        public String inheritedEcho(String value) {
            return value;
        }
    }

    public static class ChildService extends ParentService {
    }
}
