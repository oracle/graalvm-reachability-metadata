/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.weaver.Member;
import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.ResolvedPointcutDefinition;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.reflect.Java15ReflectionBasedReferenceTypeDelegate;
import org.aspectj.weaver.reflect.ReflectionBasedReferenceTypeDelegate;
import org.aspectj.weaver.reflect.ReflectionBasedReferenceTypeDelegateFactory;
import org.aspectj.weaver.reflect.ReflectionWorld;
import org.junit.jupiter.api.Test;

public class Java15ReflectionBasedReferenceTypeDelegateTest {
    @Test
    void discoversPointcutParameterNamesFromDeclaredMethods() {
        Java15ReflectionBasedReferenceTypeDelegate delegate = delegateFor(ParameterNameAspect.class);

        ResolvedMember[] pointcuts = delegate.getDeclaredPointcuts();

        assertThat(pointcuts)
                .singleElement()
                .satisfies(pointcut -> {
                    assertThat(pointcut.getKind()).isEqualTo(Member.POINTCUT);
                    assertThat(pointcut.getName()).isEqualTo("matchingArgument");
                    assertThat(pointcut.getParameterNames()).containsExactly("candidate");
                    assertThat(pointcut.getParameterTypes())
                            .singleElement()
                            .satisfies(parameterType -> assertThat(parameterType.getName())
                                    .isEqualTo(String.class.getName()));
                    assertThat(pointcut).isInstanceOf(ResolvedPointcutDefinition.class);
                    assertThat(((ResolvedPointcutDefinition) pointcut).getPointcut()).isNotNull();
                });
    }

    private static Java15ReflectionBasedReferenceTypeDelegate delegateFor(Class<?> targetClass) {
        ReflectionWorld world = new ReflectionWorld(targetClass.getClassLoader());
        ReferenceType referenceType = referenceTypeFor(targetClass, world);
        ReflectionBasedReferenceTypeDelegate delegate = ReflectionBasedReferenceTypeDelegateFactory.createDelegate(
                referenceType, world, targetClass);
        assertThat(delegate).isInstanceOf(Java15ReflectionBasedReferenceTypeDelegate.class);
        return (Java15ReflectionBasedReferenceTypeDelegate) delegate;
    }

    private static ReferenceType referenceTypeFor(Class<?> targetClass, ReflectionWorld world) {
        ResolvedType resolvedType = world.resolve(targetClass);
        assertThat(resolvedType).isInstanceOf(ReferenceType.class);
        return (ReferenceType) resolvedType;
    }

    @Aspect
    public static class ParameterNameAspect {
        @Pointcut("args(candidate)")
        public void matchingArgument(String candidate) {
        }
    }
}
