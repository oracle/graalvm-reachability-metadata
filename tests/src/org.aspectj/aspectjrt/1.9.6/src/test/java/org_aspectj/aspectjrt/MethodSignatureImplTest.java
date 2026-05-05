/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodSignatureImplTest {
    @Test
    void resolvesMethodDeclaredOnSignatureDeclaringType() {
        Factory factory = new Factory("MethodSignatureImplTest.java", MethodSignatureImplTest.class);
        MethodSignature signature = factory.makeMethodSig(
                Modifier.PUBLIC,
                "declaredGreeting",
                DeclaredMethodSubject.class,
                new Class[] {String.class},
                new String[] {"name"},
                new Class[0],
                String.class);

        Method method = signature.getMethod();

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(DeclaredMethodSubject.class);
        assertThat(method.getName()).isEqualTo("declaredGreeting");
        assertThat(method.getParameterTypes()).containsExactly(String.class);
        assertThat(method.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void searchesHierarchyWhenMethodIsInheritedBySignatureDeclaringType() {
        Factory factory = new Factory("MethodSignatureImplTest.java", MethodSignatureImplTest.class);
        MethodSignature signature = factory.makeMethodSig(
                Modifier.PUBLIC,
                "inheritedGreeting",
                InheritingMethodSubject.class,
                new Class[] {String.class},
                new String[] {"name"},
                new Class[0],
                String.class);

        Method method = signature.getMethod();

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(BaseMethodSubject.class);
        assertThat(method.getName()).isEqualTo("inheritedGreeting");
        assertThat(method.getParameterTypes()).containsExactly(String.class);
        assertThat(method.getReturnType()).isEqualTo(String.class);
    }

    public static class DeclaredMethodSubject {
        public String declaredGreeting(String name) {
            return "Hello " + name;
        }
    }

    public static class BaseMethodSubject {
        public String inheritedGreeting(String name) {
            return "Hello " + name;
        }
    }

    public static class InheritingMethodSubject extends BaseMethodSubject {
    }
}
