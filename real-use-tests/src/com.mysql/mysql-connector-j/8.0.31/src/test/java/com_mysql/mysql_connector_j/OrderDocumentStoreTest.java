/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mysql.mysql_connector_j;

import com.mysql.cj.exceptions.CJException;
import com.mysql.cj.protocol.x.XProtocolError;
import com.mysql.cj.xdevapi.AddResult;
import com.mysql.cj.xdevapi.Client;
import com.mysql.cj.xdevapi.ClientFactory;
import com.mysql.cj.xdevapi.Collection;
import com.mysql.cj.xdevapi.DatabaseObject;
import com.mysql.cj.xdevapi.DbDoc;
import com.mysql.cj.xdevapi.DocResult;
import com.mysql.cj.xdevapi.JsonArray;
import com.mysql.cj.xdevapi.JsonNumber;
import com.mysql.cj.xdevapi.JsonParser;
import com.mysql.cj.xdevapi.JsonString;
import com.mysql.cj.xdevapi.Result;
import com.mysql.cj.xdevapi.Row;
import com.mysql.cj.xdevapi.RowResult;
import com.mysql.cj.xdevapi.Schema;
import com.mysql.cj.xdevapi.Session;
import com.mysql.cj.xdevapi.SessionFactory;
import com.mysql.cj.xdevapi.SqlResult;
import com.mysql.cj.xdevapi.Table;
import com.mysql.cj.xdevapi.XDevAPIError;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// X DevAPI document store over the X Protocol: an order service keeps JSON order documents in a
/// collection through a pooled `Client`, querying, modifying and indexing them, mixing them with a
/// relational table in one session transaction with savepoints, and mapping server errors.
public class OrderDocumentStoreTest {

    private static final String IMAGE = "container-registry.oracle.com/mysql/community-server:26.7.1";
    private static final String DATABASE = "shop";
    private static final String USER = "orders";
    private static final String PASSWORD = "orders-secret";
    private static final String CONTAINER = "real-use-mysql-xdevapi-" + UUID.randomUUID();

    private static String url;
    private static Client client;

