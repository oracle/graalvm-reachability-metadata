/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_math3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.commons.math3.fraction.Fraction;
import org.apache.commons.math3.fraction.FractionField;
import org.apache.commons.math3.linear.FieldMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.junit.jupiter.api.Test;

public class MatrixUtilsTest {
    @Test
    void createFieldIdentityMatrixBuildsFractionIdentityMatrix() {
        FieldMatrix<Fraction> identity = MatrixUtils.createFieldIdentityMatrix(FractionField.getInstance(), 3);

        assertThat(identity.getRowDimension()).isEqualTo(3);
        assertThat(identity.getColumnDimension()).isEqualTo(3);
        assertThat(identity.getEntry(0, 0)).isEqualTo(Fraction.ONE);
        assertThat(identity.getEntry(0, 1)).isEqualTo(Fraction.ZERO);
        assertThat(identity.getEntry(1, 0)).isEqualTo(Fraction.ZERO);
        assertThat(identity.getEntry(1, 1)).isEqualTo(Fraction.ONE);
        assertThat(identity.getEntry(1, 2)).isEqualTo(Fraction.ZERO);
        assertThat(identity.getEntry(2, 2)).isEqualTo(Fraction.ONE);
    }

    @Test
    void serializedRealVectorIsRestoredIntoTransientHolderField() throws Exception {
        RealVector vector = MatrixUtils.createRealVector(new double[] {1.25, -2.5, 3.75});
        VectorHolder restored = new VectorHolder("vector");

        try (ObjectInputStream input = inputForSerializedVector(vector)) {
            MatrixUtils.deserializeRealVector(restored, "coefficients", input);
        }

        assertThat(restored.getName()).isEqualTo("vector");
        assertThat(restored.getCoefficients().getDimension()).isEqualTo(3);
        assertThat(restored.getCoefficients().getEntry(0)).isEqualTo(1.25);
        assertThat(restored.getCoefficients().getEntry(1)).isEqualTo(-2.5);
        assertThat(restored.getCoefficients().getEntry(2)).isEqualTo(3.75);
    }

    @Test
    void serializedRealMatrixIsRestoredIntoTransientHolderField() throws Exception {
        RealMatrix matrix = MatrixUtils.createRealMatrix(new double[][] {
                {1.0, 2.0, 3.0},
                {4.5, 5.5, 6.5}
        });
        MatrixHolder restored = new MatrixHolder("matrix");

        try (ObjectInputStream input = inputForSerializedMatrix(matrix)) {
            MatrixUtils.deserializeRealMatrix(restored, "coefficients", input);
        }

        assertThat(restored.getName()).isEqualTo("matrix");
        assertThat(restored.getCoefficients().getRowDimension()).isEqualTo(2);
        assertThat(restored.getCoefficients().getColumnDimension()).isEqualTo(3);
        assertThat(restored.getCoefficients().getEntry(0, 0)).isEqualTo(1.0);
        assertThat(restored.getCoefficients().getEntry(0, 2)).isEqualTo(3.0);
        assertThat(restored.getCoefficients().getEntry(1, 0)).isEqualTo(4.5);
        assertThat(restored.getCoefficients().getEntry(1, 2)).isEqualTo(6.5);
    }

    private static ObjectInputStream inputForSerializedVector(RealVector vector) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            MatrixUtils.serializeRealVector(vector, output);
        }
        return new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static ObjectInputStream inputForSerializedMatrix(RealMatrix matrix) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            MatrixUtils.serializeRealMatrix(matrix, output);
        }
        return new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static final class VectorHolder {
        private final String name;
        private transient RealVector coefficients;

        private VectorHolder(String name) {
            this.name = name;
        }

        private String getName() {
            return name;
        }

        private RealVector getCoefficients() {
            return coefficients;
        }
    }

    private static final class MatrixHolder {
        private final String name;
        private transient RealMatrix coefficients;

        private MatrixHolder(String name) {
            this.name = name;
        }

        private String getName() {
            return name;
        }

        private RealMatrix getCoefficients() {
            return coefficients;
        }
    }
}
