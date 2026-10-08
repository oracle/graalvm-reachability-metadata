/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodSignatureImplTest {
    @Test
    void resolvesMethodDeclaredBySignatureType() {
        Factory factory = new Factory("MethodSignatureImplTest.java", MethodSignatureImplTest.class);
        MethodSignature signature = factory.makeMethodSig(
                Modifier.PUBLIC,
                "declaredOperation",
                DeclaredOperationFixture.class,
                new Class[] {String.class, int.class},
                new String[] {"value", "count"},
                new Class[0],
                String.class
        );

        Method method = signature.getMethod();

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(DeclaredOperationFixture.class);
        assertThat(method.getName()).isEqualTo("declaredOperation");
        assertThat(method.getParameterTypes()).containsExactly(String.class, int.class);
        assertThat(method.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void searchesHierarchyWhenSignatureTypeInheritsMethod() {
        Factory factory = new Factory("MethodSignatureImplTest.java", MethodSignatureImplTest.class);
        MethodSignature signature = factory.makeMethodSig(
                Modifier.PUBLIC,
                "inheritedOperation",
                InheritingOperationFixture.class,
                new Class[] {long.class},
                new String[] {"value"},
                new Class[0],
                long.class
        );

        Method method = signature.getMethod();

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(BaseOperationFixture.class);
        assertThat(method.getName()).isEqualTo("inheritedOperation");
        assertThat(method.getParameterTypes()).containsExactly(long.class);
        assertThat(method.getReturnType()).isEqualTo(long.class);
    }

    public static final class DeclaredOperationFixture {
        public String declaredOperation(String value, int count) {
            return value.repeat(count);
        }
    }

    public static class BaseOperationFixture {
        public long inheritedOperation(long value) {
            return value + 1;
        }
    }

    public static final class InheritingOperationFixture extends BaseOperationFixture {
    }
}
