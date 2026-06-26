/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.Structure.FieldOrder;
import com.sun.jna.platform.win32.OaIdl.SAFEARRAY;
import com.sun.jna.platform.win32.OaIdlUtil;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.WTypes.VARTYPE;

public class OaIdlUtilTest {
    @Test
    void toPrimitiveArrayConvertsMultiDimensionalIntegerSafeArray() {
        InMemorySafeArray safeArray = new InMemorySafeArray(Variant.VT_I4, new int[] {2, 3}, 1, 2, 3, 4, 5, 6);

        Object result = OaIdlUtil.toPrimitiveArray(safeArray, false);

        assertThat(result).isInstanceOf(Object[][].class);
        assertThat((Object[][]) result).isDeepEqualTo(new Object[][] {{1, 2, 3}, {4, 5, 6}});
        assertThat(safeArray.wasUnaccessed()).isTrue();
    }

    @FieldOrder({"cDims", "fFeatures", "cbElements", "cLocks", "pvData", "rgsabound"})
    static final class InMemorySafeArray extends SAFEARRAY {
        private final Memory data;
        private final int varType;
        private final int[] lengths;
        private boolean dataUnaccessed;

        InMemorySafeArray(int varType, int[] lengths, int... values) {
            if (elementCount(lengths) != values.length) {
                throw new IllegalArgumentException("Value count must match SAFEARRAY dimensions.");
            }
            this.varType = varType;
            this.lengths = lengths.clone();
            this.data = new Memory((long) values.length * Integer.BYTES);
            for (int i = 0; i < values.length; i++) {
                data.setInt((long) i * Integer.BYTES, values[i]);
            }
        }

        @Override
        public Pointer accessData() {
            return data;
        }

        @Override
        public void unaccessData() {
            dataUnaccessed = true;
        }

        @Override
        public int getDimensionCount() {
            return lengths.length;
        }

        @Override
        public int getLBound(int dimension) {
            return 0;
        }

        @Override
        public int getUBound(int dimension) {
            return lengths[dimension] - 1;
        }

        @Override
        public VARTYPE getVarType() {
            return new VARTYPE(varType);
        }

        boolean wasUnaccessed() {
            return dataUnaccessed;
        }

        private static int elementCount(int[] lengths) {
            int result = 1;
            for (int length : lengths) {
                result *= length;
            }
            return result;
        }
    }
}
