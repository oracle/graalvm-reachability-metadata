/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.librealsense;

import static org.assertj.core.api.Assertions.assertThat;

import org.bytedeco.librealsense.motion_intrinsics;
import org.bytedeco.librealsense.rs_motion_device_intrinsic;
import org.junit.jupiter.api.Test;

public class MotionIntrinsicsTest {
    @Test
    void keepsAccelerometerAndGyroscopeCorrectionModelsIndependent() {
        float[][] accelerometerModel = {
            {1.01f, 0.02f, 0.03f, 0.10f},
            {0.04f, 1.05f, 0.06f, -0.20f},
            {0.07f, 0.08f, 1.09f, 0.30f}
        };
        float[][] gyroscopeModel = {
            {0.91f, -0.02f, -0.03f, -0.10f},
            {-0.04f, 0.95f, -0.06f, 0.20f},
            {-0.07f, -0.08f, 0.99f, -0.30f}
        };

        try (motion_intrinsics calibration = new motion_intrinsics()) {
            rs_motion_device_intrinsic accelerometer = calibration.acc();
            rs_motion_device_intrinsic gyroscope = calibration.gyro();
            writeCorrectionModel(accelerometer, accelerometerModel);
            writeCorrectionModel(gyroscope, gyroscopeModel);

            for (int axis = 0; axis < 3; axis++) {
                assertThat(readCorrectionRow(calibration.acc(), axis))
                        .containsExactly(accelerometerModel[axis]);
                assertThat(readCorrectionRow(calibration.gyro(), axis))
                        .containsExactly(gyroscopeModel[axis]);
            }
        }
    }

    @Test
    void preservesPerAxisNoiseAndBiasVariances() {
        float[] accelerometerNoise = {0.001f, 0.002f, 0.003f};
        float[] accelerometerBias = {0.011f, 0.012f, 0.013f};
        float[] gyroscopeNoise = {0.021f, 0.022f, 0.023f};
        float[] gyroscopeBias = {0.031f, 0.032f, 0.033f};

        try (motion_intrinsics calibration = new motion_intrinsics()) {
            rs_motion_device_intrinsic accelerometer = calibration.acc();
            rs_motion_device_intrinsic gyroscope = calibration.gyro();
            writeVariances(accelerometer, accelerometerNoise, accelerometerBias);
            writeVariances(gyroscope, gyroscopeNoise, gyroscopeBias);

            assertThat(readNoiseVariances(calibration.acc())).containsExactly(accelerometerNoise);
            assertThat(readBiasVariances(calibration.acc())).containsExactly(accelerometerBias);
            assertThat(readNoiseVariances(calibration.gyro())).containsExactly(gyroscopeNoise);
            assertThat(readBiasVariances(calibration.gyro())).containsExactly(gyroscopeBias);
        }
    }

    private static void writeCorrectionModel(
            rs_motion_device_intrinsic sensor, float[][] correctionModel) {
        for (int axis = 0; axis < 3; axis++) {
            for (int coefficient = 0; coefficient < 4; coefficient++) {
                sensor.data(axis, coefficient, correctionModel[axis][coefficient]);
            }
        }
    }

    private static float[] readCorrectionRow(rs_motion_device_intrinsic sensor, int axis) {
        float[] row = new float[4];
        for (int coefficient = 0; coefficient < row.length; coefficient++) {
            row[coefficient] = sensor.data(axis, coefficient);
        }
        return row;
    }

    private static void writeVariances(
            rs_motion_device_intrinsic sensor, float[] noise, float[] bias) {
        for (int axis = 0; axis < 3; axis++) {
            sensor.noise_variances(axis, noise[axis]);
            sensor.bias_variances(axis, bias[axis]);
        }
    }

    private static float[] readNoiseVariances(rs_motion_device_intrinsic sensor) {
        float[] variances = new float[3];
        for (int axis = 0; axis < variances.length; axis++) {
            variances[axis] = sensor.noise_variances(axis);
        }
        return variances;
    }

    private static float[] readBiasVariances(rs_motion_device_intrinsic sensor) {
        float[] variances = new float[3];
        for (int axis = 0; axis < variances.length; axis++) {
            variances[axis] = sensor.bias_variances(axis);
        }
        return variances;
    }
}
