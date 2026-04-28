/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_awaitility.awaitility;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.fieldIn;
import static org.hamcrest.Matchers.equalTo;

public class CallableHamcrestConditionTest {

    @Test
    void describesNamedFieldSupplierWhenMatched() {
        FieldBackedService service = new FieldBackedService("ready");

        String result = await()
                .pollDelay(Duration.ZERO)
                .pollInterval(Duration.ofMillis(1))
                .atMost(Duration.ofMillis(100))
                .until(fieldIn(service).ofType(String.class).andWithName("state"), equalTo("ready"));

        assertThat(result).isEqualTo("ready");
    }

    private static final class FieldBackedService {
        private final String state;

        private FieldBackedService(String state) {
            this.state = state;
        }
    }
}
