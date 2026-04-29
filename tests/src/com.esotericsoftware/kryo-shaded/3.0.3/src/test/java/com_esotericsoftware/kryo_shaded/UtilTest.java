/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.util.Util;
import org.junit.jupiter.api.Test;

public class UtilTest {
    @Test
    void formatsObjectsThroughPublicStringUtility() {
        assertThat(Util.string("kryo-value")).isEqualTo("kryo-value");
        assertThat(Util.string(new Object())).isEqualTo("Object");
        assertThat(Util.string(null)).isEqualTo("null");
    }

    @Test
    void formatsArrayClassNamesFromObjectsAndClasses() {
        assertThat(Util.string(new int[2][3])).isEqualTo("int[][]");
        assertThat(Util.className(String[][].class)).isEqualTo("String[][]");
        assertThat(Util.getDimensionCount(byte[][][].class)).isEqualTo(3);
        assertThat(Util.getElementClass(byte[][][].class)).isSameAs(byte.class);
    }

    @Test
    void mapsPrimitiveAndWrapperTypes() {
        assertThat(Util.getWrapperClass(int.class)).isSameAs(Integer.class);
        assertThat(Util.getWrapperClass(void.class)).isSameAs(Void.class);
        assertThat(Util.getPrimitiveClass(Integer.class)).isSameAs(int.class);
        assertThat(Util.getPrimitiveClass(Void.class)).isSameAs(void.class);
        assertThat(Util.getPrimitiveClass(String.class)).isSameAs(String.class);
        assertThat(Util.isWrapperClass(Double.class)).isTrue();
        assertThat(Util.isWrapperClass(String.class)).isFalse();
    }

    @Test
    void swapsIntegerAndLongByteOrder() {
        assertThat(Util.swapInt(0x01020304)).isEqualTo(0x04030201);
        assertThat(Util.swapLong(0x0102030405060708L)).isEqualTo(0x0807060504030201L);
    }
}
