/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.awaitility.reflect.WhiteboxImpl;

import static org.assertj.core.api.Assertions.assertThat;

public class WhiteboxImplTest {
    @Test
    void readsInternalStateByNameAndType() {
        State state = new State();

        assertThat(WhiteboxImpl.getInternalState(state, "message")).isEqualTo("ready");
        assertThat(WhiteboxImpl.getInternalState(state, String.class)).isEqualTo("ready");
        assertThat(WhiteboxImpl.getByNameAndType(state, "message", String.class)).isEqualTo("ready");
    }

    public static class State {
        private final String message = "ready";
    }
}
