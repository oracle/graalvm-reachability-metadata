/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml.classmate;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.fasterxml.classmate.members.RawField;
import com.fasterxml.classmate.members.RawMethod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedTypeTest {
    @Test
    void exposesDeclaredMembersThroughResolvedType() {
        ResolvedType resolvedType = new TypeResolver().resolve(MemberFixture.class);

        assertThat(resolvedType.getMemberFields())
                .extracting(RawField::getName)
                .containsExactly("instanceField");
        assertThat(resolvedType.getStaticFields())
                .extracting(RawField::getName)
                .containsExactly("STATIC_FIELD");
        assertThat(resolvedType.getMemberMethods())
                .extracting(RawMethod::getName)
                .containsExactly("instanceMethod");
        assertThat(resolvedType.getStaticMethods())
                .extracting(RawMethod::getName)
                .containsExactly("staticMethod");
        assertThat(resolvedType.getConstructors())
                .extracting(rawConstructor -> rawConstructor.getRawMember().getParameterCount())
                .containsExactlyInAnyOrder(0, 1);
    }

    private static final class MemberFixture {
        static final String STATIC_FIELD = "static";

        private final int instanceField;

        private MemberFixture() {
            this(0);
        }

        MemberFixture(int instanceField) {
            this.instanceField = instanceField;
        }

        private int instanceMethod() {
            return instanceField;
        }

        static String staticMethod() {
            return STATIC_FIELD;
        }
    }
}
