/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.grpc_google_cloud_spanner_admin_database_v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.iam.v1.GetIamPolicyRequest;
import com.google.iam.v1.Policy;
import com.google.iam.v1.SetIamPolicyRequest;
import com.google.iam.v1.TestIamPermissionsRequest;
import com.google.iam.v1.TestIamPermissionsResponse;
import com.google.longrunning.Operation;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Empty;
import com.google.protobuf.FieldMask;
import com.google.protobuf.MessageLite;
import com.google.rpc.Status;
import com.google.spanner.admin.database.v1.AddSplitPointsRequest;
import com.google.spanner.admin.database.v1.AddSplitPointsResponse;
import com.google.spanner.admin.database.v1.Backup;
import com.google.spanner.admin.database.v1.BackupSchedule;
import com.google.spanner.admin.database.v1.CopyBackupRequest;
import com.google.spanner.admin.database.v1.CreateBackupRequest;
import com.google.spanner.admin.database.v1.CreateBackupScheduleRequest;
import com.google.spanner.admin.database.v1.CreateDatabaseRequest;
import com.google.spanner.admin.database.v1.Database;
import com.google.spanner.admin.database.v1.DatabaseAdminGrpc;
import com.google.spanner.admin.database.v1.DatabaseDialect;
import com.google.spanner.admin.database.v1.DatabaseRole;
import com.google.spanner.admin.database.v1.DeleteBackupRequest;
import com.google.spanner.admin.database.v1.DeleteBackupScheduleRequest;
import com.google.spanner.admin.database.v1.DropDatabaseRequest;
import com.google.spanner.admin.database.v1.GetBackupRequest;
import com.google.spanner.admin.database.v1.GetBackupScheduleRequest;
import com.google.spanner.admin.database.v1.GetDatabaseDdlRequest;
import com.google.spanner.admin.database.v1.GetDatabaseDdlResponse;
import com.google.spanner.admin.database.v1.GetDatabaseRequest;
import com.google.spanner.admin.database.v1.InternalUpdateGraphOperationRequest;
import com.google.spanner.admin.database.v1.InternalUpdateGraphOperationResponse;
import com.google.spanner.admin.database.v1.ListBackupOperationsRequest;
import com.google.spanner.admin.database.v1.ListBackupOperationsResponse;
import com.google.spanner.admin.database.v1.ListBackupSchedulesRequest;
import com.google.spanner.admin.database.v1.ListBackupSchedulesResponse;
import com.google.spanner.admin.database.v1.ListBackupsRequest;
import com.google.spanner.admin.database.v1.ListBackupsResponse;
import com.google.spanner.admin.database.v1.ListDatabaseOperationsRequest;
import com.google.spanner.admin.database.v1.ListDatabaseOperationsResponse;
import com.google.spanner.admin.database.v1.ListDatabaseRolesRequest;
import com.google.spanner.admin.database.v1.ListDatabaseRolesResponse;
import com.google.spanner.admin.database.v1.ListDatabasesRequest;
import com.google.spanner.admin.database.v1.ListDatabasesResponse;
import com.google.spanner.admin.database.v1.RestoreDatabaseRequest;
import com.google.spanner.admin.database.v1.UpdateBackupRequest;
import com.google.spanner.admin.database.v1.UpdateBackupScheduleRequest;
import com.google.spanner.admin.database.v1.UpdateDatabaseDdlRequest;
import com.google.spanner.admin.database.v1.UpdateDatabaseRequest;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.protobuf.ProtoFileDescriptorSupplier;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import io.grpc.protobuf.ProtoServiceDescriptorSupplier;
import io.grpc.stub.StreamObserver;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class Grpc_google_cloud_spanner_admin_database_v1Test {
    private static final String INSTANCE = "projects/test-project/instances/test-instance";
    private static final String DATABASE = INSTANCE + "/databases/test-database";
    private static final String BACKUP = INSTANCE + "/backups/test-backup";
    private static final String BACKUP_SCHEDULE = DATABASE + "/backupSchedules/nightly";
    private static final String DATABASE_ROLE = DATABASE + "/databaseRoles/reader";

    @Test
    void serviceDescriptorExposesAllDatabaseAdminMethods() {
        ServiceDescriptor descriptor = DatabaseAdminGrpc.getServiceDescriptor();
        ServerServiceDefinition boundService = DatabaseAdminGrpc.bindService(new FakeDatabaseAdminService());

        assertThat(descriptor.getName()).isEqualTo(DatabaseAdminGrpc.SERVICE_NAME);
        assertThat(boundService.getServiceDescriptor().getName()).isEqualTo(DatabaseAdminGrpc.SERVICE_NAME);
        assertThat(descriptor.getSchemaDescriptor()).isNotNull();
        assertThat(boundService.getMethods()).hasSize(27);
        assertThat(descriptor.getMethods())
                .extracting(MethodDescriptor::getFullMethodName)
                .containsExactlyInAnyOrderElementsOf(expectedFullMethodNames());

        assertUnaryMethod(DatabaseAdminGrpc.getListDatabasesMethod(), "ListDatabases");
        assertUnaryMethod(DatabaseAdminGrpc.getCreateDatabaseMethod(), "CreateDatabase");
        assertUnaryMethod(DatabaseAdminGrpc.getGetDatabaseMethod(), "GetDatabase");
        assertUnaryMethod(DatabaseAdminGrpc.getUpdateDatabaseMethod(), "UpdateDatabase");
        assertUnaryMethod(DatabaseAdminGrpc.getUpdateDatabaseDdlMethod(), "UpdateDatabaseDdl");
        assertUnaryMethod(DatabaseAdminGrpc.getDropDatabaseMethod(), "DropDatabase");
        assertUnaryMethod(DatabaseAdminGrpc.getGetDatabaseDdlMethod(), "GetDatabaseDdl");
        assertUnaryMethod(DatabaseAdminGrpc.getSetIamPolicyMethod(), "SetIamPolicy");
        assertUnaryMethod(DatabaseAdminGrpc.getGetIamPolicyMethod(), "GetIamPolicy");
        assertUnaryMethod(DatabaseAdminGrpc.getTestIamPermissionsMethod(), "TestIamPermissions");
        assertUnaryMethod(DatabaseAdminGrpc.getCreateBackupMethod(), "CreateBackup");
        assertUnaryMethod(DatabaseAdminGrpc.getCopyBackupMethod(), "CopyBackup");
        assertUnaryMethod(DatabaseAdminGrpc.getGetBackupMethod(), "GetBackup");
        assertUnaryMethod(DatabaseAdminGrpc.getUpdateBackupMethod(), "UpdateBackup");
        assertUnaryMethod(DatabaseAdminGrpc.getDeleteBackupMethod(), "DeleteBackup");
        assertUnaryMethod(DatabaseAdminGrpc.getListBackupsMethod(), "ListBackups");
        assertUnaryMethod(DatabaseAdminGrpc.getRestoreDatabaseMethod(), "RestoreDatabase");
        assertUnaryMethod(DatabaseAdminGrpc.getListDatabaseOperationsMethod(), "ListDatabaseOperations");
        assertUnaryMethod(DatabaseAdminGrpc.getListBackupOperationsMethod(), "ListBackupOperations");
        assertUnaryMethod(DatabaseAdminGrpc.getListDatabaseRolesMethod(), "ListDatabaseRoles");
        assertUnaryMethod(DatabaseAdminGrpc.getAddSplitPointsMethod(), "AddSplitPoints");
        assertUnaryMethod(DatabaseAdminGrpc.getCreateBackupScheduleMethod(), "CreateBackupSchedule");
        assertUnaryMethod(DatabaseAdminGrpc.getGetBackupScheduleMethod(), "GetBackupSchedule");
        assertUnaryMethod(DatabaseAdminGrpc.getUpdateBackupScheduleMethod(), "UpdateBackupSchedule");
        assertUnaryMethod(DatabaseAdminGrpc.getDeleteBackupScheduleMethod(), "DeleteBackupSchedule");
        assertUnaryMethod(DatabaseAdminGrpc.getListBackupSchedulesMethod(), "ListBackupSchedules");
        assertUnaryMethod(DatabaseAdminGrpc.getInternalUpdateGraphOperationMethod(), "InternalUpdateGraphOperation");
    }

    @Test
    void protobufSchemaDescriptorsExposeDatabaseAdminDefinitions() {
        ProtoFileDescriptorSupplier fileSupplier =
                (ProtoFileDescriptorSupplier) DatabaseAdminGrpc.getServiceDescriptor().getSchemaDescriptor();
        ProtoServiceDescriptorSupplier serviceSupplier =
                (ProtoServiceDescriptorSupplier) DatabaseAdminGrpc.getServiceDescriptor().getSchemaDescriptor();
        ProtoMethodDescriptorSupplier methodSupplier =
                (ProtoMethodDescriptorSupplier) DatabaseAdminGrpc.getGetDatabaseMethod().getSchemaDescriptor();

        Descriptors.FileDescriptor fileDescriptor = fileSupplier.getFileDescriptor();
        Descriptors.ServiceDescriptor serviceDescriptor = serviceSupplier.getServiceDescriptor();
        Descriptors.MethodDescriptor methodDescriptor = methodSupplier.getMethodDescriptor();

        assertThat(fileDescriptor.getServices()).contains(serviceDescriptor);
        assertThat(serviceDescriptor.getFullName()).isEqualTo(DatabaseAdminGrpc.SERVICE_NAME);
        assertThat(serviceDescriptor.getMethods())
                .extracting(Descriptors.MethodDescriptor::getName)
                .containsExactlyInAnyOrderElementsOf(expectedSchemaMethodNames());
        assertThat(methodDescriptor.getName()).isEqualTo("GetDatabase");
        assertThat(methodDescriptor.getInputType().getFullName())
                .isEqualTo(GetDatabaseRequest.getDescriptor().getFullName());
        assertThat(methodDescriptor.getOutputType().getFullName()).isEqualTo(Database.getDescriptor().getFullName());
    }

    @Test
    void methodDescriptorMarshallersRoundTripTypedMessages() throws Exception {
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getListDatabasesMethod(),
                ListDatabasesRequest.newBuilder().setParent(INSTANCE).build(),
                ListDatabasesResponse.newBuilder().addDatabases(database(DATABASE)).setNextPageToken("next-db").build());
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getCreateDatabaseMethod(),
                createDatabaseRequest(),
                operation("operations/create-database"));
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getGetDatabaseMethod(),
                GetDatabaseRequest.newBuilder().setName(DATABASE).build(),
                database(DATABASE));
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getUpdateDatabaseMethod(),
                updateDatabaseRequest(),
                operation("operations/update-database"));
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getUpdateDatabaseDdlMethod(),
                updateDatabaseDdlRequest(),
                operation("operations/update-ddl"));
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getDropDatabaseMethod(),
                DropDatabaseRequest.newBuilder().setDatabase(DATABASE).build(),
                Empty.getDefaultInstance());
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getGetDatabaseDdlMethod(),
                GetDatabaseDdlRequest.newBuilder().setDatabase(DATABASE).build(),
                databaseDdl());
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getSetIamPolicyMethod(),
                SetIamPolicyRequest.newBuilder().setResource(DATABASE).setPolicy(policy()).build(),
                policy());
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getGetIamPolicyMethod(),
                GetIamPolicyRequest.newBuilder().setResource(DATABASE).build(),
                policy());
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getTestIamPermissionsMethod(),
                TestIamPermissionsRequest.newBuilder()
                        .setResource(DATABASE)
                        .addPermissions("spanner.databases.get")
                        .build(),
                TestIamPermissionsResponse.newBuilder().addPermissions("spanner.databases.get").build());
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getCreateBackupMethod(),
                createBackupRequest(),
                operation("operations/create-backup"));
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getCopyBackupMethod(),
                CopyBackupRequest.newBuilder().setParent(INSTANCE).setBackupId("copy").setSourceBackup(BACKUP).build(),
                operation("operations/copy-backup"));
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getGetBackupMethod(),
                GetBackupRequest.newBuilder().setName(BACKUP).build(),
                backup(BACKUP));
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getUpdateBackupMethod(),
                UpdateBackupRequest.newBuilder()
                        .setBackup(backup(BACKUP))
                        .setUpdateMask(FieldMask.newBuilder().addPaths("expire_time"))
                        .build(),
                backup(BACKUP));
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getDeleteBackupMethod(),
                DeleteBackupRequest.newBuilder().setName(BACKUP).build(),
                Empty.getDefaultInstance());
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getListBackupsMethod(),
                ListBackupsRequest.newBuilder().setParent(INSTANCE).build(),
                ListBackupsResponse.newBuilder().addBackups(backup(BACKUP)).setNextPageToken("next-backup").build());
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getRestoreDatabaseMethod(),
                restoreDatabaseRequest(),
                operation("operations/restore-database"));
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getListDatabaseOperationsMethod(),
                ListDatabaseOperationsRequest.newBuilder().setParent(INSTANCE).build(),
                ListDatabaseOperationsResponse.newBuilder().addOperations(operation("operations/database-op")).build());
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getListBackupOperationsMethod(),
                ListBackupOperationsRequest.newBuilder().setParent(INSTANCE).build(),
                ListBackupOperationsResponse.newBuilder().addOperations(operation("operations/backup-op")).build());
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getListDatabaseRolesMethod(),
                ListDatabaseRolesRequest.newBuilder().setParent(DATABASE).build(),
                ListDatabaseRolesResponse.newBuilder().addDatabaseRoles(databaseRole()).build());
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getAddSplitPointsMethod(),
                AddSplitPointsRequest.newBuilder().setDatabase(DATABASE).setInitiator("test").build(),
                AddSplitPointsResponse.getDefaultInstance());
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getCreateBackupScheduleMethod(),
                createBackupScheduleRequest(),
                backupSchedule(BACKUP_SCHEDULE));
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getGetBackupScheduleMethod(),
                GetBackupScheduleRequest.newBuilder().setName(BACKUP_SCHEDULE).build(),
                backupSchedule(BACKUP_SCHEDULE));
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getUpdateBackupScheduleMethod(),
                UpdateBackupScheduleRequest.newBuilder()
                        .setBackupSchedule(backupSchedule(BACKUP_SCHEDULE))
                        .setUpdateMask(FieldMask.newBuilder().addPaths("retention_duration"))
                        .build(),
                backupSchedule(BACKUP_SCHEDULE));
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getDeleteBackupScheduleMethod(),
                DeleteBackupScheduleRequest.newBuilder().setName(BACKUP_SCHEDULE).build(),
                Empty.getDefaultInstance());
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getListBackupSchedulesMethod(),
                ListBackupSchedulesRequest.newBuilder().setParent(DATABASE).build(),
                ListBackupSchedulesResponse.newBuilder().addBackupSchedules(backupSchedule(BACKUP_SCHEDULE)).build());
        assertMarshallerRoundTrips(
                DatabaseAdminGrpc.getInternalUpdateGraphOperationMethod(),
                InternalUpdateGraphOperationRequest.newBuilder()
                        .setDatabase(DATABASE)
                        .setOperationId("operation-id")
                        .setVmIdentityToken("token")
                        .setProgress(0.75D)
                        .setStatus(Status.newBuilder().setMessage("running"))
                        .build(),
                InternalUpdateGraphOperationResponse.getDefaultInstance());
    }

    @Test
    void blockingStubInvokesEveryUnaryRpcAgainstInProcessService() throws Exception {
        try (GrpcFixture fixture = GrpcFixture.start()) {
            DatabaseAdminGrpc.DatabaseAdminBlockingStub stub = DatabaseAdminGrpc.newBlockingStub(fixture.channel);

            assertThat(stub.listDatabases(ListDatabasesRequest.newBuilder().setParent(INSTANCE).build())
                            .getDatabases(0)
                            .getName())
                    .isEqualTo(DATABASE + "-listed");
            assertThat(stub.createDatabase(createDatabaseRequest()).getName()).isEqualTo("operations/create-database");
            assertThat(stub.getDatabase(GetDatabaseRequest.newBuilder().setName(DATABASE).build()).getName())
                    .isEqualTo(DATABASE);
            assertThat(stub.updateDatabase(updateDatabaseRequest()).getName()).isEqualTo("operations/update-database");
            assertThat(stub.updateDatabaseDdl(updateDatabaseDdlRequest()).getName()).isEqualTo("operations/update-ddl");
            assertThat(stub.dropDatabase(DropDatabaseRequest.newBuilder().setDatabase(DATABASE).build()))
                    .isEqualTo(Empty.getDefaultInstance());
            assertThat(stub.getDatabaseDdl(GetDatabaseDdlRequest.newBuilder().setDatabase(DATABASE).build())
                            .getStatementsList())
                    .containsExactly("CREATE TABLE Singers (SingerId INT64) PRIMARY KEY(SingerId)");
            assertThat(stub.setIamPolicy(SetIamPolicyRequest.newBuilder().setResource(DATABASE).setPolicy(policy()).build())
                            .getVersion())
                    .isEqualTo(3);
            assertThat(stub.getIamPolicy(GetIamPolicyRequest.newBuilder().setResource(DATABASE).build()).getVersion())
                    .isEqualTo(3);
            assertThat(stub.testIamPermissions(TestIamPermissionsRequest.newBuilder()
                                    .setResource(DATABASE)
                                    .addPermissions("spanner.databases.get")
                                    .build())
                            .getPermissionsList())
                    .containsExactly("spanner.databases.get");
            assertThat(stub.createBackup(createBackupRequest()).getName()).isEqualTo("operations/create-backup");
            assertThat(stub.copyBackup(CopyBackupRequest.newBuilder()
                                    .setParent(INSTANCE)
                                    .setBackupId("copy")
                                    .setSourceBackup(BACKUP)
                                    .build())
                            .getName())
                    .isEqualTo("operations/copy-backup");
            assertThat(stub.getBackup(GetBackupRequest.newBuilder().setName(BACKUP).build()).getName()).isEqualTo(BACKUP);
            assertThat(stub.updateBackup(UpdateBackupRequest.newBuilder()
                                    .setBackup(backup(BACKUP))
                                    .setUpdateMask(FieldMask.newBuilder().addPaths("expire_time"))
                                    .build())
                            .getState())
                    .isEqualTo(Backup.State.READY);
            assertThat(stub.deleteBackup(DeleteBackupRequest.newBuilder().setName(BACKUP).build()))
                    .isEqualTo(Empty.getDefaultInstance());
            assertThat(stub.listBackups(ListBackupsRequest.newBuilder().setParent(INSTANCE).build())
                            .getBackups(0)
                            .getName())
                    .isEqualTo(BACKUP + "-listed");
            assertThat(stub.restoreDatabase(restoreDatabaseRequest()).getName()).isEqualTo("operations/restore-database");
            assertThat(stub.listDatabaseOperations(ListDatabaseOperationsRequest.newBuilder().setParent(INSTANCE).build())
                            .getOperations(0)
                            .getName())
                    .isEqualTo("operations/database-op");
            assertThat(stub.listBackupOperations(ListBackupOperationsRequest.newBuilder().setParent(INSTANCE).build())
                            .getOperations(0)
                            .getName())
                    .isEqualTo("operations/backup-op");
            assertThat(stub.listDatabaseRoles(ListDatabaseRolesRequest.newBuilder().setParent(DATABASE).build())
                            .getDatabaseRoles(0)
                            .getName())
                    .isEqualTo(DATABASE_ROLE);
            assertThat(stub.addSplitPoints(AddSplitPointsRequest.newBuilder()
                                    .setDatabase(DATABASE)
                                    .setInitiator("test")
                                    .build())
                            .isInitialized())
                    .isTrue();
            assertThat(stub.createBackupSchedule(createBackupScheduleRequest()).getName()).isEqualTo(BACKUP_SCHEDULE);
            assertThat(stub.getBackupSchedule(GetBackupScheduleRequest.newBuilder().setName(BACKUP_SCHEDULE).build())
                            .getName())
                    .isEqualTo(BACKUP_SCHEDULE);
            assertThat(stub.updateBackupSchedule(UpdateBackupScheduleRequest.newBuilder()
                                    .setBackupSchedule(backupSchedule(BACKUP_SCHEDULE))
                                    .setUpdateMask(FieldMask.newBuilder().addPaths("retention_duration"))
                                    .build())
                            .getName())
                    .isEqualTo(BACKUP_SCHEDULE);
            assertThat(stub.deleteBackupSchedule(DeleteBackupScheduleRequest.newBuilder().setName(BACKUP_SCHEDULE).build()))
                    .isEqualTo(Empty.getDefaultInstance());
            assertThat(stub.listBackupSchedules(ListBackupSchedulesRequest.newBuilder().setParent(DATABASE).build())
                            .getBackupSchedules(0)
                            .getName())
                    .isEqualTo(BACKUP_SCHEDULE + "-listed");
            assertThat(stub.internalUpdateGraphOperation(InternalUpdateGraphOperationRequest.newBuilder()
                                    .setDatabase(DATABASE)
                                    .setOperationId("operation-id")
                                    .setVmIdentityToken("token")
                                    .setProgress(1D)
                                    .setStatus(Status.newBuilder().setMessage("done"))
                                    .build())
                            .isInitialized())
                    .isTrue();
        }
    }

    @Test
    void futureAsyncAndBlockingV2StubsDeliverResponses() throws Exception {
        try (GrpcFixture fixture = GrpcFixture.start()) {
            ListenableFuture<Database> future = DatabaseAdminGrpc.newFutureStub(fixture.channel)
                    .getDatabase(GetDatabaseRequest.newBuilder().setName(DATABASE).build());
            assertThat(future.get(5, TimeUnit.SECONDS).getName()).isEqualTo(DATABASE);

            assertThat(DatabaseAdminGrpc.newBlockingV2Stub(fixture.channel)
                            .getBackup(GetBackupRequest.newBuilder().setName(BACKUP).build())
                            .getName())
                    .isEqualTo(BACKUP);

            RecordingObserver<GetDatabaseDdlResponse> observer = new RecordingObserver<>();
            DatabaseAdminGrpc.newStub(fixture.channel)
                    .getDatabaseDdl(GetDatabaseDdlRequest.newBuilder().setDatabase(DATABASE).build(), observer);

            GetDatabaseDdlResponse response = observer.awaitValue();
            assertThat(response.getStatementsList())
                    .containsExactly("CREATE TABLE Singers (SingerId INT64) PRIMARY KEY(SingerId)");
            assertThat(observer.error).isNull();
        }
    }

    @Test
    void defaultAsyncServiceMethodsReturnUnimplementedStatus() throws Exception {
        try (GrpcFixture fixture = GrpcFixture.start(new DefaultDatabaseAdminService())) {
            DatabaseAdminGrpc.DatabaseAdminBlockingStub stub =
                    DatabaseAdminGrpc.newBlockingStub(fixture.channel).withDeadlineAfter(5, TimeUnit.SECONDS);

            assertThatThrownBy(() -> stub.getDatabase(GetDatabaseRequest.newBuilder().setName(DATABASE).build()))
                    .isInstanceOfSatisfying(StatusRuntimeException.class,
                            exception -> assertThat(exception.getStatus().getCode()).isEqualTo(Code.UNIMPLEMENTED));
        }
    }

    private static List<String> expectedFullMethodNames() {
        return List.of(
                fullMethodName("ListDatabases"),
                fullMethodName("CreateDatabase"),
                fullMethodName("GetDatabase"),
                fullMethodName("UpdateDatabase"),
                fullMethodName("UpdateDatabaseDdl"),
                fullMethodName("DropDatabase"),
                fullMethodName("GetDatabaseDdl"),
                fullMethodName("SetIamPolicy"),
                fullMethodName("GetIamPolicy"),
                fullMethodName("TestIamPermissions"),
                fullMethodName("CreateBackup"),
                fullMethodName("CopyBackup"),
                fullMethodName("GetBackup"),
                fullMethodName("UpdateBackup"),
                fullMethodName("DeleteBackup"),
                fullMethodName("ListBackups"),
                fullMethodName("RestoreDatabase"),
                fullMethodName("ListDatabaseOperations"),
                fullMethodName("ListBackupOperations"),
                fullMethodName("ListDatabaseRoles"),
                fullMethodName("AddSplitPoints"),
                fullMethodName("CreateBackupSchedule"),
                fullMethodName("GetBackupSchedule"),
                fullMethodName("UpdateBackupSchedule"),
                fullMethodName("DeleteBackupSchedule"),
                fullMethodName("ListBackupSchedules"),
                fullMethodName("InternalUpdateGraphOperation"));
    }

    private static List<String> expectedSchemaMethodNames() {
        return expectedFullMethodNames().stream()
                .map(fullMethodName -> fullMethodName.substring(fullMethodName.lastIndexOf('/') + 1))
                .toList();
    }

    private static String fullMethodName(String methodName) {
        return MethodDescriptor.generateFullMethodName(DatabaseAdminGrpc.SERVICE_NAME, methodName);
    }

    private static void assertUnaryMethod(MethodDescriptor<?, ?> method, String methodName) {
        assertThat(method.getType()).isEqualTo(MethodDescriptor.MethodType.UNARY);
        assertThat(method.getFullMethodName()).isEqualTo(fullMethodName(methodName));
        assertThat(method.getRequestMarshaller()).isNotNull();
        assertThat(method.getResponseMarshaller()).isNotNull();
    }

    private static <ReqT extends MessageLite, RespT extends MessageLite> void assertMarshallerRoundTrips(
            MethodDescriptor<ReqT, RespT> method, ReqT request, RespT response) throws Exception {
        assertThat(roundTrip(method.getRequestMarshaller(), request)).isEqualTo(request);
        assertThat(roundTrip(method.getResponseMarshaller(), response)).isEqualTo(response);
        assertThat(method).isSameAs(method);
    }

    private static <T> T roundTrip(MethodDescriptor.Marshaller<T> marshaller, T value) throws Exception {
        try (InputStream input = marshaller.stream(value)) {
            return marshaller.parse(input);
        }
    }

    private static CreateDatabaseRequest createDatabaseRequest() {
        return CreateDatabaseRequest.newBuilder()
                .setParent(INSTANCE)
                .setCreateStatement("CREATE DATABASE `test-database`")
                .addExtraStatements("CREATE TABLE Singers (SingerId INT64) PRIMARY KEY(SingerId)")
                .setDatabaseDialect(DatabaseDialect.GOOGLE_STANDARD_SQL)
                .build();
    }

    private static UpdateDatabaseRequest updateDatabaseRequest() {
        return UpdateDatabaseRequest.newBuilder()
                .setDatabase(database(DATABASE))
                .setUpdateMask(FieldMask.newBuilder().addPaths("enable_drop_protection"))
                .build();
    }

    private static UpdateDatabaseDdlRequest updateDatabaseDdlRequest() {
        return UpdateDatabaseDdlRequest.newBuilder()
                .setDatabase(DATABASE)
                .addStatements("CREATE TABLE Albums (AlbumId INT64) PRIMARY KEY(AlbumId)")
                .setOperationId("ddl-operation")
                .build();
    }

    private static CreateBackupRequest createBackupRequest() {
        return CreateBackupRequest.newBuilder()
                .setParent(INSTANCE)
                .setBackupId("test-backup")
                .setBackup(backup(BACKUP))
                .build();
    }

    private static RestoreDatabaseRequest restoreDatabaseRequest() {
        return RestoreDatabaseRequest.newBuilder()
                .setParent(INSTANCE)
                .setDatabaseId("restored-database")
                .setBackup(BACKUP)
                .build();
    }

    private static CreateBackupScheduleRequest createBackupScheduleRequest() {
        return CreateBackupScheduleRequest.newBuilder()
                .setParent(DATABASE)
                .setBackupScheduleId("nightly")
                .setBackupSchedule(backupSchedule(BACKUP_SCHEDULE))
                .build();
    }

    private static Database database(String name) {
        return Database.newBuilder()
                .setName(name)
                .setState(Database.State.READY)
                .setDatabaseDialect(DatabaseDialect.GOOGLE_STANDARD_SQL)
                .setVersionRetentionPeriod("7d")
                .setDefaultLeader("us-central1")
                .build();
    }

    private static Backup backup(String name) {
        return Backup.newBuilder()
                .setName(name)
                .setDatabase(DATABASE)
                .setState(Backup.State.READY)
                .setSizeBytes(1024L)
                .setDatabaseDialect(DatabaseDialect.GOOGLE_STANDARD_SQL)
                .build();
    }

    private static BackupSchedule backupSchedule(String name) {
        return BackupSchedule.newBuilder().setName(name).build();
    }

    private static DatabaseRole databaseRole() {
        return DatabaseRole.newBuilder().setName(DATABASE_ROLE).build();
    }

    private static Operation operation(String name) {
        return Operation.newBuilder().setName(name).build();
    }

    private static Policy policy() {
        return Policy.newBuilder().setVersion(3).build();
    }

    private static GetDatabaseDdlResponse databaseDdl() {
        return GetDatabaseDdlResponse.newBuilder()
                .addStatements("CREATE TABLE Singers (SingerId INT64) PRIMARY KEY(SingerId)")
                .build();
    }

    private static void respond(StreamObserver<Empty> observer) {
        respond(observer, Empty.getDefaultInstance());
    }

    private static <T> void respond(StreamObserver<T> observer, T value) {
        observer.onNext(value);
        observer.onCompleted();
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
                    .addService(new FakeDatabaseAdminService())
                    .build()
                    .start();
            ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
            return new GrpcFixture(server, channel);
        }

        static GrpcFixture start(DatabaseAdminGrpc.AsyncService service) throws Exception {
            String serverName = InProcessServerBuilder.generateName();
            Server server = InProcessServerBuilder.forName(serverName)
                    .directExecutor()
                    .addService(DatabaseAdminGrpc.bindService(service))
                    .build()
                    .start();
            ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
            return new GrpcFixture(server, channel);
        }

        @Override
        public void close() throws Exception {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static final class RecordingObserver<T> implements StreamObserver<T> {
        private final CountDownLatch completed = new CountDownLatch(1);
        private T value;
        private Throwable error;

        @Override
        public void onNext(T value) {
            this.value = value;
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

        T awaitValue() throws InterruptedException {
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            return value;
        }
    }

    private static final class DefaultDatabaseAdminService implements DatabaseAdminGrpc.AsyncService {
    }

    private static final class FakeDatabaseAdminService extends DatabaseAdminGrpc.DatabaseAdminImplBase {
        @Override
        public void listDatabases(
                ListDatabasesRequest request, StreamObserver<ListDatabasesResponse> responseObserver) {
            respond(responseObserver, ListDatabasesResponse.newBuilder()
                    .addDatabases(database(request.getParent() + "/databases/test-database-listed"))
                    .setNextPageToken("next-db")
                    .build());
        }

        @Override
        public void createDatabase(
                CreateDatabaseRequest request, StreamObserver<Operation> responseObserver) {
            respond(responseObserver, operation("operations/create-database"));
        }

        @Override
        public void getDatabase(GetDatabaseRequest request, StreamObserver<Database> responseObserver) {
            respond(responseObserver, database(request.getName()));
        }

        @Override
        public void updateDatabase(
                UpdateDatabaseRequest request, StreamObserver<Operation> responseObserver) {
            respond(responseObserver, operation("operations/update-database"));
        }

        @Override
        public void updateDatabaseDdl(
                UpdateDatabaseDdlRequest request, StreamObserver<Operation> responseObserver) {
            respond(responseObserver, operation("operations/update-ddl"));
        }

        @Override
        public void dropDatabase(DropDatabaseRequest request, StreamObserver<Empty> responseObserver) {
            respond(responseObserver);
        }

        @Override
        public void getDatabaseDdl(
                GetDatabaseDdlRequest request, StreamObserver<GetDatabaseDdlResponse> responseObserver) {
            respond(responseObserver, databaseDdl());
        }

        @Override
        public void setIamPolicy(SetIamPolicyRequest request, StreamObserver<Policy> responseObserver) {
            respond(responseObserver, policy());
        }

        @Override
        public void getIamPolicy(GetIamPolicyRequest request, StreamObserver<Policy> responseObserver) {
            respond(responseObserver, policy());
        }

        @Override
        public void testIamPermissions(
                TestIamPermissionsRequest request, StreamObserver<TestIamPermissionsResponse> responseObserver) {
            respond(responseObserver, TestIamPermissionsResponse.newBuilder()
                    .addAllPermissions(request.getPermissionsList())
                    .build());
        }

        @Override
        public void createBackup(CreateBackupRequest request, StreamObserver<Operation> responseObserver) {
            respond(responseObserver, operation("operations/create-backup"));
        }

        @Override
        public void copyBackup(CopyBackupRequest request, StreamObserver<Operation> responseObserver) {
            respond(responseObserver, operation("operations/copy-backup"));
        }

        @Override
        public void getBackup(GetBackupRequest request, StreamObserver<Backup> responseObserver) {
            respond(responseObserver, backup(request.getName()));
        }

        @Override
        public void updateBackup(UpdateBackupRequest request, StreamObserver<Backup> responseObserver) {
            respond(responseObserver, request.getBackup());
        }

        @Override
        public void deleteBackup(DeleteBackupRequest request, StreamObserver<Empty> responseObserver) {
            respond(responseObserver);
        }

        @Override
        public void listBackups(ListBackupsRequest request, StreamObserver<ListBackupsResponse> responseObserver) {
            respond(responseObserver, ListBackupsResponse.newBuilder()
                    .addBackups(backup(request.getParent() + "/backups/test-backup-listed"))
                    .setNextPageToken("next-backup")
                    .build());
        }

        @Override
        public void restoreDatabase(RestoreDatabaseRequest request, StreamObserver<Operation> responseObserver) {
            respond(responseObserver, operation("operations/restore-database"));
        }

        @Override
        public void listDatabaseOperations(
                ListDatabaseOperationsRequest request,
                StreamObserver<ListDatabaseOperationsResponse> responseObserver) {
            respond(responseObserver, ListDatabaseOperationsResponse.newBuilder()
                    .addOperations(operation("operations/database-op"))
                    .build());
        }

        @Override
        public void listBackupOperations(
                ListBackupOperationsRequest request,
                StreamObserver<ListBackupOperationsResponse> responseObserver) {
            respond(responseObserver, ListBackupOperationsResponse.newBuilder()
                    .addOperations(operation("operations/backup-op"))
                    .build());
        }

        @Override
        public void listDatabaseRoles(
                ListDatabaseRolesRequest request, StreamObserver<ListDatabaseRolesResponse> responseObserver) {
            respond(responseObserver, ListDatabaseRolesResponse.newBuilder().addDatabaseRoles(databaseRole()).build());
        }

        @Override
        public void addSplitPoints(
                AddSplitPointsRequest request, StreamObserver<AddSplitPointsResponse> responseObserver) {
            respond(responseObserver, AddSplitPointsResponse.getDefaultInstance());
        }

        @Override
        public void createBackupSchedule(
                CreateBackupScheduleRequest request, StreamObserver<BackupSchedule> responseObserver) {
            respond(responseObserver, backupSchedule(request.getParent() + "/backupSchedules/" + request.getBackupScheduleId()));
        }

        @Override
        public void getBackupSchedule(
                GetBackupScheduleRequest request, StreamObserver<BackupSchedule> responseObserver) {
            respond(responseObserver, backupSchedule(request.getName()));
        }

        @Override
        public void updateBackupSchedule(
                UpdateBackupScheduleRequest request, StreamObserver<BackupSchedule> responseObserver) {
            respond(responseObserver, request.getBackupSchedule());
        }

        @Override
        public void deleteBackupSchedule(
                DeleteBackupScheduleRequest request, StreamObserver<Empty> responseObserver) {
            respond(responseObserver);
        }

        @Override
        public void listBackupSchedules(
                ListBackupSchedulesRequest request, StreamObserver<ListBackupSchedulesResponse> responseObserver) {
            respond(responseObserver, ListBackupSchedulesResponse.newBuilder()
                    .addBackupSchedules(backupSchedule(request.getParent() + "/backupSchedules/nightly-listed"))
                    .build());
        }

        @Override
        public void internalUpdateGraphOperation(
                InternalUpdateGraphOperationRequest request,
                StreamObserver<InternalUpdateGraphOperationResponse> responseObserver) {
            respond(responseObserver, InternalUpdateGraphOperationResponse.getDefaultInstance());
        }
    }
}
