/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.auxiliary.AuxiliaryType;
import net.bytebuddy.implementation.auxiliary.PrivilegedMemberLookupAction;
import org.junit.jupiter.api.Test;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;

public class PrivilegedMemberLookupActionTest {
    @Test
    void selectsPrivilegedLookupActionForPublicAndDeclaredMethods() {
        TypeDescription sampleType = TypeDescription.ForLoadedType.of(Sample.class);

        AuxiliaryType publicMethodLookup = PrivilegedMemberLookupAction.of(method(sampleType, "publicMethod"));
        AuxiliaryType declaredMethodLookup = PrivilegedMemberLookupAction.of(method(sampleType, "privateMethod"));

        assertThat(publicMethodLookup).isSameAs(PrivilegedMemberLookupAction.FOR_PUBLIC_METHOD);
        assertThat(declaredMethodLookup).isSameAs(PrivilegedMemberLookupAction.FOR_DECLARED_METHOD);
    }

    @Test
    void selectsPrivilegedLookupActionForPublicAndDeclaredConstructors() {
        TypeDescription publicType = TypeDescription.ForLoadedType.of(PublicSample.class);
        TypeDescription privateType = TypeDescription.ForLoadedType.of(PrivateSample.class);

        AuxiliaryType publicConstructorLookup = PrivilegedMemberLookupAction.of(constructor(publicType));
        AuxiliaryType declaredConstructorLookup = PrivilegedMemberLookupAction.of(constructor(privateType));

        assertThat(publicConstructorLookup).isSameAs(PrivilegedMemberLookupAction.FOR_PUBLIC_CONSTRUCTOR);
        assertThat(declaredConstructorLookup).isSameAs(PrivilegedMemberLookupAction.FOR_DECLARED_CONSTRUCTOR);
    }

    private static MethodDescription method(TypeDescription typeDescription, String name) {
        return typeDescription.getDeclaredMethods()
                .filter(named(name))
                .getOnly();
    }

    private static MethodDescription constructor(TypeDescription typeDescription) {
        return typeDescription.getDeclaredMethods()
                .filter(isConstructor())
                .getOnly();
    }

    public static class PublicSample {
    }

    private static class PrivateSample {
    }

    private static class Sample {
        public void publicMethod() {
        }

        private void privateMethod() {
        }
    }
}
