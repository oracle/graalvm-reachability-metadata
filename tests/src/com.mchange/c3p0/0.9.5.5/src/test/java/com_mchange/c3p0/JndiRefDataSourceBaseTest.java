/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.JndiRefConnectionPoolDataSource;
import org.junit.jupiter.api.Test;

import javax.naming.CompositeName;
import javax.naming.Context;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class JndiRefDataSourceBaseTest {
    @Test
    void serializedJndiSourcePreservesLookupNameAndEnvironment() throws Exception {
        JndiRefConnectionPoolDataSource original = new JndiRefConnectionPoolDataSource(false);
        original.setIdentityToken(UUID.randomUUID().toString());
        original.setJndiName(new CompositeName("jdbc/reporting"));
        original.setJndiLookupCaching(false);
        Properties environment = new Properties();
        environment.setProperty(Context.SECURITY_PRINCIPAL, "report-reader");
        original.setJndiEnv(environment);
        JndiRefConnectionPoolDataSource restored = C3p0Support.roundTrip(original, JndiRefConnectionPoolDataSource.class);
        assertNotSame(original, restored);
        assertEquals(new CompositeName("jdbc/reporting"), restored.getJndiName());
        assertEquals("report-reader", restored.getJndiEnv().get(Context.SECURITY_PRINCIPAL));
        assertFalse(restored.isJndiLookupCaching());
    }
}
