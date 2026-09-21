/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;
import org.aspectj.weaver.reflect.ReflectionBasedReferenceTypeDelegate;
import org.aspectj.weaver.reflect.ReflectionBasedReferenceTypeDelegateFactory;
import org.aspectj.weaver.reflect.ReflectionWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Java15ReflectionBasedReferenceTypeDelegateTest {
    @Test
    void discoversPointcutParameterNamesFromDeclaredMethodsWhenAnnotationArgNamesAreMissing() {
        ClassLoader classLoader = getClass().getClassLoader();
        ReflectionWorld world = new ReflectionWorld(classLoader);
        Class<?> aspectType = Java15ReflectionBasedReferenceTypeDelegatePointcutAspect.class;
        ReferenceType referenceType = referenceTypeFor(aspectType, world);
        ReflectionBasedReferenceTypeDelegate delegate = ReflectionBasedReferenceTypeDelegateFactory.createDelegate(
                referenceType,
                world,
                classLoader
        );

        ResolvedMember[] pointcuts = delegate.getDeclaredPointcuts();

        assertThat(pointcuts)
                .filteredOn(pointcut -> pointcut.getName().equals("capturesStringArgument"))
                .singleElement()
                .satisfies(pointcut -> {
                    assertThat(pointcut.getParameterTypes())
                            .extracting(UnresolvedType::getName)
                            .containsExactly("java.lang.String");
                    assertThat(pointcut.getParameterNames()).containsExactly("value");
                });
    }

    private static ReferenceType referenceTypeFor(Class<?> type, World world) {
        UnresolvedType unresolvedType = UnresolvedType.forName(type.getName());
        return ReferenceType.fromTypeX(unresolvedType, world);
    }
}

@Aspect
class Java15ReflectionBasedReferenceTypeDelegatePointcutAspect {
    @Pointcut("args(value)")
    public void capturesStringArgument(String value) {
    }
}
