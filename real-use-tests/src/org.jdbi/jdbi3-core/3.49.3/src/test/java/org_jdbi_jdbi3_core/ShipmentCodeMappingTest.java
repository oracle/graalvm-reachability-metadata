/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi_jdbi3_core;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.argument.AbstractArgumentFactory;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.config.ConfigRegistry;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jdbi's custom type-mapping subsystem for application-specific identifiers.
public class ShipmentCodeMappingTest {
    @Test
    void bindsAndReadsApplicationSpecificShipmentCodes() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:shipment_codes");
        jdbi.registerArgument(new ShipmentCodeArgumentFactory());
        jdbi.registerColumnMapper(ShipmentCode.class, new ShipmentCodeColumnMapper());

        jdbi.useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE shipment (
                        code VARCHAR(32) PRIMARY KEY,
                        destination VARCHAR(64) NOT NULL
                    )
                    """);

            insertShipment(handle, new ShipmentCode("SHP-1042"), "Rotterdam");
            insertShipment(handle, new ShipmentCode("SHP-1043"), "Singapore");

            assertEquals(List.of(
                    new ShipmentCode("SHP-1042"),
                    new ShipmentCode("SHP-1043")),
                    handle.createQuery("""
                            SELECT code
                            FROM shipment
                            ORDER BY code
                            """)
                            .mapTo(ShipmentCode.class)
                            .list());
        });
    }

    private static void insertShipment(Handle handle, ShipmentCode code, String destination) {
        handle.createUpdate("""
                INSERT INTO shipment (code, destination)
                VALUES (:code, :destination)
                """)
                .bind("code", code)
                .bind("destination", destination)
                .execute();
    }

    private record ShipmentCode(String value) {
    }

    private static final class ShipmentCodeArgumentFactory extends AbstractArgumentFactory<ShipmentCode> {
        private ShipmentCodeArgumentFactory() {
            super(Types.VARCHAR);
        }

        @Override
        protected Argument build(ShipmentCode code, ConfigRegistry config) {
            return (position, statement, context) -> statement.setString(position, code.value());
        }
    }

    private static final class ShipmentCodeColumnMapper implements ColumnMapper<ShipmentCode> {
        @Override
        public ShipmentCode map(ResultSet resultSet, int columnNumber, StatementContext context)
                throws SQLException {
            return new ShipmentCode(resultSet.getString(columnNumber));
        }
    }
}
