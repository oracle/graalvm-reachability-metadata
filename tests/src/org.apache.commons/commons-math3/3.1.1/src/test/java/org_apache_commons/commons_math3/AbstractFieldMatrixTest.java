/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_math3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.math3.fraction.Fraction;
import org.apache.commons.math3.fraction.FractionField;
import org.apache.commons.math3.linear.Array2DRowFieldMatrix;
import org.apache.commons.math3.linear.BlockFieldMatrix;
import org.junit.jupiter.api.Test;

public class AbstractFieldMatrixTest {
    @Test
    void array2DRowFieldMatrixAllocatesCompleteTypedArrayFilledWithZero() {
        Array2DRowFieldMatrix<Fraction> matrix = new Array2DRowFieldMatrix<>(FractionField.getInstance(), 2, 3);

        assertThat(matrix.getRowDimension()).isEqualTo(2);
        assertThat(matrix.getColumnDimension()).isEqualTo(3);
        for (int row = 0; row < matrix.getRowDimension(); row++) {
            for (int column = 0; column < matrix.getColumnDimension(); column++) {
                assertThat(matrix.getEntry(row, column)).isEqualTo(Fraction.ZERO);
            }
        }
    }

    @Test
    void blockFieldMatrixLayoutAllocatesPartialOuterArrayAndTypedBlocks() {
        Fraction[][] blocks = BlockFieldMatrix.createBlocksLayout(FractionField.getInstance(), 37, 38);

        assertThat(blocks.length).isEqualTo(4);
        assertThat(blocks[0]).hasSize(36 * 36);
        assertThat(blocks[1]).hasSize(36 * 2);
        assertThat(blocks[2]).hasSize(1 * 36);
        assertThat(blocks[3]).hasSize(1 * 2);
        for (Fraction[] block : blocks) {
            assertThat(block).containsOnly(Fraction.ZERO);
        }
    }
}
