/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_common;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.model.util.Helper;
import org.junit.jupiter.api.Test;

public class HelperTest {
    @Test
    void loadsJsonResourceFromClasspath() {
        String json = Helper.loadJson("/io_fabric8/kubernetes_model_common/helper-load-json.json");

        assertThat(json).contains("\"apiVersion\": \"v1\"");
        assertThat(json).contains("\"kind\": \"Example\"");
    }
}
