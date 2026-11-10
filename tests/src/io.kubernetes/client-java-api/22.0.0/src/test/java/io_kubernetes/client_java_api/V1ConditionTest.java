/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kubernetes.client_java_api;

import io.kubernetes.client.openapi.models.V1Condition;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V1ConditionTest {
    @Test
    void readsAndWritesControllerCondition() throws Exception {
        V1Condition condition = V1Condition.fromJson(ModelJson.CONDITION);
        assertThat(condition.getLastTransitionTime()).isEqualTo(OffsetDateTime.parse("2024-03-04T05:06:07Z"));
        assertThat(condition.getMessage()).isEqualTo("Address allocated");
        assertThat(condition.getObservedGeneration()).isEqualTo(3L);
        assertThat(condition.getReason()).isEqualTo("Allocated");
        assertThat(condition.getStatus()).isEqualTo("True");
        assertThat(condition.getType()).isEqualTo("Ready");
        ModelJson.assertJsonEquals(ModelJson.CONDITION, condition.toJson());
    }
}
