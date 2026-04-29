/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpcore;

import org.apache.http.util.ExceptionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExceptionUtilsTest {
    @Test
    public void initCauseAssociatesOriginalFailure() {
        Throwable transportFailure = new Exception("transport failed");
        Throwable rootCause = new IllegalStateException("socket closed");

        ExceptionUtils.initCause(transportFailure, rootCause);

        assertThat(transportFailure).hasCause(rootCause);
    }

    @Test
    public void initCauseKeepsExistingCauseWhenThrowableRejectsReplacement() {
        Throwable originalCause = new IllegalArgumentException("original cause");
        Throwable transportFailure = new Exception("transport failed", originalCause);
        Throwable replacementCause = new IllegalStateException("replacement cause");

        ExceptionUtils.initCause(transportFailure, replacementCause);

        assertThat(transportFailure).hasCause(originalCause);
    }
}
