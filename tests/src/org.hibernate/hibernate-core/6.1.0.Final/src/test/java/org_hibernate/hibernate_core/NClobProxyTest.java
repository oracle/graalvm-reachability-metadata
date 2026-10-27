/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.engine.jdbc.NClobProxy;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.sql.NClob;

import static org.assertj.core.api.Assertions.assertThat;

public class NClobProxyTest {

    @Test
    public void createsNationalizedClobsFromStringsAndReaders() throws Exception {
        NClob stringClob = NClobProxy.generateProxy("hibernate");
        NClob readerClob = NClobProxy.generateProxy(new StringReader("native"), 6);

        assertThat(stringClob.length()).isEqualTo(9);
        assertThat(readerClob.getSubString(1, 6)).isEqualTo("native");

        stringClob.free();
        readerClob.free();
    }
}
