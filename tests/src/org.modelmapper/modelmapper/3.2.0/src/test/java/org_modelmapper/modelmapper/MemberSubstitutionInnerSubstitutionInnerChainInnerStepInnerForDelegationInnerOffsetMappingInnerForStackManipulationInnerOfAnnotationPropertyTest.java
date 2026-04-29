/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.asm.MemberSubstitution.Substitution.Chain.Step.ForDelegation.OffsetMapping.Factory;
import org.modelmapper.internal.bytebuddy.asm.MemberSubstitution.Substitution.Chain.Step.ForDelegation.OffsetMapping.ForStackManipulation.OfAnnotationProperty;

public class MemberSubstitutionInnerSubstitutionInnerChainInnerStepInnerForDelegationInnerOffsetMappingInnerForStackManipulationInnerOfAnnotationPropertyTest {
    @Test
    void createsOffsetMappingFactoryForAnnotationProperty() {
        Factory<CustomBinding> factory = OfAnnotationProperty.of(CustomBinding.class, "value");

        assertThat(factory.getAnnotationType()).isEqualTo(CustomBinding.class);
    }

    private @interface CustomBinding {
        String value();
    }
}
