/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.grpc_google_cloud_spanner_v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.ListValue;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.google.rpc.Status;
import com.google.spanner.v1.BatchCreateSessionsRequest;
import com.google.spanner.v1.BatchCreateSessionsResponse;
import com.google.spanner.v1.BatchWriteRequest;
import com.google.spanner.v1.BatchWriteResponse;
import com.google.spanner.v1.BeginTransactionRequest;
import com.google.spanner.v1.CommitRequest;
import com.google.spanner.v1.CommitResponse;
import com.google.spanner.v1.CreateSessionRequest;
import com.google.spanner.v1.DeleteSessionRequest;
import com.google.spanner.v1.ExecuteBatchDmlRequest;
import com.google.spanner.v1.ExecuteBatchDmlResponse;
import com.google.spanner.v1.ExecuteSqlRequest;
import com.google.spanner.v1.GetSessionRequest;
import com.google.spanner.v1.ListSessionsRequest;
import com.google.spanner.v1.ListSessionsResponse;
import com.google.spanner.v1.PartialResultSet;
import com.google.spanner.v1.PartitionQueryRequest;
import com.google.spanner.v1.PartitionReadRequest;
import com.google.spanner.v1.PartitionResponse;
import com.google.spanner.v1.ReadRequest;
import com.google.spanner.v1.ResultSet;
import com.google.spanner.v1.RollbackRequest;
import com.google.spanner.v1.Session;
import com.google.spanner.v1.SpannerGrpc;
import com.google.spanner.v1.Transaction;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.BlockingClientCall;
import io.grpc.stub.StreamObserver;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class Grpc_google_cloud_spanner_v1Test {
    private static final String DATABASE = "projects/test/instances/test/databases/test";
    private static final String SESSION = DATABASE + "/sessions/session-1";

    @Test
    void serviceDescriptorExposesAllPublicSpannerMethods() {
        ServiceDescriptor descriptor = SpannerGrpc.getServiceDescriptor();
        ServerServiceDefinition boundService = SpannerGrpc.bindService(new FakeSpannerService());

        assertThat(descriptor.getName()).isEqualTo(SpannerGrpc.SERVICE_NAME);
        assertThat(boundService.getServiceDescriptor().getName()).isEqualTo(SpannerGrpc.SERVICE_NAME);
        assertThat(descriptor.getSchemaDescriptor()).isNotNull();
        assertThat(boundService.getMethods()).hasSize(16);
        assertThat(descriptor.getMethods())
                .extracting(MethodDescriptor::getFullMethodName)
                .containsExactlyInAnyOrderElementsOf(expectedFullMethodNames());

        assertUnaryMethod(SpannerGrpc.getCreateSessionMethod(), "CreateSession");
        assertUnaryMethod(SpannerGrpc.getBatchCreateSessionsMethod(), "BatchCreateSessions");
        assertUnaryMethod(SpannerGrpc.getGetSessionMethod(), "GetSession");
        assertUnaryMethod(SpannerGrpc.getListSessionsMethod(), "ListSessions");
        assertUnaryMethod(SpannerGrpc.getDeleteSessionMethod(), "DeleteSession");
        assertUnaryMethod(SpannerGrpc.getExecuteSqlMethod(), "ExecuteSql");
        assertServerStreamingMethod(SpannerGrpc.getExecuteStreamingSqlMethod(), "ExecuteStreamingSql");
        assertUnaryMethod(SpannerGrpc.getExecuteBatchDmlMethod(), "ExecuteBatchDml");
        assertUnaryMethod(SpannerGrpc.getReadMethod(), "Read");
        assertServerStreamingMethod(SpannerGrpc.getStreamingReadMethod(), "StreamingRead");
        assertUnaryMethod(SpannerGrpc.getBeginTransactionMethod(), "BeginTransaction");
        assertUnaryMethod(SpannerGrpc.getCommitMethod(), "Commit");
        assertUnaryMethod(SpannerGrpc.getRollbackMethod(), "Rollback");
        assertUnaryMethod(SpannerGrpc.getPartitionQueryMethod(), "PartitionQuery");
        assertUnaryMethod(SpannerGrpc.getPartitionReadMethod(), "PartitionRead");
        assertServerStreamingMethod(SpannerGrpc.getBatchWriteMethod(), "BatchWrite");
    }

    @Test
    void methodDescriptorMarshallersRoundTripTypedMessages() throws Exception {
        CreateSessionRequest request = CreateSessionRequest.newBuilder().setDatabase(DATABASE).build();
        Session response = session("marshaller-session");

        assertThat(roundTrip(SpannerGrpc.getCreateSessionMethod().getRequestMarshaller(), request)).isEqualTo(request);
        assertThat(roundTrip(SpannerGrpc.getCreateSessionMethod().getResponseMarshaller(), response))
                .isEqualTo(response);
        assertThat(SpannerGrpc.getCreateSessionMethod()).isSameAs(SpannerGrpc.getCreateSessionMethod());
    }

    @Test
    void blockingStubInvokesEveryUnaryAndStreamingRpcAgainstInProcessService() throws Exception {
        try (GrpcFixture fixture = GrpcFixture.start()) {
            SpannerGrpc.SpannerBlockingStub stub = SpannerGrpc.newBlockingStub(fixture.channel);

            assertThat(stub.createSession(CreateSessionRequest.newBuilder().setDatabase(DATABASE).build()).getName())
                    .isEqualTo(DATABASE + "/sessions/created");
            assertThat(stub.batchCreateSessions(batchCreateSessionsRequest()).getSession(0).getCreatorRole())
                    .isEqualTo("batch:2");
            assertThat(stub.getSession(GetSessionRequest.newBuilder().setName(SESSION).build()).getName())
                    .isEqualTo(SESSION);
            assertThat(stub.listSessions(ListSessionsRequest.newBuilder().setDatabase(DATABASE).setPageSize(10).build())
                            .getSessionsList())
                    .extracting(Session::getName)
                    .containsExactly(DATABASE + "/sessions/listed");
            assertThat(stub.deleteSession(DeleteSessionRequest.newBuilder().setName(SESSION).build()))
                    .isEqualTo(Empty.getDefaultInstance());
            assertThat(stub.executeSql(sqlRequest("select 1")).getRows(0).getValues(0).getStringValue())
                    .isEqualTo("select 1");
            assertThat(toList(stub.executeStreamingSql(sqlRequest("stream sql"))))
                    .extracting(PartialResultSet::getResumeToken)
                    .containsExactly(ByteString.copyFromUtf8("sql-1"), ByteString.copyFromUtf8("sql-2"));
            assertThat(stub.executeBatchDml(batchDmlRequest()).getStatus().getMessage()).isEqualTo("dml:1");
            assertThat(stub.read(readRequest("Albums")).getRows(0).getValues(0).getStringValue()).isEqualTo("Albums");
            assertThat(toList(stub.streamingRead(readRequest("Singers"))))
                    .extracting(PartialResultSet::getResumeToken)
                    .containsExactly(ByteString.copyFromUtf8("read-1"), ByteString.copyFromUtf8("read-2"));
            assertThat(stub.beginTransaction(BeginTransactionRequest.newBuilder().setSession(SESSION).build()).getId())
                    .isEqualTo(ByteString.copyFromUtf8(SESSION));
            assertThat(stub.commit(CommitRequest.newBuilder()
                            .setSession(SESSION)
                            .setTransactionId(ByteString.copyFromUtf8("tx-1"))
                            .build())
                    .getCommitTimestamp()
                    .getSeconds())
                    .isEqualTo(123L);
            assertThat(stub.rollback(RollbackRequest.newBuilder()
                            .setSession(SESSION)
                            .setTransactionId(ByteString.copyFromUtf8("tx-1"))
                            .build()))
                    .isEqualTo(Empty.getDefaultInstance());
            assertThat(stub.partitionQuery(PartitionQueryRequest.newBuilder()
                                    .setSession(SESSION)
                                    .setSql("select *")
                                    .build())
                            .getTransaction()
                            .getId())
                    .isEqualTo(ByteString.copyFromUtf8("partition-query"));
            assertThat(stub.partitionRead(PartitionReadRequest.newBuilder()
                                    .setSession(SESSION)
                                    .setTable("Albums")
                                    .build())
                            .getTransaction()
                            .getId())
                    .isEqualTo(ByteString.copyFromUtf8("partition-read"));
            assertThat(toList(stub.batchWrite(BatchWriteRequest.newBuilder().setSession(SESSION).build())))
                    .extracting(BatchWriteResponse::getIndexesList)
                    .containsExactly(List.of(0), List.of(1));
        }
    }

    @Test
    void futureAndAsyncStubsDeliverUnaryAndStreamingResponses() throws Exception {
        try (GrpcFixture fixture = GrpcFixture.start()) {
            CreateSessionRequest request = CreateSessionRequest.newBuilder().setDatabase(DATABASE).build();
            ListenableFuture<Session> future = SpannerGrpc.newFutureStub(fixture.channel).createSession(request);

            assertThat(future.get(5, TimeUnit.SECONDS).getName()).isEqualTo(DATABASE + "/sessions/created");
            assertThat(SpannerGrpc.newBlockingV2Stub(fixture.channel).createSession(request).getName())
                    .isEqualTo(DATABASE + "/sessions/created");

            RecordingObserver<PartialResultSet> observer = new RecordingObserver<>();
            SpannerGrpc.newStub(fixture.channel).executeStreamingSql(sqlRequest("async stream"), observer);

            assertThat(observer.awaitValues()).extracting(PartialResultSet::getResumeToken)
                    .containsExactly(ByteString.copyFromUtf8("sql-1"), ByteString.copyFromUtf8("sql-2"));
            assertThat(observer.error).isNull();
        }
    }

    @Test
    void blockingV2StubReadsServerStreamingResponsesThroughBlockingClientCall() throws Exception {
        try (GrpcFixture fixture = GrpcFixture.start()) {
            SpannerGrpc.SpannerBlockingV2Stub stub = SpannerGrpc.newBlockingV2Stub(fixture.channel);

            BlockingClientCall<?, PartialResultSet> sqlCall = stub.executeStreamingSql(sqlRequest("v2 stream sql"));
            assertThat(readNext(sqlCall).getResumeToken()).isEqualTo(ByteString.copyFromUtf8("sql-1"));
            assertThat(readNext(sqlCall).getResumeToken()).isEqualTo(ByteString.copyFromUtf8("sql-2"));
            assertThat(readNext(sqlCall)).isNull();

            BlockingClientCall<?, PartialResultSet> readCall = stub.streamingRead(readRequest("Singers"));
            assertThat(readNext(readCall).getResumeToken()).isEqualTo(ByteString.copyFromUtf8("read-1"));
            assertThat(readNext(readCall).getResumeToken()).isEqualTo(ByteString.copyFromUtf8("read-2"));
            assertThat(readNext(readCall)).isNull();

            BlockingClientCall<?, BatchWriteResponse> batchWriteCall =
                    stub.batchWrite(BatchWriteRequest.newBuilder().setSession(SESSION).build());
            assertThat(readNext(batchWriteCall).getIndexesList()).containsExactly(0);
            assertThat(readNext(batchWriteCall).getIndexesList()).containsExactly(1);
            assertThat(readNext(batchWriteCall)).isNull();
        }
    }

    @Test
    void directAsyncServiceReportsUnimplementedForDefaultRpcHandlers() throws Exception {
        try (GrpcFixture fixture = GrpcFixture.start(new DefaultAsyncSpannerService())) {
            SpannerGrpc.SpannerBlockingStub stub =
                    SpannerGrpc.newBlockingStub(fixture.channel).withDeadlineAfter(5, TimeUnit.SECONDS);

            assertThatThrownBy(() -> stub.getSession(GetSessionRequest.newBuilder().setName(SESSION).build()))
                    .isInstanceOfSatisfying(StatusRuntimeException.class,
                            exception -> assertThat(exception.getStatus().getCode()).isEqualTo(Code.UNIMPLEMENTED));
        }
    }

    private static List<String> expectedFullMethodNames() {
        return List.of(
                fullMethodName("CreateSession"),
                fullMethodName("BatchCreateSessions"),
                fullMethodName("GetSession"),
                fullMethodName("ListSessions"),
                fullMethodName("DeleteSession"),
                fullMethodName("ExecuteSql"),
                fullMethodName("ExecuteStreamingSql"),
                fullMethodName("ExecuteBatchDml"),
                fullMethodName("Read"),
                fullMethodName("StreamingRead"),
                fullMethodName("BeginTransaction"),
                fullMethodName("Commit"),
                fullMethodName("Rollback"),
                fullMethodName("PartitionQuery"),
                fullMethodName("PartitionRead"),
                fullMethodName("BatchWrite"));
    }

    private static void assertUnaryMethod(MethodDescriptor<?, ?> method, String name) {
        assertThat(method.getType()).isEqualTo(MethodDescriptor.MethodType.UNARY);
        assertThat(method.getFullMethodName()).isEqualTo(fullMethodName(name));
        assertThat(method.isSampledToLocalTracing()).isTrue();
    }

    private static void assertServerStreamingMethod(MethodDescriptor<?, ?> method, String name) {
        assertThat(method.getType()).isEqualTo(MethodDescriptor.MethodType.SERVER_STREAMING);
        assertThat(method.getFullMethodName()).isEqualTo(fullMethodName(name));
        assertThat(method.isSampledToLocalTracing()).isTrue();
    }

    private static String fullMethodName(String method) {
        return MethodDescriptor.generateFullMethodName(SpannerGrpc.SERVICE_NAME, method);
    }

    private static <T extends MessageLite> T roundTrip(MethodDescriptor.Marshaller<T> marshaller, T message)
            throws Exception {
        try (InputStream input = marshaller.stream(message)) {
            return marshaller.parse(input);
        }
    }

    private static BatchCreateSessionsRequest batchCreateSessionsRequest() {
        return BatchCreateSessionsRequest.newBuilder()
                .setDatabase(DATABASE)
                .setSessionTemplate(Session.newBuilder().putLabels("purpose", "test"))
                .setSessionCount(2)
                .build();
    }

    private static ExecuteSqlRequest sqlRequest(String sql) {
        return ExecuteSqlRequest.newBuilder().setSession(SESSION).setSql(sql).build();
    }

    private static ExecuteBatchDmlRequest batchDmlRequest() {
        return ExecuteBatchDmlRequest.newBuilder()
                .setSession(SESSION)
                .addStatements(ExecuteBatchDmlRequest.Statement.newBuilder().setSql("update Albums set Title='A'"))
                .build();
    }

    private static ReadRequest readRequest(String table) {
        return ReadRequest.newBuilder().setSession(SESSION).setTable(table).addColumns("Id").build();
    }

    private static Session session(String name) {
        return Session.newBuilder().setName(name).build();
    }

    private static ResultSet singleStringRow(String value) {
        return ResultSet.newBuilder()
                .addRows(ListValue.newBuilder().addValues(Value.newBuilder().setStringValue(value)))
                .build();
    }

    private static PartialResultSet partial(String token) {
        return PartialResultSet.newBuilder().setResumeToken(ByteString.copyFromUtf8(token)).build();
    }

    private static <T> List<T> toList(Iterator<T> iterator) {
        List<T> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    private static <T> T readNext(BlockingClientCall<?, T> call) throws Exception {
        return call.read(5, TimeUnit.SECONDS);
    }

    private static final class GrpcFixture implements AutoCloseable {
        private final Server server;
        private final ManagedChannel channel;

        private GrpcFixture(Server server, ManagedChannel channel) {
            this.server = server;
            this.channel = channel;
        }

        static GrpcFixture start() throws Exception {
            String serverName = InProcessServerBuilder.generateName();
            Server server = InProcessServerBuilder.forName(serverName)
                    .directExecutor()
                    .addService(new FakeSpannerService())
                    .build()
                    .start();
            ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
            return new GrpcFixture(server, channel);
        }

        static GrpcFixture start(SpannerGrpc.AsyncService service) throws Exception {
            String serverName = InProcessServerBuilder.generateName();
            Server server = InProcessServerBuilder.forName(serverName)
                    .directExecutor()
                    .addService(SpannerGrpc.bindService(service))
                    .build()
                    .start();
            ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
            return new GrpcFixture(server, channel);
        }

        @Override
        public void close() throws Exception {
            channel.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            server.shutdownNow();
            assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class DefaultAsyncSpannerService implements SpannerGrpc.AsyncService {
    }

    private static final class FakeSpannerService extends SpannerGrpc.SpannerImplBase {
        @Override
        public void createSession(CreateSessionRequest request, StreamObserver<Session> responseObserver) {
            complete(responseObserver, session(request.getDatabase() + "/sessions/created"));
        }

        @Override
        public void batchCreateSessions(
                BatchCreateSessionsRequest request, StreamObserver<BatchCreateSessionsResponse> responseObserver) {
            Session created = Session.newBuilder().setName(request.getDatabase() + "/sessions/batch").setCreatorRole(
                    "batch:" + request.getSessionCount()).build();
            complete(responseObserver, BatchCreateSessionsResponse.newBuilder().addSession(created).build());
        }

        @Override
        public void getSession(GetSessionRequest request, StreamObserver<Session> responseObserver) {
            complete(responseObserver, session(request.getName()));
        }

        @Override
        public void listSessions(ListSessionsRequest request, StreamObserver<ListSessionsResponse> responseObserver) {
            complete(responseObserver,
                    ListSessionsResponse.newBuilder()
                            .addSessions(session(request.getDatabase() + "/sessions/listed"))
                            .build());
        }

        @Override
        public void deleteSession(DeleteSessionRequest request, StreamObserver<Empty> responseObserver) {
            complete(responseObserver, Empty.getDefaultInstance());
        }

        @Override
        public void executeSql(ExecuteSqlRequest request, StreamObserver<ResultSet> responseObserver) {
            complete(responseObserver, singleStringRow(request.getSql()));
        }

        @Override
        public void executeStreamingSql(
                ExecuteSqlRequest request, StreamObserver<PartialResultSet> responseObserver) {
            stream(responseObserver, partial("sql-1"), partial("sql-2"));
        }

        @Override
        public void executeBatchDml(
                ExecuteBatchDmlRequest request, StreamObserver<ExecuteBatchDmlResponse> responseObserver) {
            Status status = Status.newBuilder().setCode(0).setMessage("dml:" + request.getStatementsCount()).build();
            complete(responseObserver, ExecuteBatchDmlResponse.newBuilder().setStatus(status).build());
        }

        @Override
        public void read(ReadRequest request, StreamObserver<ResultSet> responseObserver) {
            complete(responseObserver, singleStringRow(request.getTable()));
        }

        @Override
        public void streamingRead(ReadRequest request, StreamObserver<PartialResultSet> responseObserver) {
            stream(responseObserver, partial("read-1"), partial("read-2"));
        }

        @Override
        public void beginTransaction(BeginTransactionRequest request, StreamObserver<Transaction> responseObserver) {
            complete(responseObserver,
                    Transaction.newBuilder().setId(ByteString.copyFromUtf8(request.getSession())).build());
        }

        @Override
        public void commit(CommitRequest request, StreamObserver<CommitResponse> responseObserver) {
            complete(responseObserver,
                    CommitResponse.newBuilder().setCommitTimestamp(Timestamp.newBuilder().setSeconds(123L)).build());
        }

        @Override
        public void rollback(RollbackRequest request, StreamObserver<Empty> responseObserver) {
            complete(responseObserver, Empty.getDefaultInstance());
        }

        @Override
        public void partitionQuery(
                PartitionQueryRequest request, StreamObserver<PartitionResponse> responseObserver) {
            complete(responseObserver, partitionResponse("partition-query"));
        }

        @Override
        public void partitionRead(PartitionReadRequest request, StreamObserver<PartitionResponse> responseObserver) {
            complete(responseObserver, partitionResponse("partition-read"));
        }

        @Override
        public void batchWrite(BatchWriteRequest request, StreamObserver<BatchWriteResponse> responseObserver) {
            stream(responseObserver,
                    BatchWriteResponse.newBuilder().addIndexes(0).build(),
                    BatchWriteResponse.newBuilder().addIndexes(1).build());
        }

        private static PartitionResponse partitionResponse(String transactionId) {
            return PartitionResponse.newBuilder()
                    .setTransaction(Transaction.newBuilder().setId(ByteString.copyFromUtf8(transactionId)))
                    .build();
        }

        private static <T> void complete(StreamObserver<T> responseObserver, T value) {
            responseObserver.onNext(value);
            responseObserver.onCompleted();
        }

        @SafeVarargs
        private static <T> void stream(StreamObserver<T> responseObserver, T... values) {
            for (T value : values) {
                responseObserver.onNext(value);
            }
            responseObserver.onCompleted();
        }
    }

    private static final class RecordingObserver<T> implements StreamObserver<T> {
        private final CountDownLatch completed = new CountDownLatch(1);
        private final List<T> values = new ArrayList<>();
        private Throwable error;

        @Override
        public void onNext(T value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
            completed.countDown();
        }

        @Override
        public void onCompleted() {
            completed.countDown();
        }

        List<T> awaitValues() throws InterruptedException {
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            return values;
        }
    }
}
