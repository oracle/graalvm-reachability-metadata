/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_onnxruntime.onnxruntime;

import static org.assertj.core.api.Assertions.assertThat;

import ai.onnxruntime.OrtEnvironment;
import org.junit.jupiter.api.Test;

public class OnnxRuntimeTest {
    @Test
    void loadsNativeRuntimeFromBundledResources() {
        OrtEnvironment environment = OrtEnvironment.getEnvironment();

        assertThat(environment.getVersion()).isNotBlank();
        assertThat(OrtEnvironment.getAvailableProviders()).isNotEmpty();
    }
}
