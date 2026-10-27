/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import org.hsqldb.server.HsqlSocketFactory;
import org.junit.jupiter.api.Test;

public class HsqlSocketFactoryTest {
    @Test
    void createsSecureSocketFactory() throws Exception {
        HsqlSocketFactory socketFactory = HsqlSocketFactory.getInstance(true);

        assertThat(socketFactory.isSecure()).isTrue();
    }
}
