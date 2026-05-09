/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_mapreduce_client_shuffle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FadvisedChunkedFile;
import org.apache.hadoop.mapred.FadvisedFileRegion;
import org.apache.hadoop.mapred.ShuffleHandler;
import org.apache.hadoop.mapreduce.security.token.JobTokenIdentifier;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.jboss.netty.buffer.ChannelBuffer;
import org.junit.jupiter.api.Test;

public class Hadoop_mapreduce_client_shuffleTest {
    @Test
    void metadataSerializationRoundTripsPortNumbers() throws Exception {
        int[] ports = {0, 1, ShuffleHandler.DEFAULT_SHUFFLE_PORT, 65535, Integer.MAX_VALUE};

        for (int port : ports) {
            ByteBuffer serialized = ShuffleHandler.serializeMetaData(port);

            assertThat(serialized.remaining()).isEqualTo(Integer.BYTES);
            assertThat(ShuffleHandler.deserializeMetaData(serialized.duplicate())).isEqualTo(port);
        }
    }

    @Test
    void serviceDataSerializationWritesCompleteJobToken() throws Exception {
        byte[] identifier = "job_0001".getBytes(StandardCharsets.UTF_8);
        byte[] password = "shuffle-secret".getBytes(StandardCharsets.UTF_8);
        Text kind = new Text("mapreduce.job");
        Text service = new Text("shuffle-service");
        Token<JobTokenIdentifier> token = new Token<>(identifier, password, kind, service);

        ByteBuffer serialized = ShuffleHandler.serializeServiceData(token);
        byte[] tokenBytes = new byte[serialized.remaining()];
        serialized.get(tokenBytes);
        Token<JobTokenIdentifier> decoded = new Token<>();
        decoded.readFields(new DataInputStream(new ByteArrayInputStream(tokenBytes)));

        assertThat(decoded.getIdentifier()).isEqualTo(identifier);
        assertThat(decoded.getPassword()).isEqualTo(password);
        assertThat(decoded.getKind()).isEqualTo(kind);
        assertThat(decoded.getService()).isEqualTo(service);
    }

