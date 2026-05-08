/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises string-decoded JDBC arrays backed by {@code ArrayDecoding.AbstractObjectStringArrayDecoder}.
 */
public class ArrayDecodingInnerAbstractObjectStringArrayDecoderTest {

    private static final String USERNAME = "fred";

    private static final String PASSWORD = "secret";

    private static final String DATABASE = "test";

    private static PostgresqlTestContainer container;

    @BeforeAll
    static void beforeAll() throws Exception {
        container = PostgresqlTestContainer.start(DATABASE, USERNAME, PASSWORD);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (container != null) {
            container.close();
        }
    }

    @Test
    void getArrayDecodesOneDimensionalNumericArray() throws Exception {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT ARRAY[1::numeric, NULL::numeric, 3::numeric]")) {
            assertThat(resultSet.next()).isTrue();

            Array sqlArray = resultSet.getArray(1);
            try {
                Object decodedArray = sqlArray.getArray();

                assertThat(decodedArray).isInstanceOf(BigDecimal[].class);
                BigDecimal[] values = (BigDecimal[]) decodedArray;
                assertThat(values).containsExactly(new BigDecimal("1"), null, new BigDecimal("3"));
            } finally {
                sqlArray.free();
            }
        }
    }

    @Test
    void getArrayDecodesMultiDimensionalNumericArray() throws Exception {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT ARRAY[[1::numeric, 2::numeric], [3::numeric, 4::numeric]]")) {
            assertThat(resultSet.next()).isTrue();

            Array sqlArray = resultSet.getArray(1);
            try {
                Object decodedArray = sqlArray.getArray();

                assertThat(decodedArray).isInstanceOf(BigDecimal[][].class);
                BigDecimal[][] values = (BigDecimal[][]) decodedArray;
                assertThat(values.length).isEqualTo(2);
                assertThat(values[0]).containsExactly(new BigDecimal("1"), new BigDecimal("2"));
                assertThat(values[1]).containsExactly(new BigDecimal("3"), new BigDecimal("4"));
            } finally {
                sqlArray.free();
            }
        }
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(container.jdbcUrl() + "?preferQueryMode=simple", container.connectionProperties());
    }
}
