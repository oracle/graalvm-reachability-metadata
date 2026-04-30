/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.OaIdl.SAFEARRAY;
import com.sun.jna.platform.win32.OaIdlUtil;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.WTypes.VARTYPE;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class OaIdlUtilTest {

    @Test
    void toPrimitiveArrayCreatesMultiDimensionalObjectArrayFromSafeArrayData() {
        InMemorySafeArray safeArray = InMemorySafeArray.ofIntegers(
            new int[] {2, 3},
            11, 22, 33, 44, 55, 66
        );

        Object result = OaIdlUtil.toPrimitiveArray(safeArray, false);

        assertThat(result).isInstanceOf(Object[][].class);
        assertThat((Object[][]) result).isDeepEqualTo(new Object[][] {
            {11, 22, 33},
            {44, 55, 66}
        });
        safeArray.assertAccessCycleCompleted();
    }

    @Structure.FieldOrder({
        "cDims", "fFeatures", "cbElements", "cLocks", "pvData", "rgsabound"
    })
    private static final class InMemorySafeArray extends SAFEARRAY {

        private final Pointer dataPointer;
        private final int[] lowerBounds;
        private final int[] upperBounds;
        private final VARTYPE varType;
        private boolean accessed;
        private boolean unaccessed;

        private InMemorySafeArray(int varTypeValue, int[] dimensions, Pointer dataPointer) {
            this.dataPointer = dataPointer;
            this.lowerBounds = new int[dimensions.length];
            this.upperBounds = Arrays.stream(dimensions).map(dimension -> dimension - 1).toArray();
            this.varType = new VARTYPE(varTypeValue);
        }

        private static InMemorySafeArray ofIntegers(int[] dimensions, int... values) {
            int elementCount = Arrays.stream(dimensions).reduce(1, (left, right) -> left * right);
            if (elementCount != values.length) {
                throw new IllegalArgumentException("Dimension element count must match supplied values");
            }
            Memory memory = new Memory((long) Integer.BYTES * values.length);
            for (int index = 0; index < values.length; index++) {
                memory.setInt((long) index * Integer.BYTES, values[index]);
            }
            return new InMemorySafeArray(Variant.VT_I4, dimensions, memory);
        }

        @Override
        public Pointer accessData() {
            accessed = true;
            return dataPointer;
        }

        @Override
        public void unaccessData() {
            unaccessed = true;
        }

        @Override
        public int getDimensionCount() {
            return lowerBounds.length;
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
        public VARTYPE getVarType() {
            return varType;
        }

        private void assertAccessCycleCompleted() {
            assertThat(accessed).isTrue();
            assertThat(unaccessed).isTrue();
        }

    }
}
