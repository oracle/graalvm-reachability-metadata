/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.impl.DbAuth;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DbAuthTest {
    @Test
    void serializesDatabaseCredentials() throws Exception {
        DbAuth auth = new DbAuth("user", "password");

        DbAuth restored = C3p0TestSupport.roundTrip(auth);

        assertThat(restored.getUser()).isEqualTo("user");
        assertThat(restored.getPassword()).isEqualTo("password");
    }
}
