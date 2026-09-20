/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.lang.reflect.Method;

import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;
import org.aspectj.weaver.reflect.ReflectionBasedReferenceTypeDelegate;
import org.aspectj.weaver.reflect.ReflectionBasedReferenceTypeDelegateFactory;
import org.aspectj.weaver.reflect.ReflectionWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionBasedReferenceTypeDelegateFactoryTest {
    @Test
    void createsJava15ReflectionDelegateByClassName() {
        ClassLoader classLoader = getClass().getClassLoader();
        ReflectionWorld world = new ReflectionWorld(classLoader);
        ReferenceType referenceType = referenceTypeFor(String.class, world);

        ReflectionBasedReferenceTypeDelegate delegate = ReflectionBasedReferenceTypeDelegateFactory.createDelegate(
                referenceType,
                world,
                classLoader
        );

        assertThat(delegate).isNotNull();
        assertThat(delegate.getClazz()).isEqualTo(String.class);
        assertThat(delegate.getResolvedTypeX()).isSameAs(referenceType);
    }

    @Test
    void createsJava14ReflectionDelegateByClassName() {
        ClassLoader classLoader = getClass().getClassLoader();
        ReflectionWorld world = new ReflectionWorld(true, classLoader);
        ReferenceType referenceType = referenceTypeFor(String.class, world);

        ReflectionBasedReferenceTypeDelegate delegate = ReflectionBasedReferenceTypeDelegateFactory.create14Delegate(
                referenceType,
                world,
                classLoader
        );

        assertThat(delegate).isNotNull();
        assertThat(delegate.getClazz()).isEqualTo(String.class);
        assertThat(delegate.getResolvedTypeX()).isSameAs(referenceType);
    }

    @Test
    void createsGenericSignatureProviderForResolvedMethodMembers() throws NoSuchMethodException {
        ReflectionWorld world = new ReflectionWorld(getClass().getClassLoader());
        Method method = String.class.getMethod("substring", int.class, int.class);

        ResolvedMember resolvedMember = ReflectionBasedReferenceTypeDelegateFactory.createResolvedMember(method, world);

        assertThat(resolvedMember.getName()).isEqualTo("substring");
        assertThat(resolvedMember.getArity()).isEqualTo(2);
        assertThat(resolvedMember.getReturnType().getName()).isEqualTo(String.class.getName());
    }

    private static ReferenceType referenceTypeFor(Class<?> type, World world) {
        UnresolvedType unresolvedType = UnresolvedType.forName(type.getName());
        return ReferenceType.fromTypeX(unresolvedType, world);
    }
}
