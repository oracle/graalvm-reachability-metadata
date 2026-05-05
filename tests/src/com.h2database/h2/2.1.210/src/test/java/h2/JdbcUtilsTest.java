/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.api.JavaObjectSerializer;
import org.h2.util.JdbcUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JdbcUtilsTest {
    private static final String SERIALIZER_PROPERTY = "h2.javaObjectSerializer";
    private static final String TEST_SERIALIZER_CLASS = TestJavaObjectSerializer.class.getName();

    @BeforeAll
    static void configureJavaObjectSerializerProperty() {
        System.setProperty(SERIALIZER_PROPERTY, TEST_SERIALIZER_CLASS);
    }

    @Test
    void initializesConfiguredJavaObjectSerializerWithDefaultConstructor() {
        assertThat(JdbcUtils.serializer).isInstanceOf(TestJavaObjectSerializer.class);
    }

    @Test
    void loadsUserClassWithDefaultClassLoader() {
        assertThat(JdbcUtils.loadUserClass("java.lang.String")).isSameAs(String.class);
    }

    @Test
    void createsJdbcDriverWithDefaultConstructor() {
        assertThatThrownBy(() -> JdbcUtils.getConnection(
                "org.h2.Driver", "jdbc:jdbc-utils-driver:mem", "sa", ""))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("not suitable");
    }

    @Test
    void createsJndiContextWithDefaultConstructor() {
        assertThatThrownBy(() -> JdbcUtils.getConnection(
                "javax.naming.InitialContext", "java:comp/env/jdbc/missing", null, null))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void readsDatabaseMetaDataByInvokingNoArgumentMethods() throws SQLException {
        try (Connection connection = JdbcUtils.getConnection(
                "org.h2.Driver", "jdbc:h2:mem:jdbc-utils-metadata", "sa", "");
                ResultSet resultSet = JdbcUtils.getMetaResultSet(connection, "@info")) {
            assertThat(containsMetadataEntry(resultSet, "meta.getDatabaseProductName", "H2")).isTrue();
        }
    }

    @Test
    void serializesJavaObjectWithDefaultObjectStreams() {
        JavaObjectSerializer previousSerializer = JdbcUtils.serializer;
        try {
            JdbcUtils.serializer = null;

            byte[] bytes = JdbcUtils.serialize(new SamplePayload("sample"), null);
            Object deserialized = JdbcUtils.deserialize(bytes, null);

            assertThat(deserialized).isEqualTo(new SamplePayload("sample"));
        } finally {
            JdbcUtils.serializer = previousSerializer;
        }
    }

    private static boolean containsMetadataEntry(ResultSet resultSet, String expectedKey, String expectedValue)
            throws SQLException {
        while (resultSet.next()) {
            if (expectedKey.equals(resultSet.getString("KEY")) && expectedValue.equals(resultSet.getString("VALUE"))) {
                return true;
            }
        }
        return false;
    }

    public static final class TestJavaObjectSerializer implements JavaObjectSerializer {
        public TestJavaObjectSerializer() {
        }

        @Override
        public byte[] serialize(Object obj) throws Exception {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
                objectOutput.writeObject(obj);
            }
            return output.toByteArray();
        }

        @Override
        public Object deserialize(byte[] bytes) throws Exception {
            try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return objectInput.readObject();
            }
        }
    }

    private record SamplePayload(String value) implements Serializable {
    }
}
