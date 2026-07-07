/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_common.wildfly_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.wildfly.common.rpc.RemoteExceptionCause;

public class RemoteExceptionCauseAnonymous1Test {
    @Test
    void convertsSinglePublicExceptionField() {
        SingleFieldException exception = new SingleFieldException();

        RemoteExceptionCause remoteCause = RemoteExceptionCause.of(exception);

        assertThat(remoteCause.getExceptionClassName()).isEqualTo(exception.getClass().getName());
        assertThat(remoteCause.getFieldNames()).containsExactly("requestId");
        assertThat(remoteCause.getFieldValue("requestId")).isEqualTo("request-42");
    }

    @Test
    void convertsMultiplePublicExceptionFields() {
        MultipleFieldsException exception = new MultipleFieldsException();

        RemoteExceptionCause remoteCause = RemoteExceptionCause.of(exception);

        assertThat(remoteCause.getExceptionClassName()).isEqualTo(exception.getClass().getName());
        assertThat(remoteCause.getFieldNames()).containsExactlyInAnyOrder("attempts", "category");
        assertThat(remoteCause.getFieldValue("attempts")).isEqualTo("3");
        assertThat(remoteCause.getFieldValue("category")).isEqualTo("network");
    }

    public static class SingleFieldException extends Exception {
        public String requestId = "request-42";
    }

    public static class MultipleFieldsException extends Exception {
        public int attempts = 3;
        public String category = "network";
    }
}
