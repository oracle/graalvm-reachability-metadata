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
    void createsFractionIdentityMatrix() {
        FieldMatrix<Fraction> identity = MatrixUtils.createFieldIdentityMatrix(FractionField.getInstance(), 3);

        assertThat(identity.getRowDimension()).isEqualTo(3);
        assertThat(identity.getColumnDimension()).isEqualTo(3);
        for (int row = 0; row < identity.getRowDimension(); row++) {
            for (int column = 0; column < identity.getColumnDimension(); column++) {
                Fraction expected = row == column ? Fraction.ONE : Fraction.ZERO;
                assertThat(identity.getEntry(row, column)).isEqualTo(expected);
            }
        }
    }

    @Test
    void serializesAndDeserializesTransientRealVectorField() throws Exception {
        VectorHolder holder = new VectorHolder("vector-holder");
        RealVector vector = MatrixUtils.createRealVector(new double[] {1.25, -2.5, 8.0});

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializeVector(vector)))) {
            MatrixUtils.deserializeRealVector(holder, "vector", input);
        }

        assertThat(holder.getName()).isEqualTo("vector-holder");
        assertThat(holder.getVector().toArray()).containsExactly(1.25, -2.5, 8.0);
    }

    @Test
    void serializesAndDeserializesTransientRealMatrixField() throws Exception {
        double[][] data = {
            {1.0, 2.5, -3.0},
            {4.25, 5.5, 6.75}
        };
        MatrixHolder holder = new MatrixHolder("matrix-holder");
        RealMatrix matrix = MatrixUtils.createRealMatrix(data);

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializeMatrix(matrix)))) {
            MatrixUtils.deserializeRealMatrix(holder, "matrix", input);
        }

        assertThat(holder.getName()).isEqualTo("matrix-holder");
        assertThat(holder.getMatrix().getData()).isDeepEqualTo(data);
    }

    private static byte[] serializeVector(RealVector vector) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            MatrixUtils.serializeRealVector(vector, output);
        }
        return bytes.toByteArray();
    }

    private static byte[] serializeMatrix(RealMatrix matrix) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            MatrixUtils.serializeRealMatrix(matrix, output);
        }
        return bytes.toByteArray();
    }

    private static final class VectorHolder {
        private final String name;
        private transient RealVector vector;

        private VectorHolder(String name) {
            this.name = name;
        }

        private String getName() {
            return name;
        }

        private RealVector getVector() {
            return vector;
        }
    }

    private static final class MatrixHolder {
        private final String name;
        private transient RealMatrix matrix;

        private MatrixHolder(String name) {
            this.name = name;
        }

        private String getName() {
            return name;
        }

        private RealMatrix getMatrix() {
            return matrix;
        }
    }
}
