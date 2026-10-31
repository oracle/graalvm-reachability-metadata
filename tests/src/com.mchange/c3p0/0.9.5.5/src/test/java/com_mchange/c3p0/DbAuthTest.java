/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.impl.DbAuth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DbAuthTest {
    @Test
    void serializedCredentialsPreserveIdentityAndMasking() throws Exception {
        DbAuth original = new DbAuth("report-reader", "secret");
        DbAuth restored = C3p0Support.roundTrip(original, DbAuth.class);
        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals(original.hashCode(), restored.hashCode());
        assertEquals("report-reader", restored.getUser());
        assertEquals("secret", restored.getPassword());
        assertEquals("re******", restored.getMaskedUserString());
        DbAuth anonymous = C3p0Support.roundTrip(new DbAuth(null, null), DbAuth.class);
        assertNull(anonymous.getUser());
        assertNull(anonymous.getPassword());
    }
}
