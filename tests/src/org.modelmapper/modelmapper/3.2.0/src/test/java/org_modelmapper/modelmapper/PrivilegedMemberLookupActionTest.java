/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.modelmapper.internal.bytebuddy.matcher.ElementMatchers.isConstructor;
import static org.modelmapper.internal.bytebuddy.matcher.ElementMatchers.isMethod;
import static org.modelmapper.internal.bytebuddy.matcher.ElementMatchers.named;

import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.modelmapper.internal.bytebuddy.description.method.MethodDescription;
import org.modelmapper.internal.bytebuddy.description.type.TypeDescription;
import org.modelmapper.internal.bytebuddy.implementation.auxiliary.PrivilegedMemberLookupAction;
import org.modelmapper.internal.util.Assert;

public class PrivilegedMemberLookupActionTest {
    @Test
    void selectsPrivilegedLookupActionsForLoadedMethodsAndConstructors() {
        assertThat(PrivilegedMemberLookupAction.of(lookupMethod(ModelMapper.class, "getConfiguration")))
            .isSameAs(PrivilegedMemberLookupAction.FOR_PUBLIC_METHOD);
        assertThat(PrivilegedMemberLookupAction.of(lookupMethod(ModelMapper.class, "mapInternal")))
            .isSameAs(PrivilegedMemberLookupAction.FOR_DECLARED_METHOD);
        assertThat(PrivilegedMemberLookupAction.of(lookupConstructor(ModelMapper.class)))
            .isSameAs(PrivilegedMemberLookupAction.FOR_PUBLIC_CONSTRUCTOR);
        assertThat(PrivilegedMemberLookupAction.of(lookupConstructor(Assert.class)))
            .isSameAs(PrivilegedMemberLookupAction.FOR_DECLARED_CONSTRUCTOR);
    }

    private static MethodDescription.InDefinedShape lookupMethod(Class<?> type, String name) {
        return TypeDescription.ForLoadedType.of(type)
            .getDeclaredMethods()
            .filter(isMethod().and(named(name)))
            .getOnly();
    }

    private static MethodDescription.InDefinedShape lookupConstructor(Class<?> type) {
        return TypeDescription.ForLoadedType.of(type)
            .getDeclaredMethods()
            .filter(isConstructor())
            .getOnly();
    }
}
