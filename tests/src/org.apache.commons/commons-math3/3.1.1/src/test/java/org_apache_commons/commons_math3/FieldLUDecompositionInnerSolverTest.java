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
    @SuppressWarnings("deprecation")
    void solvesSparseFieldVectorRightHandSide() {
        FieldDecompositionSolver<Fraction> solver = createSolver();
        SparseFieldVector<Fraction> rightHandSide = new SparseFieldVector<Fraction>(FractionField.getInstance(), 2);
        rightHandSide.setEntry(0, new Fraction(5));
        rightHandSide.setEntry(1, new Fraction(3));

        FieldVector<Fraction> solution = solver.solve(rightHandSide);

        assertThat(solution.getEntry(0)).isEqualTo(new Fraction(2));
        assertThat(solution.getEntry(1)).isEqualTo(new Fraction(1));
    }

    @Test
    void solvesArrayFieldVectorRightHandSide() {
        FieldDecompositionSolver<Fraction> solver = createSolver();
        ArrayFieldVector<Fraction> rightHandSide = new ArrayFieldVector<Fraction>(new Fraction[] {
            new Fraction(5),
            new Fraction(3)
        });

        FieldVector<Fraction> solution = solver.solve(rightHandSide);

        assertThat(solution.getEntry(0)).isEqualTo(new Fraction(2));
        assertThat(solution.getEntry(1)).isEqualTo(new Fraction(1));
    }

    @Test
    void solvesMatrixRightHandSide() {
        FieldDecompositionSolver<Fraction> solver = createSolver();
        FieldMatrix<Fraction> rightHandSide = new Array2DRowFieldMatrix<Fraction>(new Fraction[][] {
            {new Fraction(5), new Fraction(1)},
            {new Fraction(3), new Fraction(2)}
        });

        FieldMatrix<Fraction> solution = solver.solve(rightHandSide);

        assertThat(solution.getEntry(0, 0)).isEqualTo(new Fraction(2));
        assertThat(solution.getEntry(1, 0)).isEqualTo(new Fraction(1));
        assertThat(solution.getEntry(0, 1)).isEqualTo(new Fraction(-1));
        assertThat(solution.getEntry(1, 1)).isEqualTo(new Fraction(3));
    }

    private static FieldDecompositionSolver<Fraction> createSolver() {
        FieldMatrix<Fraction> matrix = new Array2DRowFieldMatrix<Fraction>(new Fraction[][] {
            {new Fraction(2), new Fraction(1)},
            {new Fraction(1), new Fraction(1)}
        });
        return new FieldLUDecomposition<Fraction>(matrix).getSolver();
    }
}
