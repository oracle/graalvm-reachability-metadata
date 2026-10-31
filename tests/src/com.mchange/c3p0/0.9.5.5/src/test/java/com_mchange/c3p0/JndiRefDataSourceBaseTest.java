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
import javax.naming.Name;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class JndiRefDataSourceBaseTest {
    @Test
    void serializedJndiSourceResolvesAReferenceOnlyLookupName() throws Exception {
        JndiRefConnectionPoolDataSource original = new JndiRefConnectionPoolDataSource(false);
        original.setIdentityToken(UUID.randomUUID().toString());
        CompositeName expected = new CompositeName("jdbc/reference-only");
        original.setJndiName(new ReferenceOnlyName(expected.getAll()));
        original.setJndiLookupCaching(false);

        JndiRefConnectionPoolDataSource restored = C3p0Support.roundTrip(original, JndiRefConnectionPoolDataSource.class);

        assertNotSame(original, restored);
        assertEquals(expected, restored.getJndiName());
        assertEquals(original.getIdentityToken(), restored.getIdentityToken());
        assertFalse(restored.isJndiLookupCaching());
    }

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

    /// An application name that is persisted through its JNDI reference, not its object state.
    public static final class ReferenceOnlyName extends CompositeName implements Referenceable {
        private static final long serialVersionUID = 1L;

        private ReferenceOnlyName(Enumeration<String> components) {
            super(components);
        }

        @Override
        public Object clone() {
            return new ReferenceOnlyName(getAll());
        }

        @Override
        public Reference getReference() {
            return new Reference(CompositeName.class.getName(), new StringRefAddr("name", toString()),
                            NameFactory.class.getName(), null);
        }

        private void writeObject(ObjectOutputStream output) throws NotSerializableException {
            throw new NotSerializableException("Persist this name through its JNDI reference");
        }
    }

    public static final class NameFactory implements ObjectFactory {
        @Override
        public Object getObjectInstance(Object object, Name name, Context context, Hashtable<?, ?> environment) throws Exception {
            Reference reference = (Reference) object;
            return new CompositeName((String) reference.get("name").getContent());
        }
    }
}
