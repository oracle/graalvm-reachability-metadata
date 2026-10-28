/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jooq.jooq;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises jOOQ's reactive R2DBC execution subsystem to dispatch a carrier's ready shipments.
 */
public class ReactiveShipmentDispatchTest {
    private static final Table<Record> SHIPMENTS = DSL.table(DSL.name("reactive_shipment"));
    private static final Field<Integer> ID = DSL.field(DSL.name("shipment_id"), Integer.class);
    private static final Field<String> CARRIER = DSL.field(DSL.name("carrier"), String.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("shipment_status"), String.class);

    @Test
    @Timeout(60)
    public void dispatchesReadyShipmentsAndDoesNotRedispatchOnRetry() {
        ConnectionFactory connections = ConnectionFactories.get("r2dbc:h2:mem:///reactive_shipment_dispatch");

        // One subscribed chain owns the connection through setup, dispatch, retry, and readback.
        DispatchResult result = Mono.usingWhen(Mono.from(connections.create()), connection -> {
            DSLContext database = DSL.using(connection, SQLDialect.H2);
            return Mono.from(database.createTable(SHIPMENTS)
                            .column(ID, SQLDataType.INTEGER.nullable(false))
                            .column(CARRIER, SQLDataType.VARCHAR(30).nullable(false))
                            .column(STATUS, SQLDataType.VARCHAR(30).nullable(false))
                            .primaryKey(ID))
                    .then(Mono.from(database.insertInto(SHIPMENTS, ID, CARRIER, STATUS)
                            .values(101, "north", "READY")
                            .values(102, "south", "READY")
                            .values(103, "north", "PACKING")
                            .values(104, "north", "READY")
                            .values(105, "north", "DISPATCHED")))
                    .then(dispatchReady(database, "north"))
                    .flatMap(first -> dispatchReady(database, "north")
                            .flatMap(retry -> Flux.from(database.select(ID, CARRIER, STATUS)
                                            .from(SHIPMENTS).orderBy(ID))
                                    .map(row -> new Shipment(row.value1(), row.value2(), row.value3()))
                                    .collectList()
                                    .map(shipments -> new DispatchResult(first, retry, shipments))));
        }, connection -> Mono.from(connection.close())).block(Duration.ofSeconds(45));

        assertNotNull(result);
        assertEquals(new DispatchBatch(List.of(101, 104), 2), result.first());
        assertEquals(new DispatchBatch(List.of(), 0), result.retry());
        assertEquals(List.of(
                new Shipment(101, "north", "DISPATCHED"),
                new Shipment(102, "south", "READY"),
                new Shipment(103, "north", "PACKING"),
                new Shipment(104, "north", "DISPATCHED"),
                new Shipment(105, "north", "DISPATCHED")), result.shipments());
    }

    private static Mono<DispatchBatch> dispatchReady(DSLContext database, String carrier) {
        // jOOQ queries are subscribed as publishers; no blocking JDBC execution is used.
        return Flux.from(database.select(ID).from(SHIPMENTS)
                        .where(CARRIER.eq(carrier)).and(STATUS.eq("READY")).orderBy(ID))
                .map(row -> row.value1())
                .collectList()
                .flatMap(ids -> {
                    if (ids.isEmpty()) {
                        return Mono.just(new DispatchBatch(ids, 0));
                    }
                    return Mono.from(database.update(SHIPMENTS).set(STATUS, "DISPATCHED")
                                    .where(ID.in(ids)).and(STATUS.eq("READY")))
                            .map(updated -> new DispatchBatch(ids, updated));
                });
    }

    private record DispatchBatch(List<Integer> shipmentIds, int updated) {
    }

    private record Shipment(int id, String carrier, String status) {
    }

    private record DispatchResult(DispatchBatch first, DispatchBatch retry, List<Shipment> shipments) {
    }
}
