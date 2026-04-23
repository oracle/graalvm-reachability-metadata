/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.impl.JndiRefDataSourceBase;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class JndiRefDataSourceBaseTest {
    @Test
    void serializesJndiReferenceProperties() throws Exception {
        JndiRefDataSourceBase dataSource = new JndiRefDataSourceBase(false);
        Properties environment = new Properties();
        environment.put("java.naming.factory.initial", "example.Factory");
        dataSource.setCaching(false);
        dataSource.setFactoryClassLocation("factory-location");
        dataSource.setIdentityToken("jndi-" + UUID.randomUUID());
        dataSource.setJndiEnv(environment);
        dataSource.setJndiName("jdbc/test");

        JndiRefDataSourceBase restored = C3p0TestSupport.roundTrip(dataSource);

        assertThat(restored.isCaching()).isFalse();
        assertThat(restored.getFactoryClassLocation()).isEqualTo("factory-location");
        assertThat(restored.getJndiEnv()).containsEntry("java.naming.factory.initial", "example.Factory");
        assertThat(restored.getJndiName()).isEqualTo("jdbc/test");
    }
}
