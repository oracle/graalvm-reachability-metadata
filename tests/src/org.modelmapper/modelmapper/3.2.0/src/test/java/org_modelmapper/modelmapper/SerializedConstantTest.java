/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.implementation.bytecode.StackManipulation;
import org.modelmapper.internal.bytebuddy.implementation.bytecode.constant.NullConstant;
import org.modelmapper.internal.bytebuddy.implementation.bytecode.constant.SerializedConstant;

public class SerializedConstantTest {
    @Test
    void serializesNonNullConstantAsValidStackManipulation() {
        StackManipulation serializedConstant = SerializedConstant.of("ModelMapper serialized constant");
        StackManipulation sameSerializedConstant = SerializedConstant.of("ModelMapper serialized constant");

        assertThat(serializedConstant).isNotSameAs(NullConstant.INSTANCE);
        assertThat(serializedConstant.isValid()).isTrue();
        assertThat(serializedConstant)
            .isEqualTo(sameSerializedConstant)
            .hasSameHashCodeAs(sameSerializedConstant);
    }
}
