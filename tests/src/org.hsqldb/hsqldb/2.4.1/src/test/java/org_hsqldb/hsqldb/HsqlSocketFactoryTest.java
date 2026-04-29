/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import org.hsqldb.server.HsqlSocketFactory;
import org.hsqldb.server.HsqlSocketFactorySecure;
import org.junit.jupiter.api.Test;

public class HsqlSocketFactoryTest {
    @Test
    public void createsSecureSocketFactoryThroughPublicFactoryMethod() throws Exception {
        HsqlSocketFactory factory = HsqlSocketFactory.getInstance(true);

        assertThat(factory).isInstanceOf(HsqlSocketFactorySecure.class);
        assertThat(factory.isSecure()).isTrue();
    }
}
