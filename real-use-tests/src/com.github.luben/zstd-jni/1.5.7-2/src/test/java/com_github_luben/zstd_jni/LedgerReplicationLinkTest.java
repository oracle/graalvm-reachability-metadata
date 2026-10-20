/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdIOException;
import com.github.luben.zstd.ZstdInputStream;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.compression.ZstdDecoder;
import io.netty.handler.codec.compression.ZstdEncoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compresses a long-lived TCP replication connection with Netty's `ZstdEncoder` and
 * `ZstdDecoder` channel codecs, which run on zstd-jni's `Zstd.compress`, `Zstd.compressBound`
 * and a continuous `ZstdInputStreamNoFinalizer`, and checks the wire with `ZstdInputStream` and
 * `Zstd.getFrameContentSize`.
 *
 * A ledger primary streams length-prefixed ledger entries to a replica in batches, flushing
 * once per batch. The encoder cuts one zstd frame each time its block buffer fills and one more
 * at every flush, so the replica's decoder must reassemble frames that TCP splits arbitrarily.
 * The replica acknowledges each commit over the same compressed link, and a peer that connects
 * without the codec is cut off with zstd's unknown-prefix error.
 */
class LedgerReplicationLinkTest {

    private static final int LEVEL = 5;
    private static final int BLOCK_SIZE = 4 * 1024;
    private static final int MAX_ENCODE_SIZE = 1024 * 1024;
    private static final int MAX_RECORD_SIZE = 64 * 1024;
    private static final int LENGTH_PREFIX = 4;
    private static final int[] BATCH_SIZES = {150, 1, 320, 75};
    private static final long TIMEOUT_SECONDS = 30;

