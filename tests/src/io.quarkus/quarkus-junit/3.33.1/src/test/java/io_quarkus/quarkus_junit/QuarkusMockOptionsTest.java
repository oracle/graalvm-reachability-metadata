/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_junit;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusMock;
import org.junit.jupiter.api.Test;

public class QuarkusMockOptionsTest {
    @Test
    void mockObserverOptionIsDisabledByDefaultAndFluent() {
        QuarkusMock.Options options = new QuarkusMock.Options();

        assertThat(options.isMockObservers()).isFalse();
        assertThat(options.setMockObservers(true)).isSameAs(options);
        assertThat(options.isMockObservers()).isTrue();
        assertThat(options.setMockObservers(false)).isSameAs(options);
        assertThat(options.isMockObservers()).isFalse();
    }
}