    @Test
    void chunkedFileStreamsFileInConfiguredChunkSizes() throws Exception {
        Path file = writeTempFile("abcdefghij");

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file.toFile(), "r")) {
            FadvisedChunkedFile chunkedFile = new FadvisedChunkedFile(
                    randomAccessFile,
                    0,
                    randomAccessFile.length(),
                    4,
                    false,
                    0,
                    null,
                    "chunked-test");
            try {
                assertThat(readChunk(chunkedFile.nextChunk())).isEqualTo("abcd");
                assertThat(readChunk(chunkedFile.nextChunk())).isEqualTo("efgh");
                assertThat(readChunk(chunkedFile.nextChunk())).isEqualTo("ij");
                assertThat(chunkedFile.isEndOfInput()).isTrue();
                assertThat(chunkedFile.nextChunk()).isNull();
            } finally {
                chunkedFile.close();
            }
        }
    }

    @Test
    void chunkedFileStreamsOnlyRequestedRangeFromOffset() throws Exception {
        Path file = writeTempFile("0123456789abcdef");

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file.toFile(), "r")) {
            FadvisedChunkedFile chunkedFile = new FadvisedChunkedFile(
                    randomAccessFile,
                    4,
                    5,
                    16,
                    false,
                    0,
                    null,
                    "offset-chunked-test");
            try {
                assertThat(readChunk(chunkedFile.nextChunk())).isEqualTo("45678");
                assertThat(chunkedFile.isEndOfInput()).isTrue();
                assertThat(chunkedFile.nextChunk()).isNull();
            } finally {
                chunkedFile.close();
            }
        }
    }

    @Test
    void fileRegionTransfersRequestedRangeWithCustomShuffleBuffer() throws Exception {
        Path file = writeTempFile("abcdefghijklmnopqrstuvwxyz");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file.toFile(), "r")) {
            FadvisedFileRegion region = new FadvisedFileRegion(
                    randomAccessFile,
                    2,
                    9,
                    false,
                    0,
                    null,
                    "region-test",
                    4,
                    false);
            try {
                long transferred = region.transferTo(Channels.newChannel(output), 0);

                assertThat(transferred).isEqualTo(9);
                assertThat(new String(output.toByteArray(), StandardCharsets.UTF_8)).isEqualTo("cdefghijk");
                region.transferSuccessful();
            } finally {
                region.releaseExternalResources();
            }
        }
    }

    @Test
    void fileRegionTransfersFromRelativePositionAndRejectsOutOfRangePositions() throws Exception {
        Path file = writeTempFile("0123456789abcdef");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file.toFile(), "r")) {
            FadvisedFileRegion region = new FadvisedFileRegion(
                    randomAccessFile,
                    3,
                    8,
                    false,
                    0,
                    null,
                    "relative-region-test",
                    3,
                    false);
            try {
                long transferred = region.transferTo(Channels.newChannel(output), 2);

                assertThat(transferred).isEqualTo(6);
                assertThat(new String(output.toByteArray(), StandardCharsets.UTF_8)).isEqualTo("56789a");
                assertThat(region.transferTo(Channels.newChannel(new ByteArrayOutputStream()), 8)).isZero();
                assertThatThrownBy(() -> region.transferTo(Channels.newChannel(new ByteArrayOutputStream()), -1))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("position out of range");
                assertThatThrownBy(() -> region.transferTo(Channels.newChannel(new ByteArrayOutputStream()), 9))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("position out of range");
            } finally {
                region.releaseExternalResources();
            }
        }
    }

    @Test
    void fileRegionUsesZeroCopyTransferWhenAllowed() throws Exception {
        Path file = writeTempFile("zero-copy-transfer");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file.toFile(), "r")) {
            FadvisedFileRegion region = new FadvisedFileRegion(
                    randomAccessFile,
                    5,
                    4,
                    false,
                    0,
                    null,
                    "zero-copy-region-test",
                    2,
                    true);
            try {
                long transferred = region.transferTo(Channels.newChannel(output), 0);

                assertThat(transferred).isEqualTo(4);
                assertThat(new String(output.toByteArray(), StandardCharsets.UTF_8)).isEqualTo("copy");
            } finally {
                region.releaseExternalResources();
            }
        }
    }

    @Test
    void shuffleHandlerStartsOnEphemeralPortAndPublishesMetadata() throws Exception {
        Configuration configuration = new Configuration(false);
        configuration.setBoolean(YarnConfiguration.NM_RECOVERY_ENABLED, false);
        configuration.setInt(ShuffleHandler.SHUFFLE_PORT_CONFIG_KEY, 0);
        configuration.setInt(ShuffleHandler.MAX_SHUFFLE_THREADS, 1);
        configuration.setInt(ShuffleHandler.SHUFFLE_CONNECTION_KEEP_ALIVE_TIME_OUT, 1);
        configuration.setInt(ShuffleHandler.SHUFFLE_MAPOUTPUT_META_INFO_CACHE_SIZE, 1);

        ShuffleHandler handler = new ShuffleHandler();
        try {
            handler.init(configuration);
            handler.start();

            int publishedPort = ShuffleHandler.deserializeMetaData(handler.getMetaData());

            assertThat(publishedPort).isPositive();
            assertThat(handler.getConfig().getInt(ShuffleHandler.SHUFFLE_PORT_CONFIG_KEY, -1)).isEqualTo(publishedPort);
        } finally {
            handler.stop();
        }
    }

    private static Path writeTempFile(String contents) throws IOException {
        Path file = Files.createTempFile("shuffle-test", ".data");
        Files.write(file, contents.getBytes(StandardCharsets.UTF_8));
        file.toFile().deleteOnExit();
        return file;
    }

    private static String readChunk(Object chunk) {
        assertThat(chunk).isInstanceOf(ChannelBuffer.class);
        ChannelBuffer buffer = (ChannelBuffer) chunk;
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
