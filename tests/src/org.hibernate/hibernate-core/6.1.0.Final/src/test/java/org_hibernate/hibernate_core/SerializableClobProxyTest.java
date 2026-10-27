/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.engine.jdbc.SerializableClobProxy;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.serial.SerialClob;
import java.sql.Clob;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableClobProxyTest {

    @Test
    public void delegatesClobOperationsToTheWrappedClob() throws Exception {
        Clob clob = SerializableClobProxy.generateProxy(
                new SerialClob("hibernate".toCharArray())
        );

        assertThat(clob.length()).isEqualTo(9);
        assertThat(clob.getSubString(1, 9)).isEqualTo("hibernate");
    }
}
