/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi_jdbi3_core;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises Jdbi's fluent transaction and row-mapping subsystem for warehouse reservations.
public class WarehouseReservationTransactionTest {
    @Test
    void commitsReservationsAndRollsBackAnIncompleteShipment() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:warehouse_reservations");

        jdbi.useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE reservation (
                        sku VARCHAR(32) PRIMARY KEY,
                        warehouse VARCHAR(32) NOT NULL,
                        quantity INT NOT NULL
                    )
                    """);

            handle.useTransaction(transaction -> {
                insertReservation(transaction, "KB-01", "north", 18);
                insertReservation(transaction, "MS-02", "north", 7);
            });

            assertEquals(List.of(
                    new Reservation("KB-01", "north", 18),
                    new Reservation("MS-02", "north", 7)),
                    reservationsIn(handle, "north"));

            assertThrows(IllegalStateException.class, () -> handle.useTransaction(transaction -> {
                insertReservation(transaction, "MN-27", "north", 3);
                throw new IllegalStateException("carrier rejected the shipment");
            }));

            assertEquals(List.of(
                    new Reservation("KB-01", "north", 18),
                    new Reservation("MS-02", "north", 7)),
                    reservationsIn(handle, "north"));
        });
    }

    private static void insertReservation(Handle handle, String sku, String warehouse, int quantity) {
        handle.createUpdate("""
                INSERT INTO reservation (sku, warehouse, quantity)
                VALUES (:sku, :warehouse, :quantity)
                """)
                .bind("sku", sku)
                .bind("warehouse", warehouse)
                .bind("quantity", quantity)
                .execute();
    }

    private static List<Reservation> reservationsIn(Handle handle, String warehouse) {
        return handle.createQuery("""
                SELECT sku, warehouse, quantity
                FROM reservation
                WHERE warehouse = :warehouse
                ORDER BY sku
                """)
                .bind("warehouse", warehouse)
                .map(new ReservationMapper())
                .list();
    }

    private record Reservation(String sku, String warehouse, int quantity) {
    }

    private static final class ReservationMapper implements RowMapper<Reservation> {
        @Override
        public Reservation map(ResultSet resultSet, StatementContext context) throws SQLException {
            return new Reservation(
                    resultSet.getString("sku"),
                    resultSet.getString("warehouse"),
                    resultSet.getInt("quantity"));
        }
    }
}
