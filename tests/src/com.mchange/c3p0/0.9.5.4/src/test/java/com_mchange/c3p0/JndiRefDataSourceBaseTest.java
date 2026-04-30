/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.impl.JndiRefDataSourceBase;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Hashtable;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import org.junit.jupiter.api.Test;

public class JndiRefDataSourceBaseTest {
    @Test
    void serializesAndDeserializesConfiguredJndiReferenceDataSource() throws Exception {
        JndiRefDataSourceBase source = configuredBase("jdbc/example");

        JndiRefDataSourceBase restored = roundTrip(source);

        assertThat(restored.isCaching()).isFalse();
        assertThat(restored.getFactoryClassLocation()).isEqualTo("factory/location");
        assertThat(restored.getIdentityToken()).isEqualTo("jndi-token");
        assertThat(restored.getJndiEnv()).containsEntry("java.naming.factory.initial", "test.factory");
        assertThat(restored.getJndiName()).isEqualTo("jdbc/example");
        assertThat(restored.getPropertyChangeListeners()).isEmpty();
        assertThat(restored.getVetoableChangeListeners()).isEmpty();
    }

    @Test
    void serializesReferenceableJndiNameIndirectly() throws Exception {
        JndiRefDataSourceBase source = configuredBase(new NonSerializableReferenceableJndiName("jdbc/indirect"));

        byte[] serialized = serialize(source);

        assertThat(serialized).isNotEmpty();
    }

    private static JndiRefDataSourceBase configuredBase(Object jndiName) throws Exception {
        Hashtable<String, String> jndiEnv = new Hashtable<>();
        jndiEnv.put("java.naming.factory.initial", "test.factory");

        JndiRefDataSourceBase source = new JndiRefDataSourceBase(false);
        source.setCaching(false);
        source.setFactoryClassLocation("factory/location");
        source.setIdentityToken("jndi-token");
        source.setJndiEnv(jndiEnv);
        source.setJndiName(jndiName);
        return source;
    }

    private static JndiRefDataSourceBase roundTrip(JndiRefDataSourceBase source) throws Exception {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialize(source)))) {
            return (JndiRefDataSourceBase) inputStream.readObject();
        }
    }

    private static byte[] serialize(JndiRefDataSourceBase source) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(byteStream)) {
            outputStream.writeObject(source);
        }
        return byteStream.toByteArray();
    }

    private static final class NonSerializableReferenceableJndiName implements Referenceable {
        private final String name;

        private NonSerializableReferenceableJndiName(String name) {
            this.name = name;
        }

        @Override
        public Reference getReference() {
            Reference reference = new Reference(NonSerializableReferenceableJndiName.class.getName());
            reference.add(new StringRefAddr("name", name));
            return reference;
        }
    }
}
