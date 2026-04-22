/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.OaIdl;
import com.sun.jna.platform.win32.OaIdlUtil;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.WTypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OaIdlUtilTest {
    @Test
    void toPrimitiveArrayCreatesNestedJavaArraysFromSafeArrayDimensions() {
        StubSafeArray safeArray = new StubSafeArray(
                new WTypes.VARTYPE(Variant.VT_I4),
                new int[]{4, 7},
                new int[]{5, 9},
                10, 20, 30, 40, 50, 60
        );

        Object converted = OaIdlUtil.toPrimitiveArray(safeArray, false);

        assertThat(converted).isInstanceOf(Object[][].class);
        Object[][] matrix = (Object[][]) converted;
        assertThat(matrix).hasSize(2);
        assertThat(matrix[0]).containsExactly(10, 20, 30);
        assertThat(matrix[1]).containsExactly(40, 50, 60);
        assertThat(safeArray.wasUnaccessed()).isTrue();
        assertThat(safeArray.wasDestroyed()).isFalse();
    }

    @Test
    void toPrimitiveArrayDestroysTheSafeArrayWhenRequested() {
        StubSafeArray safeArray = new StubSafeArray(
                new WTypes.VARTYPE(Variant.VT_I4),
                new int[]{0},
                new int[]{1},
                7, 8
        );

        Object[] converted = (Object[]) OaIdlUtil.toPrimitiveArray(safeArray, true);

        assertThat(converted).containsExactly(7, 8);
        assertThat(safeArray.wasUnaccessed()).isTrue();
        assertThat(safeArray.wasDestroyed()).isTrue();
    }

    private static final class StubSafeArray extends OaIdl.SAFEARRAY {
        private final WTypes.VARTYPE varType;
        private final int[] lowerBounds;
        private final int[] upperBounds;
        private final Pointer data;
        private boolean unaccessed;
        private boolean destroyed;

        private StubSafeArray(WTypes.VARTYPE varType, int[] lowerBounds, int[] upperBounds, int... values) {
            this.varType = varType;
            this.lowerBounds = lowerBounds.clone();
            this.upperBounds = upperBounds.clone();
            this.data = newIntMemory(values);
        }

        @Override
        public Pointer accessData() {
            return data;
        }

        @Override
        public int getDimensionCount() {
            return lowerBounds.length;
        }

        @Override
        public WTypes.VARTYPE getVarType() {
            return varType;
        }

        @Override
        public int getLBound(int dimension) {
            return lowerBounds[dimension];
        }

        @Override
        public int getUBound(int dimension) {
            return upperBounds[dimension];
        }

        @Override
        public void unaccessData() {
            unaccessed = true;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }

        private boolean wasUnaccessed() {
            return unaccessed;
        }

        private boolean wasDestroyed() {
            return destroyed;
        }
    }

    private static Pointer newIntMemory(int... values) {
        Memory memory = new Memory((long) values.length * Integer.BYTES);
        for (int index = 0; index < values.length; index++) {
            memory.setInt((long) index * Integer.BYTES, values[index]);
        }
        return memory;
    }
}
