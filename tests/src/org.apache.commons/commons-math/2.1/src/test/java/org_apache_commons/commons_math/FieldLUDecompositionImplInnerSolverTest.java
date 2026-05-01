/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_math;

import org.apache.commons.math.Field;
import org.apache.commons.math.fraction.Fraction;
import org.apache.commons.math.fraction.FractionField;
import org.apache.commons.math.linear.FieldDecompositionSolver;
import org.apache.commons.math.linear.FieldLUDecompositionImpl;
import org.apache.commons.math.linear.FieldMatrix;
import org.apache.commons.math.linear.FieldVector;
import org.apache.commons.math.linear.MatrixUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldLUDecompositionImplInnerSolverTest {
    @Test
    void solveArrayAllocatesTypedFractionResult() {
        FieldDecompositionSolver<Fraction> solver = solverForTwoByTwoSystem();

        Fraction[] solution = solver.solve(new Fraction[] {fraction(1), fraction(2)});

        assertThat(solution).containsExactly(fraction(1), fraction(-1));
        assertThat(solution.getClass().getComponentType()).isEqualTo(Fraction.class);
    }

    @Test
    void solveFieldVectorAllocatesTypedFractionResultForNonArrayVector() {
        FieldDecompositionSolver<Fraction> solver = solverForTwoByTwoSystem();
        FieldVector<Fraction> rightHandSide = new ReadOnlyFieldVector(new Fraction[] {fraction(4), fraction(11)});

        FieldVector<Fraction> solution = solver.solve(rightHandSide);

        assertThat(solution.toArray()).containsExactly(fraction(1), fraction(2));
    }

    @Test
    void solveFieldMatrixAllocatesTypedFractionMatrixResult() {
        FieldDecompositionSolver<Fraction> solver = solverForTwoByTwoSystem();
        FieldMatrix<Fraction> rightHandSide = MatrixUtils.createFieldMatrix(new Fraction[][] {
                {fraction(1), fraction(4)},
                {fraction(2), fraction(11)}
        });

        FieldMatrix<Fraction> solution = solver.solve(rightHandSide);

        assertThat(solution.getRowDimension()).isEqualTo(2);
        assertThat(solution.getColumnDimension()).isEqualTo(2);
        assertThat(solution.getEntry(0, 0)).isEqualTo(fraction(1));
        assertThat(solution.getEntry(1, 0)).isEqualTo(fraction(-1));
        assertThat(solution.getEntry(0, 1)).isEqualTo(fraction(1));
        assertThat(solution.getEntry(1, 1)).isEqualTo(fraction(2));
    }

    private static FieldDecompositionSolver<Fraction> solverForTwoByTwoSystem() {
        FieldMatrix<Fraction> matrix = MatrixUtils.createFieldMatrix(new Fraction[][] {
                {fraction(2), fraction(1)},
                {fraction(5), fraction(3)}
        });
        return new FieldLUDecompositionImpl<>(matrix).getSolver();
    }

    private static Fraction fraction(int numerator) {
        return new Fraction(numerator);
    }

    private static final class ReadOnlyFieldVector implements FieldVector<Fraction> {
        private final Fraction[] entries;

        private ReadOnlyFieldVector(Fraction[] entries) {
            this.entries = entries.clone();
        }

        @Override
        public Field<Fraction> getField() {
            return FractionField.getInstance();
        }

        @Override
        public Fraction getEntry(int index) {
            return entries[index];
        }

        @Override
        public int getDimension() {
            return entries.length;
        }

        @Override
        public Fraction[] toArray() {
            return entries.clone();
        }

        @Override
        public Fraction[] getData() {
            return toArray();
        }

        @Override
        public FieldVector<Fraction> copy() {
            return new ReadOnlyFieldVector(entries);
        }

        @Override
        public FieldVector<Fraction> add(FieldVector<Fraction> v) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> add(Fraction[] v) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> subtract(FieldVector<Fraction> v) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> subtract(Fraction[] v) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> mapAdd(Fraction d) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> mapAddToSelf(Fraction d) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> mapSubtract(Fraction d) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> mapSubtractToSelf(Fraction d) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> mapMultiply(Fraction d) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> mapMultiplyToSelf(Fraction d) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> mapDivide(Fraction d) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> mapDivideToSelf(Fraction d) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> mapInv() {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> mapInvToSelf() {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> ebeMultiply(FieldVector<Fraction> v) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> ebeMultiply(Fraction[] v) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> ebeDivide(FieldVector<Fraction> v) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> ebeDivide(Fraction[] v) {
            throw unsupported();
        }

        @Override
        public Fraction dotProduct(FieldVector<Fraction> v) {
            throw unsupported();
        }

        @Override
        public Fraction dotProduct(Fraction[] v) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> projection(FieldVector<Fraction> v) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> projection(Fraction[] v) {
            throw unsupported();
        }

        @Override
        public FieldMatrix<Fraction> outerProduct(FieldVector<Fraction> v) {
            throw unsupported();
        }

        @Override
        public FieldMatrix<Fraction> outerProduct(Fraction[] v) {
            throw unsupported();
        }

        @Override
        public void setEntry(int index, Fraction value) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> append(FieldVector<Fraction> v) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> append(Fraction d) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> append(Fraction[] a) {
            throw unsupported();
        }

        @Override
        public FieldVector<Fraction> getSubVector(int index, int n) {
            throw unsupported();
        }

        @Override
        public void setSubVector(int index, FieldVector<Fraction> v) {
            throw unsupported();
        }

        @Override
        public void setSubVector(int index, Fraction[] v) {
            throw unsupported();
        }

        @Override
        public void set(Fraction value) {
            throw unsupported();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("This vector is read-only for solver coverage");
        }
    }
}
