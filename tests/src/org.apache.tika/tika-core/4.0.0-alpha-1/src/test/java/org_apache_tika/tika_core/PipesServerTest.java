/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.PipesServer;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;

public class PipesServerTest {

    private static final Duration IO_TIMEOUT = Duration.ofSeconds(30);

    @TempDir
    private Path tempDir;

    @Test
    public void processRequestsReadsTupleAndWritesIntermediateAndFinalResults()
            throws Exception {
        Path inputFile = tempDir.resolve("server-input.txt");
        Files.writeString(
                inputFile, "hello from the in-process pipes server", StandardCharsets.UTF_8);
        Path tikaConfig = writePipesConfig();

        PipedInputStream testReadsFromServer = new PipedInputStream(64 * 1024);
        PipedOutputStream serverOutput = new PipedOutputStream(testReadsFromServer);
        PipedInputStream serverInput = new PipedInputStream(64 * 1024);
        PipedOutputStream testWritesToServer = new PipedOutputStream(serverInput);

        PipesServer server = new PipesServer(
                tikaConfig,
                serverInput,
                new PrintStream(serverOutput, true, StandardCharsets.UTF_8),
                -1L,
                10_000L,
                0L);
        Thread serverThread = new Thread(server::processRequests, "tika-pipes-server-test");
        serverThread.setDaemon(true);
        serverThread.start();

        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tika-pipes-server-reader-test");
            thread.setDaemon(true);
            return thread;
        });
        try {
            DataInputStream fromServer = new DataInputStream(testReadsFromServer);
            DataOutputStream toServer = new DataOutputStream(testWritesToServer);

            assertThat(readStatus(fromServer, executor))
                    .isEqualTo(PipesServer.STATUS.READY);

            FetchEmitTuple tuple = new FetchEmitTuple(
                    "server-task-1",
                    new FetchKey("fs", inputFile.toString()),
                    new EmitKey("unused", "server-result"));
            writeCall(toServer, tuple);

            ServerMessage intermediate = readMessage(fromServer, executor);
            assertThat(intermediate.status()).isEqualTo(PipesServer.STATUS.INTERMEDIATE_RESULT);
            Metadata intermediateMetadata = (Metadata) deserialize(intermediate.payload());
            assertThat(intermediateMetadata.get(Metadata.CONTENT_TYPE)).isNotBlank();

            ServerMessage finalMessage = readMessage(fromServer, executor);
            assertThat(finalMessage.status()).isEqualTo(PipesServer.STATUS.PARSE_SUCCESS);
            EmitData emitData = (EmitData) deserialize(finalMessage.payload());
            assertThat(emitData.getEmitKey()).isEqualTo(new EmitKey("unused", "server-result"));
            assertThat(emitData.getMetadataList()).hasSize(1);
            Metadata metadata = emitData.getMetadataList().get(0);
            assertThat(metadata.get(TikaCoreProperties.SOURCE_PATH))
                    .isEqualTo(inputFile.toString());
            assertThat(metadata.get(Metadata.CONTENT_TYPE)).isNotBlank();
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            testReadsFromServer.close();
        }
    }

    private Path writePipesConfig() throws Exception {
        Path tikaConfig = tempDir.resolve("tika-pipes-server.xml");
        String xml = """
                <properties>
                  <fetchers>
                    <fetcher class="org.apache.tika.pipes.fetcher.fs.FileSystemFetcher">
                      <name>fs</name>
                      <allowAbsolutePaths>true</allowAbsolutePaths>
                    </fetcher>
                  </fetchers>
                </properties>
                """;
        Files.writeString(tikaConfig, xml, StandardCharsets.UTF_8);
        return tikaConfig;
    }

    private static void writeCall(DataOutputStream toServer, FetchEmitTuple tuple)
            throws Exception {
        toServer.writeByte(statusByte(PipesServer.STATUS.CALL));
        byte[] serializedTuple = serialize(tuple);
        toServer.writeInt(serializedTuple.length);
        toServer.write(serializedTuple);
        toServer.flush();
    }

    private static PipesServer.STATUS readStatus(DataInputStream input, ExecutorService executor)
            throws Exception {
        return readWithTimeout(() -> PipesServer.STATUS.lookup(input.readUnsignedByte()), executor);
    }

    private static ServerMessage readMessage(DataInputStream input, ExecutorService executor)
            throws Exception {
        return readWithTimeout(() -> {
            PipesServer.STATUS status = PipesServer.STATUS.lookup(input.readUnsignedByte());
            int length = input.readInt();
            byte[] payload = new byte[length];
            input.readFully(payload);
            return new ServerMessage(status, payload);
        }, executor);
    }

    private static <T> T readWithTimeout(Callable<T> callable, ExecutorService executor)
            throws Exception {
        Future<T> future = executor.submit(callable);
        return future.get(IO_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(output)) {
            objectOutputStream.writeObject(value);
        }
        return output.toByteArray();
    }

    private static Object deserialize(byte[] value) throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(value))) {
            return objectInputStream.readObject();
        }
    }

    private static int statusByte(PipesServer.STATUS status) {
        return status.ordinal() + 1;
    }

    private record ServerMessage(PipesServer.STATUS status, byte[] payload) {
    }
}
