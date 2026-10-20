/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.Log;
import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogFactoryTest {
    @Test
    void discoversAFactoryAndCreatesANamedLogger() {
        LogFactory.releaseAll();
        try {
            Log first = LogFactory.getFactory().getInstance("kylin.connection");
            Log second = LogFactory.getLog("kylin.query");

            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
        } finally {
            LogFactory.releaseAll();
        }
    }
}
