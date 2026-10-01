/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import org.apache.curator.shaded.com.google.common.base.Throwables;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThrowablesTest {
    @Test
    void exposesLazyStackTraceThroughThePublicApi() {
        IllegalStateException failure = new IllegalStateException("failure");

        assertThat(Throwables.getStackTraceAsString(failure)).contains("failure");
        assertThat(Throwables.lazyStackTrace(failure)).isNotEmpty();
    }
}
