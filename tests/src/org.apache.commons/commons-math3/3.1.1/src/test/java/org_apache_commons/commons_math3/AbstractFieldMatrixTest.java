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
import org.apache.commons.math3.linear.FieldMatrix;
import org.apache.commons.math3.linear.SparseFieldMatrix;
import org.junit.jupiter.api.Test;

public class AbstractFieldMatrixTest {
    @Test
    void createsZeroFilledDenseFieldMatrixStorage() {
        FieldMatrix<Fraction> matrix = new Array2DRowFieldMatrix<Fraction>(FractionField.getInstance(), 2, 3);

        assertThat(matrix.getRowDimension()).isEqualTo(2);
        assertThat(matrix.getColumnDimension()).isEqualTo(3);
        for (int row = 0; row < matrix.getRowDimension(); row++) {
            for (int column = 0; column < matrix.getColumnDimension(); column++) {
                assertThat(matrix.getEntry(row, column)).isEqualTo(Fraction.ZERO);
            }
        }

        Fraction value = new Fraction(7, 3);
        matrix.setEntry(1, 2, value);

        assertThat(matrix.getEntry(1, 2)).isEqualTo(value);
    }

    @Test
    void copiesBlockLayoutWithoutReferencingInputBlocks() {
        Fraction[][] rawData = {
            {new Fraction(1, 2), new Fraction(2, 3)},
            {new Fraction(3, 4), new Fraction(4, 5)}
        };
        Fraction[][] blockData = BlockFieldMatrix.toBlocksLayout(rawData);

        FieldMatrix<Fraction> matrix = new BlockFieldMatrix<Fraction>(2, 2, blockData, true);
        blockData[0][0] = Fraction.ZERO;

        assertThat(matrix.getEntry(0, 0)).isEqualTo(new Fraction(1, 2));
        assertThat(matrix.getEntry(0, 1)).isEqualTo(new Fraction(2, 3));
        assertThat(matrix.getEntry(1, 0)).isEqualTo(new Fraction(3, 4));
        assertThat(matrix.getEntry(1, 1)).isEqualTo(new Fraction(4, 5));
    }

    @Test
    @SuppressWarnings("deprecation")
    void extractsRowsFromSparseFieldMatrix() {
        SparseFieldMatrix<Fraction> matrix = new SparseFieldMatrix<Fraction>(FractionField.getInstance(), 2, 3);
        Fraction first = new Fraction(5, 6);
        Fraction last = new Fraction(-7, 8);
        matrix.setEntry(1, 0, first);
        matrix.setEntry(1, 2, last);

        Fraction[] row = matrix.getRow(1);

        assertThat(row).containsExactly(first, Fraction.ZERO, last);
    }
}
