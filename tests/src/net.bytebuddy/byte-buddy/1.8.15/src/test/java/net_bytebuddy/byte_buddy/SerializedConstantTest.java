/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.constant.SerializedConstant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializedConstantTest {
    @Test
    void serializesNonNullValueAsValidStackManipulation() {
        StackManipulation stackManipulation = SerializedConstant.of("byte-buddy serialized constant");

        assertThat(stackManipulation).isInstanceOf(SerializedConstant.class);
        assertThat(stackManipulation.isValid()).isTrue();
    }
}
