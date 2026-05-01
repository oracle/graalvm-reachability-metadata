/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_session;

import org.eclipse.jetty.session.DatabaseAdaptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseAdaptorTest {
    @Test
    void initializeLoadsConfiguredDriverClassName() throws Exception {
        DatabaseAdaptor adaptor = new DatabaseAdaptor();
        String driverClassName = DatabaseAdaptorTest.class.getName();
        String connectionUrl = "jdbc:jetty-session-test";

        adaptor.setDriverInfo(driverClassName, connectionUrl);
        adaptor.initialize();

        assertThat(adaptor.getDriverClassName()).isEqualTo(driverClassName);
        assertThat(adaptor.getConnectionUrl()).isEqualTo(connectionUrl);
        assertThat(adaptor.getDatasource()).isNull();
    }
}