    @BeforeAll
    static void startMysql() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        docker("run", "--rm", "-d", "--name", CONTAINER,
                "-e", "MYSQL_RANDOM_ROOT_PASSWORD=yes",
                "-e", "MYSQL_DATABASE=" + DATABASE,
                "-e", "MYSQL_USER=" + USER,
                "-e", "MYSQL_PASSWORD=" + PASSWORD,
                "-p", "127.0.0.1:" + port + ":33060",
                IMAGE);
        url = "mysqlx://" + USER + ":" + PASSWORD + "@127.0.0.1:" + port + "/" + DATABASE;
        awaitServer(TimeUnit.MINUTES.toMillis(3));
        client = new ClientFactory().getClient(url, """
                {"pooling": {"enabled": true, "maxSize": 3, "maxIdleTime": 0, "queueTimeout": 5000}}
                """);
    }

    @AfterAll
    static void stopMysql() throws Exception {
        if (client != null) {
            client.close();
        }
        docker("rm", "-f", CONTAINER);
    }

    @Test
    void storesQueriesAndModifiesOrderDocuments() {
        Session session = client.getSession();
        try {
            Schema schema = session.getDefaultSchema();
            assertThat(schema.getName()).isEqualTo(DATABASE);
            Collection orders = schema.createCollection("orders", true);
            orders.createIndex("idx_customer", """
                    {"fields": [{"field": "$.customer", "type": "TEXT(32)"}]}
                    """);

            AddResult added = orders.add("""
                    {"_id": "o-1", "customer": "ada", "total": 120.50, "status": "PAID", "tags": ["gift"]}
                    """, """
                    {"_id": "o-2", "customer": "ada", "total": 44.00, "status": "NEW", "tags": []}
                    """, """
                    {"_id": "o-3", "customer": "grace", "total": 310.25, "status": "PAID", "tags": []}
                    """).execute();
            assertThat(added.getAffectedItemsCount()).isEqualTo(3);
            assertThat(added.getGeneratedIds()).isEmpty();

            DbDoc parsed = JsonParser.parseDoc("""
                    {"customer": "linus", "total": 9.99, "status": "CANCELLED", "tags": ["promo"]}
                    """);
            AddResult generated = orders.add(parsed).execute();
            assertThat(generated.getGeneratedIds()).hasSize(1);
            String generatedId = generated.getGeneratedIds().get(0);
            assertThat(((JsonString) orders.getOne(generatedId).get("customer")).getString()).isEqualTo("linus");
            assertThat(orders.count()).isEqualTo(4);

            DocResult paid = orders.find("status = :status AND total > :minimum")
                    .bind("status", "PAID")
                    .bind("minimum", 100)
                    .fields("_id AS id", "customer AS customer", "total AS total")
                    .sort("total DESC")
                    .execute();
            List<String> paidIds = new ArrayList<>();
            for (DbDoc doc : paid.fetchAll()) {
                paidIds.add(((JsonString) doc.get("id")).getString());
                assertThat(doc).containsOnlyKeys("id", "customer", "total");
            }
            assertThat(paidIds).containsExactly("o-3", "o-1");

            DocResult spending = orders.find("status != 'CANCELLED'")
                    .fields("customer AS customer", "COUNT(*) AS orderCount", "SUM(total) AS spent")
                    .groupBy("customer")
                    .sort("customer")
                    .execute();
            Map<String, BigDecimal> spentByCustomer = new TreeMap<>();
            for (DbDoc doc : spending) {
                spentByCustomer.put(((JsonString) doc.get("customer")).getString(),
                        ((JsonNumber) doc.get("spent")).getBigDecimal());
            }
            assertThat(spentByCustomer).containsExactly(
                    Map.entry("ada", new BigDecimal("164.5")),
                    Map.entry("grace", new BigDecimal("310.25")));

            Result shipped = orders.modify("customer = :customer AND status = 'PAID'")
                    .bind("customer", "ada")
                    .set("status", "SHIPPED")
                    .arrayAppend("tags", "express")
                    .set("carrier", "DHL")
                    .execute();
            assertThat(shipped.getAffectedItemsCount()).isEqualTo(1);
            DbDoc first = orders.getOne("o-1");
            assertThat(((JsonString) first.get("status")).getString()).isEqualTo("SHIPPED");
            assertThat(((JsonString) first.get("carrier")).getString()).isEqualTo("DHL");
            JsonArray tags = (JsonArray) first.get("tags");
            assertThat(tags).extracting(tag -> ((JsonString) tag).getString()).containsExactly("gift", "express");

            orders.modify("_id = 'o-1'").unset("carrier").execute();
            assertThat(orders.getOne("o-1")).doesNotContainKey("carrier");

            Result replaced = orders.replaceOne("o-2", """
                    {"customer": "ada", "total": 48.00, "status": "PAID", "tags": ["corrected"]}
                    """);
            assertThat(replaced.getAffectedItemsCount()).isEqualTo(1);
            assertThat(((JsonNumber) orders.getOne("o-2").get("total")).getBigDecimal())
                    .isEqualByComparingTo("48.00");

            orders.addOrReplaceOne("o-4", """
                    {"customer": "grace", "total": 15.00, "status": "NEW", "tags": []}
                    """);
            assertThat(orders.getOne("o-4")).isNotNull();

            Result removed = orders.remove("status = 'CANCELLED'").execute();
            assertThat(removed.getAffectedItemsCount()).isEqualTo(1);
            assertThat(orders.getOne(generatedId)).isNull();
            assertThat(orders.count()).isEqualTo(4);

            assertThatThrownBy(() -> orders.add("""
                    {"_id": "o-3", "customer": "dup", "total": 1, "status": "NEW", "tags": []}
                    """).execute())
                    .isInstanceOf(XProtocolError.class)
                    .satisfies(error -> assertThat(((XProtocolError) error).getErrorCode()).isEqualTo(5116));
        } finally {
            session.close();
        }
    }

    @Test
    void commitsDocumentsAndRowsWithSavepoints() {
        Session session = client.getSession();
        try {
            session.sql("""
                    CREATE TABLE IF NOT EXISTS shipment (
                        id INT PRIMARY KEY,
                        order_id VARCHAR(16) NOT NULL,
                        carrier VARCHAR(16) NOT NULL
                    ) ENGINE = InnoDB
                    """).execute();
            Schema schema = session.getSchema(DATABASE);
            Collection invoices = schema.createCollection("invoices", true);
            Table shipments = schema.getTable("shipment");
            assertThat(shipments.existsInDatabase()).isEqualTo(DatabaseObject.DbObjectStatus.EXISTS);

            session.startTransaction();
            invoices.add("""
                    {"_id": "i-1", "order": "o-1", "amount": 120.50}
                    """).execute();
            shipments.insert("id", "order_id", "carrier").values(1, "o-1", "DHL").execute();
            String savepoint = session.setSavepoint("before_second");
            invoices.add("""
                    {"_id": "i-2", "order": "o-2", "amount": 48.00}
                    """).execute();
            shipments.insert("id", "order_id", "carrier").values(2, "o-2", "UPS").execute();
            session.rollbackTo(savepoint);
            session.commit();

            assertThat(invoices.count()).isEqualTo(1);
            assertThat(invoices.getOne("i-2")).isNull();
            RowResult rows = shipments.select("id", "order_id", "carrier")
                    .where("carrier = :carrier")
                    .bind("carrier", "DHL")
                    .orderBy("id")
                    .execute();
            List<Row> fetched = rows.fetchAll();
            assertThat(fetched).hasSize(1);
            assertThat(fetched.get(0).getInt("id")).isEqualTo(1);
            assertThat(fetched.get(0).getString("order_id")).isEqualTo("o-1");
            assertThat(rows.getColumnNames()).containsExactly("id", "order_id", "carrier");

            session.startTransaction();
            shipments.update().set("carrier", "FedEx").where("id = 1").execute();
            invoices.remove("_id = 'i-1'").execute();
            session.rollback();
            assertThat(shipments.select("carrier").where("id = 1").execute().fetchOne().getString("carrier"))
                    .isEqualTo("DHL");
            assertThat(invoices.count()).isEqualTo(1);

            SqlResult joined = session.sql("""
                    SELECT s.carrier, JSON_UNQUOTE(i.doc->'$.order') AS order_id, i.doc->'$.amount' AS amount
                    FROM shipment s JOIN invoices i ON JSON_UNQUOTE(i.doc->'$.order') = s.order_id
                    WHERE s.id = ?
                    """).bind(1).execute();
            assertThat(joined.hasData()).isTrue();
            Row row = joined.fetchOne();
            assertThat(row.getString("carrier")).isEqualTo("DHL");
            assertThat(row.getString("order_id")).isEqualTo("o-1");
            assertThat(row.getBigDecimal("amount")).isEqualByComparingTo("120.50");

            Result deleted = shipments.delete().where("id = 1").execute();
            assertThat(deleted.getAffectedItemsCount()).isEqualTo(1);
            assertThat(shipments.count()).isZero();

            assertThatThrownBy(() -> session.sql("SELECT * FROM missing_table").execute())
                    .isInstanceOf(XProtocolError.class)
                    .satisfies(error -> assertThat(((XProtocolError) error).getErrorCode()).isEqualTo(1146));
        } finally {
            session.close();
        }
    }

    @Test
    void poolsSessionsAndRejectsUseAfterClose() {
        Client pool = new ClientFactory().getClient(url, """
                {"pooling": {"enabled": true, "maxSize": 1, "queueTimeout": 1000}}
                """);
        Session first = pool.getSession();
        String connectionId = connectionId(first);
        assertThatThrownBy(pool::getSession)
                .isInstanceOf(CJException.class)
                .hasMessageContaining("Session can not be obtained within 1000 milliseconds");
        first.close();

        Session reused = pool.getSession();
        assertThat(connectionId(reused)).isEqualTo(connectionId);
        assertThat(reused.getDefaultSchemaName()).isEqualTo(DATABASE);
        pool.close();
        assertThat(reused.isOpen()).isFalse();
        assertThatThrownBy(pool::getSession)
                .isInstanceOf(XDevAPIError.class)
                .hasMessageContaining("Client is closed");

        Session direct = new SessionFactory().getSession(url);
        try {
            assertThat(direct.getSchemas()).extracting(Schema::getName).contains(DATABASE);
            assertThat(connectionId(direct)).isNotEqualTo(connectionId);
        } finally {
            direct.close();
        }
    }

    private static String connectionId(Session session) {
        return session.sql("SELECT CONNECTION_ID()").execute().fetchOne().getString(0);
    }

    /// Polls with a bounded deadline until the X Plugin accepts the application user.
    private static void awaitServer(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        RuntimeException lastFailure = null;
        SessionFactory factory = new SessionFactory();
        while (System.currentTimeMillis() < deadline) {
            try {
                Session session = factory.getSession(url);
                try {
                    session.sql("SELECT 1").execute();
                    return;
                } finally {
                    session.close();
                }
            } catch (RuntimeException e) {
                lastFailure = e;
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("MySQL X Plugin did not become reachable", lastFailure);
    }

    private static void docker(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).inheritIO().start();
        assertThat(process.waitFor(5, TimeUnit.MINUTES)).as("docker %s finished", args[0]).isTrue();
        assertThat(process.exitValue()).as("docker %s exit code", args[0]).isZero();
    }
}
