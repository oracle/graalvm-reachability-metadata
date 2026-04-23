/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.impl.JndiRefDataSourceBase;
import org.junit.jupiter.api.Test;

import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;
import java.util.Hashtable;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class JndiRefDataSourceBaseTest {
    @Test
    void serializesJndiReferenceProperties() throws Exception {
        JndiRefDataSourceBase dataSource = new JndiRefDataSourceBase(false);
        Properties environment = new Properties();
        environment.put("java.naming.factory.initial", "example.Factory");
        dataSource.setCaching(false);
        dataSource.setFactoryClassLocation("factory-location");
        dataSource.setIdentityToken("jndi-" + UUID.randomUUID());
        dataSource.setJndiEnv(environment);
        dataSource.setJndiName("jdbc/test");

        JndiRefDataSourceBase restored = C3p0TestSupport.roundTrip(dataSource);

        assertThat(restored.isCaching()).isFalse();
        assertThat(restored.getFactoryClassLocation()).isEqualTo("factory-location");
        assertThat(restored.getJndiEnv()).containsEntry("java.naming.factory.initial", "example.Factory");
        assertThat(restored.getJndiName()).isEqualTo("jdbc/test");
    }

    @Test
    void roundTripsReferenceableJndiNameThroughIndirectSerialization() throws Exception {
        JndiRefDataSourceBase dataSource = new JndiRefDataSourceBase(false);
        dataSource.setIdentityToken("jndi-indirect-" + UUID.randomUUID());
        dataSource.setJndiName(new NonSerializableReferenceableJndiName("jdbc/indirect"));

        JndiRefDataSourceBase restored = C3p0TestSupport.roundTrip(dataSource);

        assertThat(restored.getJndiName()).isInstanceOf(NonSerializableReferenceableJndiName.class);
        assertThat(((NonSerializableReferenceableJndiName) restored.getJndiName()).getValue()).isEqualTo("jdbc/indirect");
    }

    private static final class NonSerializableReferenceableJndiName implements Referenceable {
        private final String value;

        private NonSerializableReferenceableJndiName(String value) {
            this.value = value;
        }

        private String getValue() {
            return value;
        }

        @Override
        public Reference getReference() {
            Reference reference = new Reference(
                NonSerializableReferenceableJndiName.class.getName(),
                NonSerializableReferenceableJndiNameFactory.class.getName(),
                null
            );
            reference.add(new StringRefAddr("value", value));
            return reference;
        }
    }

    public static final class NonSerializableReferenceableJndiNameFactory implements ObjectFactory {
        @Override
        public Object getObjectInstance(
            Object obj,
            javax.naming.Name name,
            javax.naming.Context nameCtx,
            Hashtable<?, ?> environment
        ) {
            Reference reference = (Reference) obj;
            return new NonSerializableReferenceableJndiName((String) reference.get("value").getContent());
        }
    }
}
