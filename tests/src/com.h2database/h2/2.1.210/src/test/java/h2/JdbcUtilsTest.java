/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.api.JavaObjectSerializer;
import org.h2.util.JdbcUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JdbcUtilsTest {
    private static final String SERIALIZER_CLASS_NAME = "h2.JdbcUtilsTest$Serializer";

    static {
        System.setProperty("h2.javaObjectSerializer", SERIALIZER_CLASS_NAME);
    }

    @Test
    void loadUserClassUsesDefaultClassLookup() {
        Class<String> loaded = JdbcUtils.loadUserClass("java.lang.String");

        assertThat(loaded).isSameAs(String.class);
    }

    @Test
    void staticInitializerInstantiatesConfiguredJavaObjectSerializer() {
        assertThat(JdbcUtils.serializer).isInstanceOf(Serializer.class);

        byte[] serialized = JdbcUtils.serialize("serializer-roundtrip", JdbcUtils.serializer);
        Object deserialized = JdbcUtils.deserialize(serialized, JdbcUtils.serializer);

        assertThat(deserialized).isEqualTo("serializer-roundtrip");
    }

    @Test
    void explicitJdbcDriverClassIsInstantiatedBeforeConnecting() {
        assertThatThrownBy(() -> JdbcUtils.getConnection("org.h2.Driver", "jdbc:not-h2:mem:driver", "sa", ""))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("not suitable");
    }

    @Test
    void explicitJndiContextClassIsInstantiatedBeforeLookup() {
        assertThatThrownBy(() -> JdbcUtils.getConnection(
                "javax.naming.InitialContext", "java:comp/env/jdbc/missing", null, null))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void infoMetadataInvokesZeroArgumentDatabaseMetaDataMethods() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:jdbcUtilsInfo");
                ResultSet resultSet = JdbcUtils.getMetaResultSet(connection, "@info")) {
            Set<String> keys = new HashSet<>();
            while (resultSet.next()) {
                keys.add(resultSet.getString("KEY"));
            }

            assertThat(keys)
                    .contains("conn.getCatalog")
                    .contains("meta.getDatabaseProductName")
                    .contains("meta.supportsTransactions");
        }
    }

    public static final class Serializer implements JavaObjectSerializer {
        @Override
        public byte[] serialize(Object obj) throws Exception {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ObjectOutputStream objectOut = new ObjectOutputStream(out)) {
                objectOut.writeObject(obj);
            }
            return out.toByteArray();
        }

        @Override
        public Object deserialize(byte[] bytes) throws Exception {
            try (ObjectInputStream objectIn = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return objectIn.readObject();
            }
        }
    }
}
