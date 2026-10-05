/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.dialect.PostgreSQLInetJdbcType;
import org.hibernate.dialect.PostgreSQLPGObjectJdbcType;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.java.StringJavaType;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

public class PostgreSQLPGObjectJdbcTypeAnonymous1Test {

    @Test
    public void bindsAnInetValueThroughAPostgreSqlObject() throws Exception {
        PostgreSQLPGObjectJdbcType jdbcType = PostgreSQLInetJdbcType.INSTANCE;
        ValueBinder<String> binder = jdbcType.getBinder(StringJavaType.INSTANCE);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:pg-object");
                PreparedStatement statement = connection.prepareStatement("select ?")) {
            binder.bind(statement, "192.0.2.42", 1, null);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1))
                        .isInstanceOfSatisfying(PGobject.class, object -> {
                            assertThat(object.getType()).isEqualTo("inet");
                            assertThat(object.getValue()).isEqualTo("192.0.2.42");
                        });
            }
        }
    }
}
