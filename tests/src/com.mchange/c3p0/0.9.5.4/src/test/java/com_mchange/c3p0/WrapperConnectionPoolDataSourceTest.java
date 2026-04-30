/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import com.mchange.v2.c3p0.test.AlwaysFailConnectionTester;
import org.junit.jupiter.api.Test;

public class WrapperConnectionPoolDataSourceTest {
    @Test
    void reconfiguresConnectionTesterFromPublicClassName() throws Exception {
        WrapperConnectionPoolDataSource source = new WrapperConnectionPoolDataSource(false);
        String testerClassName = AlwaysFailConnectionTester.class.getName();

        source.setConnectionTesterClassName(testerClassName);

        assertThat(source.getConnectionTesterClassName()).isEqualTo(testerClassName);
    }
}