    @Test
    void replicatesLedgerBatchesOverCompressedTcpLink() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(2);
        try {
            Replica replica = new Replica();
            Channel server = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(replica)
                    .bind(new InetSocketAddress("127.0.0.1", 0))
                    .sync()
                    .channel();
            InetSocketAddress address = (InetSocketAddress) server.localAddress();

            Primary primary = new Primary();
            Channel link = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(primary)
                    .connect(address)
                    .sync()
                    .channel();

            List<List<String>> batches = ledgerBatches();
            List<String> sent = new ArrayList<>();
            List<Integer> batchPlaintextSizes = new ArrayList<>();
            List<String> acks = new ArrayList<>();
            long expectedTotalCents = 0;
            for (List<String> batch : batches) {
                int plaintext = 0;
                for (String entry : batch) {
                    link.write(entry);
                    plaintext += LENGTH_PREFIX + entry.getBytes(StandardCharsets.UTF_8).length;
                    expectedTotalCents += amountCents(entry);
                }
                sent.addAll(batch);
                String commit = "COMMIT " + sent.size();
                plaintext += LENGTH_PREFIX + commit.length();
                batchPlaintextSizes.add(plaintext);

                CompletableFuture<String> ack = primary.expectAck();
                link.writeAndFlush(commit).sync();
                acks.add(ack.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                assertThat(acks.get(acks.size() - 1))
                        .isEqualTo("ACK " + sent.size() + " " + expectedTotalCents);
            }
            assertThat(replica.entries()).containsExactlyElementsOf(sent);

            List<byte[]> frames = primary.outboundFrames();
            assertThat(frames).hasSize(expectedFrameCount(batchPlaintextSizes));
            List<Long> frameContentSizes = new ArrayList<>();
            ByteArrayOutputStream wire = new ByteArrayOutputStream();
            ByteArrayOutputStream plaintext = new ByteArrayOutputStream();
            for (byte[] frame : frames) {
                long contentSize = Zstd.getFrameContentSize(frame);
                frameContentSizes.add(contentSize);
                byte[] content = Zstd.decompress(frame, (int) contentSize);
                assertThat(content).hasSize((int) contentSize);
                wire.write(frame);
                plaintext.write(content);
            }
            assertThat(frameContentSizes).containsExactlyElementsOf(expectedFrameSizes(batchPlaintextSizes));
            assertThat(wire.size()).isLessThan(plaintext.size() / 2);
            assertThat(replica.receivedWire()).isEqualTo(wire.toByteArray());

            byte[] streamed;
            try (InputStream in = new ZstdInputStream(new ByteArrayInputStream(wire.toByteArray()))) {
                streamed = in.readAllBytes();
            }
            assertThat(streamed).isEqualTo(plaintext.toByteArray());
            List<String> decodedRecords = lengthPrefixedRecords(streamed);
            assertThat(decodedRecords).hasSize(sent.size() + batches.size());
            assertThat(decodedRecords.stream().filter(r -> !r.startsWith("COMMIT ")).toList())
                    .containsExactlyElementsOf(sent);

            Channel intruder = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter())
                    .connect(address)
                    .sync()
                    .channel();
            intruder.writeAndFlush(Unpooled.copiedBuffer("REPLICATE FROM 0 PLEASE\n", StandardCharsets.US_ASCII))
                    .sync();
            Throwable failure = replica.failure().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(failure).isInstanceOf(DecompressionException.class);
            assertThat(failure.getCause()).isInstanceOf(ZstdIOException.class);
            assertThat(((ZstdIOException) failure.getCause()).getErrorCode()).isEqualTo(Zstd.errPrefixUnknown());
            assertThat(intruder.closeFuture().await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            assertThat(link.isActive()).isTrue();
            CompletableFuture<String> finalAck = primary.expectAck();
            link.writeAndFlush("COMMIT " + sent.size()).sync();
            assertThat(finalAck.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isEqualTo("ACK " + sent.size() + " " + expectedTotalCents);

            link.close().sync();
            server.close().sync();
        } finally {
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    /// Builds deterministic ledger entries, one batch per entry of `BATCH_SIZES`.
    private static List<List<String>> ledgerBatches() {
        Random random = new Random(10822);
        String[] memos = {"card settlement", "wire transfer", "fee reversal", "interest accrual"};
        List<List<String>> batches = new ArrayList<>();
        int sequence = 0;
        for (int size : BATCH_SIZES) {
            List<String> batch = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                sequence++;
                int cents = random.nextInt(2_000_000) - 1_000_000;
                batch.add(String.format(Locale.ROOT, "%07d|ACC-%04d|EUR|%d|%s|branch=%02d",
                        sequence, random.nextInt(300), cents, memos[random.nextInt(memos.length)],
                        random.nextInt(12)));
            }
            batches.add(batch);
        }
        return batches;
    }

    private static long amountCents(String entry) {
        return Long.parseLong(entry.split("\\|")[3]);
    }

    /// The encoder emits a full `BLOCK_SIZE` frame whenever its buffer fills and the remainder at
    /// each flush, and every flush empties the buffer.
    private static List<Long> expectedFrameSizes(List<Integer> batchPlaintextSizes) {
        List<Long> sizes = new ArrayList<>();
        for (int batch : batchPlaintextSizes) {
            for (int i = 0; i < batch / BLOCK_SIZE; i++) {
                sizes.add((long) BLOCK_SIZE);
            }
            if (batch % BLOCK_SIZE != 0) {
                sizes.add((long) (batch % BLOCK_SIZE));
            }
        }
        return sizes;
    }

    private static int expectedFrameCount(List<Integer> batchPlaintextSizes) {
        return expectedFrameSizes(batchPlaintextSizes).size();
    }

    private static List<String> lengthPrefixedRecords(byte[] plaintext) throws IOException {
        List<String> records = new ArrayList<>();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(plaintext));
        while (in.available() > 0) {
            byte[] record = new byte[in.readInt()];
            in.readFully(record);
            records.add(new String(record, StandardCharsets.UTF_8));
        }
        return records;
    }

    /// Installs the compressed, length-prefixed string codec both peers speak.
    private static void installLinkCodec(ChannelPipeline pipeline) {
        pipeline.addLast(new ZstdDecoder());
        pipeline.addLast(new ZstdEncoder(LEVEL, BLOCK_SIZE, MAX_ENCODE_SIZE));
        pipeline.addLast(new LengthFieldBasedFrameDecoder(MAX_RECORD_SIZE, 0, LENGTH_PREFIX, 0, LENGTH_PREFIX));
        pipeline.addLast(new LengthFieldPrepender(LENGTH_PREFIX));
        pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
        pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
    }

    /// The primary side: records every compressed buffer it puts on the socket and collects acks.
    private static final class Primary extends ChannelInitializer<SocketChannel> {

        private final List<byte[]> outboundFrames = new ArrayList<>();
        private CompletableFuture<String> pendingAck;

        synchronized CompletableFuture<String> expectAck() {
            pendingAck = new CompletableFuture<>();
            return pendingAck;
        }

        synchronized List<byte[]> outboundFrames() {
            return new ArrayList<>(outboundFrames);
        }

        @Override
        protected void initChannel(SocketChannel channel) {
            channel.pipeline().addLast(new ChannelDuplexHandler() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                    ByteBuf buffer = (ByteBuf) msg;
                    if (buffer.isReadable()) {
                        synchronized (Primary.this) {
                            outboundFrames.add(ByteBufUtil.getBytes(buffer));
                        }
                    }
                    ctx.write(msg, promise);
                }
            });
            installLinkCodec(channel.pipeline());
            channel.pipeline().addLast(new SimpleChannelInboundHandler<String>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, String ack) {
                    synchronized (Primary.this) {
                        pendingAck.complete(ack);
                    }
                }
            });
        }
    }

    /// The replica side: applies entries, acknowledges commits, and records raw inbound bytes.
    private static final class Replica extends ChannelInitializer<SocketChannel> {

        private final List<String> entries = new ArrayList<>();
        private final ByteArrayOutputStream receivedWire = new ByteArrayOutputStream();
        private final CompletableFuture<Throwable> failure = new CompletableFuture<>();
        private long totalCents;

        synchronized List<String> entries() {
            return new ArrayList<>(entries);
        }

        synchronized byte[] receivedWire() {
            return receivedWire.toByteArray();
        }

        CompletableFuture<Throwable> failure() {
            return failure;
        }

        @Override
        protected void initChannel(SocketChannel channel) {
            channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    byte[] bytes = ByteBufUtil.getBytes((ByteBuf) msg);
                    synchronized (Replica.this) {
                        receivedWire.write(bytes, 0, bytes.length);
                    }
                    ctx.fireChannelRead(msg);
                }
            });
            installLinkCodec(channel.pipeline());
            channel.pipeline().addLast(new SimpleChannelInboundHandler<String>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, String record) {
                    String ack = null;
                    synchronized (Replica.this) {
                        if (record.startsWith("COMMIT ")) {
                            long committed = Long.parseLong(record.substring("COMMIT ".length()));
                            if (committed == entries.size()) {
                                ack = "ACK " + entries.size() + " " + totalCents;
                            } else {
                                ack = "NACK " + entries.size();
                            }
                        } else {
                            entries.add(record);
                            totalCents += amountCents(record);
                        }
                    }
                    if (ack != null) {
                        ctx.writeAndFlush(ack);
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    failure.complete(cause);
                    ctx.close();
                }
            });
        }
    }
}
