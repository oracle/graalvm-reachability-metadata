/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.ServerAddress;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Field;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.IndexOptions;
import org.mongodb.morphia.annotations.Indexed;
import org.mongodb.morphia.annotations.Indexes;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationBuilderTest {
    private static final String DOCKER_HOST = "127.0.0.1";
    private static final String IMAGE = "mongo:3.4.24-xenial";
    private static final String DATABASE_NAME = "morphia_annotation_builder_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String CONTAINER_NAME = "morphia-annotation-builder-" + UUID.randomUUID();
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(50);
    private static final int CLIENT_TIMEOUT_MILLIS = 10_000;
    private static final int MONGO_PORT = 27017;

    private static int hostPort;
    private static Process process;
    private static MongoClient mongoClient;

    @BeforeAll
    public static void beforeAll() throws Exception {
        hostPort = findAvailablePort();
        process = new ProcessBuilder(
                "docker", "run", "--rm", "--name", CONTAINER_NAME,
                "-p", DOCKER_HOST + ":" + hostPort + ":" + MONGO_PORT, IMAGE)
                .redirectOutput(new File("mongo-stdout.txt"))
                .redirectError(new File("mongo-stderr.txt"))
                .start();
        mongoClient = waitForMongo();
    }

    @AfterAll
    public static void afterAll() throws Exception {
        if (mongoClient != null) {
            try {
                mongoClient.dropDatabase(DATABASE_NAME);
            } finally {
                mongoClient.close();
            }
        }
        stopContainer();
    }

    @Test
    public void createsIndexesFromAnnotationDefinitions() {
        final Morphia morphia = new Morphia();
        morphia.map(IndexedEntity.class);
        final Datastore datastore = morphia.createDatastore(mongoClient, DATABASE_NAME);

        datastore.ensureIndexes(IndexedEntity.class);

        final Map<String, Document> indexes = indexesByName();
        assertThat(indexes).containsKeys("name_ascending_index", "email_unique_index");
        assertThat(indexes.get("email_unique_index").getBoolean("unique")).isTrue();
    }

    private static MongoClient waitForMongo() throws InterruptedException {
        final Instant deadline = Instant.now().plus(STARTUP_TIMEOUT);
        RuntimeException lastConnectionException = null;
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                throw new IllegalStateException(
                        "MongoDB process exited with code " + process.exitValue(), lastConnectionException);
            }
            MongoClient client = null;
            try {
                client = createMongoClient();
                client.getDatabase("admin").runCommand(new Document("ping", 1));
                return client;
            } catch (RuntimeException exception) {
                if (client != null) {
                    client.close();
                }
                lastConnectionException = exception;
                TimeUnit.MILLISECONDS.sleep(250L);
            }
        }
        throw new IllegalStateException("MongoDB did not become available on " + DOCKER_HOST + ":" + hostPort,
                lastConnectionException);
    }

    private static MongoClient createMongoClient() {
        final MongoClientOptions options = MongoClientOptions.builder()
                .connectTimeout(CLIENT_TIMEOUT_MILLIS)
                .socketTimeout(CLIENT_TIMEOUT_MILLIS)
                .serverSelectionTimeout(CLIENT_TIMEOUT_MILLIS)
                .build();
        return new MongoClient(new ServerAddress(DOCKER_HOST, hostPort), options);
    }

    private static Map<String, Document> indexesByName() {
        final Map<String, Document> indexes = new LinkedHashMap<String, Document>();
        for (Document index : mongoClient.getDatabase(DATABASE_NAME)
                .getCollection("annotation_builder_indexed")
                .listIndexes()) {
            indexes.put(index.getString("name"), index);
        }
        return indexes;
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(null);
            return serverSocket.getLocalPort();
        }
    }

    private static void stopContainer() throws Exception {
        if (process != null && process.isAlive()) {
            new ProcessBuilder("docker", "rm", "-f", CONTAINER_NAME)
                    .redirectOutput(new File("mongo-rm-stdout.txt"))
                    .redirectError(new File("mongo-rm-stderr.txt"))
                    .start()
                    .waitFor(CLIENT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(CLIENT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Entity("annotation_builder_indexed")
    @Indexes(@Index(fields = @Field("name"), options = @IndexOptions(name = "name_ascending_index")))
    public static final class IndexedEntity {
        @Id
        private String id;
        private String name;
        @Indexed(options = @IndexOptions(name = "email_unique_index", unique = true))
        private String email;
    }
}
