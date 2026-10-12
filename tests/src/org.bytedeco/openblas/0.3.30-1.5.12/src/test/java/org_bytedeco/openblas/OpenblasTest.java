/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.openblas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bytedeco.openblas.global.openblas.CblasNoTrans;
import static org.bytedeco.openblas.global.openblas.CblasRowMajor;
import static org.bytedeco.openblas.global.openblas.cblas_dgemm;

import org.junit.jupiter.api.Test;

public class OpenblasTest {
    @Test
    void multipliesMatricesInRowMajorOrder() {
        double[] left = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        double[] right = {7.0, 8.0, 9.0, 10.0, 11.0, 12.0};
        double[] product = new double[4];

        cblas_dgemm(
                CblasRowMajor,
                CblasNoTrans,
                CblasNoTrans,
                2,
                2,
                3,
                1.0,
                left,
                3,
                right,
                2,
                0.0,
                product,
                2);

        assertThat(product).containsExactly(58.0, 64.0, 139.0, 154.0);
    }
}
