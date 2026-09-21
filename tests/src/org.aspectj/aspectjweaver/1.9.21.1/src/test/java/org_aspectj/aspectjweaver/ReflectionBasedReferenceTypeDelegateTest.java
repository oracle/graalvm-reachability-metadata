/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import org.aspectj.weaver.Member;
import org.aspectj.weaver.ReferenceType;
import org.aspectj.weaver.ResolvedMember;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.World;
import org.aspectj.weaver.reflect.ReflectionBasedReferenceTypeDelegate;
import org.aspectj.weaver.reflect.ReflectionWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionBasedReferenceTypeDelegateTest {
    @Test
    void exposesDeclaredFieldsMethodsAndConstructorsFromReflectionBackedType() {
        Class<?> reflectedType = ReflectionBasedReferenceTypeDelegate.class;
        ClassLoader classLoader = reflectedType.getClassLoader();
        ReflectionWorld world = new ReflectionWorld(true, classLoader);
        ReferenceType referenceType = referenceTypeFor(reflectedType, world);
        ReflectionBasedReferenceTypeDelegate delegate = new ReflectionBasedReferenceTypeDelegate(
                reflectedType,
                classLoader,
                world,
                referenceType
        );

        ResolvedMember[] fields = delegate.getDeclaredFields();
        ResolvedMember[] methods = delegate.getDeclaredMethods();

        assertThat(fields)
                .filteredOn(member -> member.getKind() == Member.FIELD)
                .extracting(Member::getName)
                .contains("myClass", "world", "resolvedType");
        assertThat(methods)
                .filteredOn(member -> member.getKind() == Member.METHOD)
                .extracting(Member::getName)
                .contains("getDeclaredFields", "getDeclaredMethods", "initialize");
        assertThat(methods)
                .filteredOn(member -> member.getKind() == Member.CONSTRUCTOR)
                .extracting(Member::getName)
                .contains("<init>");
    }

    private static ReferenceType referenceTypeFor(Class<?> type, World world) {
        UnresolvedType unresolvedType = UnresolvedType.forName(type.getName());
        return ReferenceType.fromTypeX(unresolvedType, world);
    }
}
