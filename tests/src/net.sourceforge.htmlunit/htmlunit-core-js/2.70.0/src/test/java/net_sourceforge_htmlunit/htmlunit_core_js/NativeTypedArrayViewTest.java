/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import net.sourceforge.htmlunit.corejs.javascript.typedarrays.NativeInt8Array;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeTypedArrayViewTest {
    @Test
    void toArrayAllocatesTypedResultArrayWhenProvidedArrayIsTooSmall() {
        NativeInt8Array typedArray = new NativeInt8Array(3);
        typedArray.setArrayElement(0, Byte.valueOf((byte) 7));
        typedArray.setArrayElement(1, Byte.valueOf((byte) -2));
        typedArray.setArrayElement(2, Byte.valueOf((byte) 42));

        Byte[] values = typedArray.toArray(new Byte[0]);

        assertThat(values).containsExactly((byte) 7, (byte) -2, (byte) 42);
    }
}
