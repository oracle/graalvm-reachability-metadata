/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.Log;
import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.LogSource;
import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.impl.SimpleLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class LogSourceTest {
    @Test
    void selectsAndConstructsALogImplementationByName() throws Exception {
        LogSource.setLogImplementation(SimpleLog.class.getName());

        Log log = LogSource.makeNewLogInstance("legacy.logger");

        assertThat(log).isInstanceOf(SimpleLog.class);
        assertThat(LogSource.getInstance("legacy.logger")).isNotNull();
    }
}
