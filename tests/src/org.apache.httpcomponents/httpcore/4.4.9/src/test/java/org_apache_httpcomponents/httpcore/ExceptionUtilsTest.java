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

@SuppressWarnings("deprecation")
class ExceptionUtilsTest {

    @Test
    void initCauseAssignsTheProvidedCause() {
        IllegalArgumentException throwable = new IllegalArgumentException("outer");
        IllegalStateException cause = new IllegalStateException("inner");

        ExceptionUtils.initCause(throwable, cause);

        assertThat(throwable).hasCause(cause);
    }
}
