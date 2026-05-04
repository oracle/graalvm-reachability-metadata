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
import org.apache.commons.math3.linear.ArrayFieldVector;
import org.apache.commons.math3.linear.FieldDecompositionSolver;
import org.apache.commons.math3.linear.FieldLUDecomposition;
import org.apache.commons.math3.linear.FieldMatrix;
import org.apache.commons.math3.linear.FieldVector;
import org.apache.commons.math3.linear.SparseFieldVector;
import org.junit.jupiter.api.Test;

public class FieldLUDecompositionInnerSolverTest {
    @Test
    void solveSparseFieldVectorUsesGenericFieldVectorPath() {
        FieldDecompositionSolver<Fraction> solver = solverForInvertibleTwoByTwoMatrix();
        SparseFieldVector<Fraction> rightHandSide = new SparseFieldVector<>(FractionField.getInstance(), 2);
        rightHandSide.setEntry(0, fraction(5));
        rightHandSide.setEntry(1, fraction(3));

        FieldVector<Fraction> solution = solver.solve(rightHandSide);

        assertThat(solution.getDimension()).isEqualTo(2);
        assertThat(solution.getEntry(0)).isEqualTo(fraction(2));
        assertThat(solution.getEntry(1)).isEqualTo(fraction(1));
    }

    @Test
    void solveArrayFieldVectorUsesOptimizedArrayFieldVectorPath() {
        FieldDecompositionSolver<Fraction> solver = solverForInvertibleTwoByTwoMatrix();
        ArrayFieldVector<Fraction> rightHandSide = new ArrayFieldVector<>(FractionField.getInstance(), new Fraction[] {
                fraction(5), fraction(3)
        }, false);

        FieldVector<Fraction> solution = solver.solve(rightHandSide);

        assertThat(solution.getDimension()).isEqualTo(2);
        assertThat(solution.getEntry(0)).isEqualTo(fraction(2));
        assertThat(solution.getEntry(1)).isEqualTo(fraction(1));
    }

    @Test
    void solveFieldMatrixSolvesEachColumnOfRightHandSide() {
        FieldDecompositionSolver<Fraction> solver = solverForInvertibleTwoByTwoMatrix();
        FieldMatrix<Fraction> rightHandSide = new Array2DRowFieldMatrix<>(
                FractionField.getInstance(), new Fraction[][] {
                        {fraction(5), fraction(4)},
                        {fraction(3), fraction(3)}
                }, false);

        FieldMatrix<Fraction> solution = solver.solve(rightHandSide);

        assertThat(solution.getRowDimension()).isEqualTo(2);
        assertThat(solution.getColumnDimension()).isEqualTo(2);
        assertThat(solution.getEntry(0, 0)).isEqualTo(fraction(2));
        assertThat(solution.getEntry(1, 0)).isEqualTo(fraction(1));
        assertThat(solution.getEntry(0, 1)).isEqualTo(fraction(1));
        assertThat(solution.getEntry(1, 1)).isEqualTo(fraction(2));
    }

    private static FieldDecompositionSolver<Fraction> solverForInvertibleTwoByTwoMatrix() {
        FieldMatrix<Fraction> coefficients = new Array2DRowFieldMatrix<>(FractionField.getInstance(), new Fraction[][] {
                {fraction(2), fraction(1)},
                {fraction(1), fraction(1)}
        }, false);
        return new FieldLUDecomposition<>(coefficients).getSolver();
    }

    private static Fraction fraction(int value) {
        return new Fraction(value);
    }
}
