/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the HTTP/2 concurrent-stream limit of the grpc-netty transport, set with
 * `maxConcurrentCallsPerConnection` on `NettyServerBuilder` and advertised to the client as
 * `SETTINGS_MAX_CONCURRENT_STREAMS`.
 *
 * A print server runs at most two jobs per printer connection. The `NettyChannelBuilder`
 * channel buffers every further job on the client until a running job completes or is
 * cancelled, then sends the buffered jobs in the order they were submitted. A running job
 * cancelled by the client is reset on the server and releases its slot, a job cancelled while
 * still buffered never reaches the server, and a second connection gets its own two slots.
 */
class PrintQueueStreamLimitTest {

    private static final String SERVICE = "printing.PrintFloor";

    private static final int JOBS_PER_CONNECTION = 2;

    private static final long WAIT_SECONDS = 10;

    private static final MethodDescriptor<StringValue, StringValue> PRINT =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Print"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    private final PrintFloor floor = new PrintFloor();

    private final List<ManagedChannel> channels = new ArrayList<>();

    private Server server;

    @BeforeEach
    void startPrintServer() throws Exception {
        server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .maxConcurrentCallsPerConnection(JOBS_PER_CONNECTION)
                .addService(floor.bindService())
                .build()
                .start();
    }

    @AfterEach
    void stopPrintServer() throws Exception {
        for (ManagedChannel channel : channels) {
            channel.shutdownNow();
            assertThat(channel.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
        server.shutdownNow();
        assertThat(server.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void jobsBeyondTheLimitWaitOnTheClientAndRunInSubmissionOrder() throws Exception {
        ManagedChannel printer = connectedPrinter("lobby");
        Map<String, ListenableFuture<StringValue>> jobs = new LinkedHashMap<>();
        for (String job : List.of("invoice-1", "invoice-2", "invoice-3", "invoice-4", "invoice-5")) {
            jobs.put(job, submit(printer, job));
        }

        floor.awaitRunning("invoice-1", "invoice-2");
        floor.finish("invoice-1");
        floor.awaitRunning("invoice-2", "invoice-3");
        floor.finish("invoice-3");
        floor.awaitRunning("invoice-2", "invoice-4");
        floor.finish("invoice-2");
        floor.awaitRunning("invoice-4", "invoice-5");
        floor.finish("invoice-5");
        floor.awaitRunning("invoice-4");
        floor.finish("invoice-4");

        for (Map.Entry<String, ListenableFuture<StringValue>> job : jobs.entrySet()) {
            assertThat(job.getValue().get(WAIT_SECONDS, TimeUnit.SECONDS).getValue())
                    .isEqualTo("printed " + job.getKey());
        }
        assertThat(floor.started()).hasSize(6).startsWith("test-page-lobby")
                .endsWith("invoice-3", "invoice-4", "invoice-5");
        assertThat(floor.mostRunningAtOnce()).isEqualTo(JOBS_PER_CONNECTION);
        assertThat(floor.log()).containsSubsequence("finish invoice-1", "start invoice-3",
                "finish invoice-3", "start invoice-4", "finish invoice-2", "start invoice-5");
    }

    @Test
    void cancelledJobsReleaseTheirSlotAndABufferedJobNeverReachesTheServer() throws Exception {
        ManagedChannel printer = connectedPrinter("office");
        ListenableFuture<StringValue> poster = submit(printer, "poster");
        ListenableFuture<StringValue> memo = submit(printer, "memo");
        ListenableFuture<StringValue> report = submit(printer, "report");
        ListenableFuture<StringValue> draft = submit(printer, "draft");
        ListenableFuture<StringValue> letter = submit(printer, "letter");
        floor.awaitRunning("poster", "memo");

        assertThat(draft.cancel(true)).isTrue();
        assertThat(poster.cancel(true)).isTrue();
        floor.awaitCancelled("poster");

        floor.finish("memo");
        floor.awaitRunning("report", "letter");
        floor.finish("report");
        floor.finish("letter");

        assertThat(memo.get(WAIT_SECONDS, TimeUnit.SECONDS).getValue()).isEqualTo("printed memo");
        assertThat(report.get(WAIT_SECONDS, TimeUnit.SECONDS).getValue()).isEqualTo("printed report");
        assertThat(letter.get(WAIT_SECONDS, TimeUnit.SECONDS).getValue()).isEqualTo("printed letter");
        assertThat(poster.isCancelled()).isTrue();
        assertThat(draft.isCancelled()).isTrue();
        assertThat(floor.cancelled()).containsExactly("poster");
        assertThat(floor.started()).containsExactlyInAnyOrder(
                "test-page-office", "poster", "memo", "report", "letter");
        assertThat(floor.mostRunningAtOnce()).isEqualTo(JOBS_PER_CONNECTION);
    }

    @Test
    void eachPrinterConnectionGetsItsOwnSlots() throws Exception {
        ManagedChannel lobby = connectedPrinter("lobby");
        ManagedChannel annex = connectedPrinter("annex");
        ListenableFuture<StringValue> badge = submit(lobby, "badge");
        ListenableFuture<StringValue> map = submit(lobby, "map");
        ListenableFuture<StringValue> agenda = submit(lobby, "agenda");
        ListenableFuture<StringValue> blueprint = submit(annex, "blueprint");
        ListenableFuture<StringValue> schedule = submit(annex, "schedule");

        floor.awaitRunning("badge", "map", "blueprint", "schedule");
        assertThat(floor.mostRunningAtOnce()).isEqualTo(2 * JOBS_PER_CONNECTION);

        floor.finish("blueprint");
        assertThat(blueprint.get(WAIT_SECONDS, TimeUnit.SECONDS).getValue()).isEqualTo("printed blueprint");
        floor.finish("badge");
        floor.awaitRunning("map", "agenda", "schedule");
        floor.finish("map");
        floor.finish("agenda");
        floor.finish("schedule");

        assertThat(badge.get(WAIT_SECONDS, TimeUnit.SECONDS).getValue()).isEqualTo("printed badge");
        assertThat(map.get(WAIT_SECONDS, TimeUnit.SECONDS).getValue()).isEqualTo("printed map");
        assertThat(agenda.get(WAIT_SECONDS, TimeUnit.SECONDS).getValue()).isEqualTo("printed agenda");
        assertThat(schedule.get(WAIT_SECONDS, TimeUnit.SECONDS).getValue()).isEqualTo("printed schedule");
        assertThat(floor.mostRunningAtOnce()).isEqualTo(2 * JOBS_PER_CONNECTION);
        assertThat(floor.log()).containsSubsequence("finish blueprint", "finish badge", "start agenda");
    }

    /// Opens a printer channel and prints a test page on it, so the connection is ready and
    /// later jobs are sent on it in the order the application submits them.
    private ManagedChannel connectedPrinter(String name) throws Exception {
        ManagedChannel channel = NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                .usePlaintext()
                .build();
        channels.add(channel);
        String testPage = "test-page-" + name;
        ListenableFuture<StringValue> printed = submit(channel, testPage);
        floor.awaitStarted(testPage);
        floor.finish(testPage);
        assertThat(printed.get(WAIT_SECONDS, TimeUnit.SECONDS).getValue()).isEqualTo("printed " + testPage);
        return channel;
    }

    private static ListenableFuture<StringValue> submit(ManagedChannel channel, String job) {
        return ClientCalls.futureUnaryCall(
                channel.newCall(PRINT, CallOptions.DEFAULT.withDeadlineAfter(60, TimeUnit.SECONDS)),
                StringValue.newBuilder().setValue(job).build());
    }

    /**
     * Holds every job that reaches the server until the test finishes it, recording when jobs
     * start, finish and are cancelled by their clients.
     */
    private static final class PrintFloor {

        private final Map<String, ServerCallStreamObserver<StringValue>> running = new LinkedHashMap<>();

        private final List<String> started = new ArrayList<>();

        private final List<String> cancelled = new ArrayList<>();

        private final List<String> log = new ArrayList<>();

        private int mostRunningAtOnce;

        ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE)
                    .addMethod(PRINT, ServerCalls.asyncUnaryCall(this::print))
                    .build();
        }

        private synchronized void print(StringValue request, StreamObserver<StringValue> response) {
            String job = request.getValue();
            ServerCallStreamObserver<StringValue> call = (ServerCallStreamObserver<StringValue>) response;
            call.setOnCancelHandler(() -> jobCancelled(job));
            running.put(job, call);
            started.add(job);
            log.add("start " + job);
            mostRunningAtOnce = Math.max(mostRunningAtOnce, running.size());
            notifyAll();
        }

        private synchronized void jobCancelled(String job) {
            running.remove(job);
            cancelled.add(job);
            log.add("cancel " + job);
            notifyAll();
        }

        void finish(String job) {
            ServerCallStreamObserver<StringValue> call;
            synchronized (this) {
                call = running.remove(job);
                assertThat(call).as("running job %s", job).isNotNull();
                log.add("finish " + job);
            }
            call.onNext(StringValue.newBuilder().setValue("printed " + job).build());
            call.onCompleted();
        }

        void awaitStarted(String job) throws InterruptedException {
            await(() -> started.contains(job), "job " + job + " to start");
        }

        void awaitCancelled(String job) throws InterruptedException {
            await(() -> cancelled.contains(job), "job " + job + " to be cancelled");
        }

        void awaitRunning(String... jobs) throws InterruptedException {
            Set<String> expected = new LinkedHashSet<>(List.of(jobs));
            await(() -> running.keySet().equals(expected), "running jobs " + expected);
        }

        synchronized List<String> started() {
            return List.copyOf(started);
        }

        synchronized List<String> cancelled() {
            return List.copyOf(cancelled);
        }

        synchronized List<String> log() {
            return List.copyOf(log);
        }

        synchronized int mostRunningAtOnce() {
            return mostRunningAtOnce;
        }

        private synchronized void await(BooleanSupplier condition, String what) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            while (!condition.getAsBoolean()) {
                long remaining = deadline - System.nanoTime();
                assertThat(remaining).as("waiting for %s, running %s", what, running.keySet()).isPositive();
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }
        }
    }
}
