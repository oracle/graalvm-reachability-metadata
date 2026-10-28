/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.util.ArrayList;
import java.util.List;

import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.constant.SerializedConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SerializedConstantTest {
    @Test
    void capturesSerializableValueAsConstant() {
        ArrayList<String> value = new ArrayList<>(List.of("alpha", "beta"));
        StackManipulation original = SerializedConstant.of(value);
        assertTrue(original.isValid());
        assertEquals(original, SerializedConstant.of(new ArrayList<>(value)));
        value.add("gamma");
        assertNotEquals(original, SerializedConstant.of(value));
    }
}
