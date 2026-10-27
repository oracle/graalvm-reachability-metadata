/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.engine.jdbc.ClobProxy;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.sql.Clob;

import static org.assertj.core.api.Assertions.assertThat;

public class ClobProxyTest {

    @Test
    public void createsClobsFromStringsAndReaders() throws Exception {
        Clob stringClob = ClobProxy.generateProxy("hibernate");
        Clob readerClob = ClobProxy.generateProxy(new StringReader("native"), 6);

        assertThat(stringClob.getSubString(1, 9)).isEqualTo("hibernate");
        assertThat(readerClob.getSubString(1, 6)).isEqualTo("native");

        stringClob.free();
        readerClob.free();
    }
}
