/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.opencv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.bytedeco.opencv.global.opencv_core.CV_64FC1;
import static org.bytedeco.opencv.global.opencv_core.solve;

import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.jupiter.api.Test;

public class OpencvCoreTest {
    @Test
    void solvesLinearSystemThroughJavaCppBindings() {
        try (Mat coefficients = new Mat(2, 2, CV_64FC1);
                Mat constants = new Mat(2, 1, CV_64FC1);
                Mat solution = new Mat()) {
            try (DoubleIndexer coefficientValues = coefficients.createIndexer();
                    DoubleIndexer constantValues = constants.createIndexer()) {
                coefficientValues.put(0, 0, 3.0);
                coefficientValues.put(0, 1, 1.0);
                coefficientValues.put(1, 0, 1.0);
                coefficientValues.put(1, 1, 2.0);
                constantValues.put(0, 0, 9.0);
                constantValues.put(1, 0, 8.0);
            }

            assertThat(solve(coefficients, constants, solution)).isTrue();
            assertThat(solution.rows()).isEqualTo(2);
            assertThat(solution.cols()).isEqualTo(1);

            try (DoubleIndexer solutionValues = solution.createIndexer()) {
                assertThat(solutionValues.get(0, 0)).isCloseTo(2.0, within(1.0e-10));
                assertThat(solutionValues.get(1, 0)).isCloseTo(3.0, within(1.0e-10));
            }
        }
    }
}
