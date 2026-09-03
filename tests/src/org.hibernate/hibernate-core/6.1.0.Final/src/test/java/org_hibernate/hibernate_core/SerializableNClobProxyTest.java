/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.engine.jdbc.NClobProxy;
import org.hibernate.engine.jdbc.SerializableNClobProxy;
import org.junit.jupiter.api.Test;

import java.sql.NClob;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableNClobProxyTest {

    @Test
    public void wrapsANationalizedClob() throws Exception {
        NClob nclob = SerializableNClobProxy.generateProxy(
                NClobProxy.generateProxy("hibernate")
        );

        assertThat(nclob.length()).isEqualTo(9);
        assertThat(nclob.getSubString(1, 9)).isEqualTo("hibernate");
    }
}
