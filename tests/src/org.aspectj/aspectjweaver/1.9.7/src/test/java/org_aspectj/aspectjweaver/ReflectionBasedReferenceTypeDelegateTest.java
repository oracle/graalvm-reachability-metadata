/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.weaver.Member;
import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.reflect.ReflectionBasedReferenceTypeDelegate;
import org.aspectj.weaver.reflect.ReflectionWorld;
import org.junit.jupiter.api.Test;

public class ReflectionBasedReferenceTypeDelegateTest {
    @Test
    void resolvesDeclaredFieldsFromClassMetadata() {
        ReflectionBasedReferenceTypeDelegate delegate = delegateFor(DelegateFixture.class);

        ResolvedMember[] fields = delegate.getDeclaredFields();

        assertThat(fields)
                .extracting(ResolvedMember::getName)
                .contains("counter", "label");
        assertThat(fields)
                .anySatisfy(field -> {
                    assertThat(field.getName()).isEqualTo("counter");
                    assertThat(field.getKind()).isEqualTo(Member.FIELD);
                    assertThat(field.getDeclaringType().getName()).isEqualTo(DelegateFixture.class.getName());
                });
    }

    @Test
    void resolvesDeclaredMethodsAndConstructorsFromClassMetadata() {
        ReflectionBasedReferenceTypeDelegate delegate = delegateFor(DelegateFixture.class);

        ResolvedMember[] methods = delegate.getDeclaredMethods();

        assertThat(methods)
                .extracting(ResolvedMember::getName)
                .contains("describe", "update");
        assertThat(methods)
                .anySatisfy(method -> {
                    assertThat(method.getName()).isEqualTo("describe");
                    assertThat(method.getKind()).isEqualTo(Member.METHOD);
                    assertThat(method.getReturnType().getName()).isEqualTo(String.class.getName());
                })
                .anySatisfy(constructor -> {
                    assertThat(constructor.getKind()).isEqualTo(Member.CONSTRUCTOR);
                    assertThat(constructor.getDeclaringType().getName()).isEqualTo(DelegateFixture.class.getName());
                });
    }

    private static ReflectionBasedReferenceTypeDelegate delegateFor(Class<?> targetClass) {
        ReflectionWorld world = new ReflectionWorld(ReflectionBasedReferenceTypeDelegateTest.class.getClassLoader());
        ResolvedType resolvedType = world.resolve(targetClass);
        assertThat(resolvedType).isInstanceOf(ReferenceType.class);
        return new ReflectionBasedReferenceTypeDelegate(
                targetClass,
                targetClass.getClassLoader(),
                world,
                (ReferenceType) resolvedType);
    }

    private static final class DelegateFixture {
        private final int counter;
        private String label;

        private DelegateFixture() {
            this(0, "initial");
        }

        private DelegateFixture(int counter, String label) {
            this.counter = counter;
            this.label = label;
        }

        private String describe() {
            return label + ':' + counter;
        }

        private void update(String newLabel) {
            this.label = newLabel;
        }
    }
}
