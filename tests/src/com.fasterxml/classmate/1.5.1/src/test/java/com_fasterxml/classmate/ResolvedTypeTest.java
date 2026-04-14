/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml.classmate;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.fasterxml.classmate.members.RawConstructor;
import com.fasterxml.classmate.members.RawField;
import com.fasterxml.classmate.members.RawMethod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedTypeTest {

    private static final TypeResolver TYPE_RESOLVER = new TypeResolver();

    @Test
    void resolvesDeclaredFieldsAndMethods() {
        ResolvedType resolvedType = TYPE_RESOLVER.resolve(SampleType.class);

        assertThat(resolvedType.getMemberFields())
                .extracting(RawField::getName)
                .containsExactly("memberField");
        assertThat(resolvedType.getStaticFields())
                .extracting(RawField::getName)
                .containsExactly("STATIC_FIELD");
        assertThat(resolvedType.getMemberMethods())
                .extracting(RawMethod::getName)
                .containsExactlyInAnyOrder("memberMethod", "privateMemberMethod");
        assertThat(resolvedType.getStaticMethods())
                .extracting(RawMethod::getName)
                .containsExactly("staticMethod");
    }

    @Test
    void resolvesDeclaredConstructors() {
        ResolvedType resolvedType = TYPE_RESOLVER.resolve(SampleType.class);

        assertThat(resolvedType.getConstructors())
                .extracting(rawConstructor -> rawConstructor.getRawMember().getParameterCount())
                .containsExactlyInAnyOrder(0, 1);
        assertThat(resolvedType.getConstructors())
                .extracting(RawConstructor::getName)
                .allMatch(name -> name.endsWith("ResolvedTypeTest$SampleType"));
    }

    private static final class SampleType {

        static final String STATIC_FIELD = "static";

        private String memberField;

        SampleType() {
        }

        private SampleType(String memberField) {
            this.memberField = memberField;
        }

        void memberMethod() {
        }

        private void privateMemberMethod() {
        }

        static void staticMethod() {
        }
    }
}
