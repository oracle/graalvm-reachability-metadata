/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_posix;

import jnr.posix.util.FieldAccess;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldAccessTest {
    @Test
    void readsProtectedFieldValue() {
        ProtectedState state = new ProtectedState("available");

        Object value = FieldAccess.getProtectedFieldValue(ProtectedState.class, "status", state);

        assertThat(value).isEqualTo("available");
    }

    private static final class ProtectedState {
        protected final String status;

        private ProtectedState(String status) {
            this.status = status;
        }
    }
}
