/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.reflect.ReflectionBasedReferenceTypeDelegate;
import org.aspectj.weaver.reflect.ReflectionBasedReferenceTypeDelegateFactory;
import org.aspectj.weaver.reflect.ReflectionWorld;
import org.junit.jupiter.api.Test;

public class ReflectionBasedReferenceTypeDelegateFactoryTest {
    @Test
    void createsReflectionDelegatesFromReferenceTypeNames() {
        ReflectionWorld world = reflectionWorld();
        Class<?> delegateTarget = ReflectionBasedReferenceTypeDelegateFactory.class;
        ReferenceType referenceType = referenceTypeFor(delegateTarget, world);

        ReflectionBasedReferenceTypeDelegate java15Delegate =
                ReflectionBasedReferenceTypeDelegateFactory.createDelegate(
                        referenceType, world, delegateTarget.getClassLoader());
        ReflectionBasedReferenceTypeDelegate java14Delegate =
                ReflectionBasedReferenceTypeDelegateFactory.create14Delegate(
                        referenceType, world, delegateTarget.getClassLoader());

        assertThat(java15Delegate).isNotNull();
        assertThat(java15Delegate.getClazz()).isEqualTo(delegateTarget);
        assertThat(java14Delegate).isNotNull();
        assertThat(java14Delegate.getClazz()).isEqualTo(delegateTarget);
    }

    @Test
    void createsResolvedMembersWithGenericSignatureInformation() throws Exception {
        ReflectionWorld world = reflectionWorld();
        Method method = Object.class.getMethod("toString");

        ResolvedMember resolvedMember = ReflectionBasedReferenceTypeDelegateFactory.createResolvedMember(method, world);

        assertThat(resolvedMember.getName()).isEqualTo("toString");
        assertThat(resolvedMember.getDeclaringType().getName()).isEqualTo(Object.class.getName());
        assertThat(resolvedMember.getReturnType().getName()).isEqualTo(String.class.getName());
    }

    private static ReflectionWorld reflectionWorld() {
        return new ReflectionWorld(ReflectionBasedReferenceTypeDelegateFactoryTest.class.getClassLoader());
    }

    private static ReferenceType referenceTypeFor(Class<?> type, ReflectionWorld world) {
        ResolvedType resolvedType = world.resolve(type);
        assertThat(resolvedType).isInstanceOf(ReferenceType.class);
        return (ReferenceType) resolvedType;
    }
}
