/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_cloud_spanner_admin_database_v1;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.longrunning.Operation;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Duration;
import com.google.protobuf.FieldMask;
import com.google.protobuf.ListValue;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.google.rpc.Status;
import com.google.spanner.admin.database.v1.AddSplitPointsRequest;
import com.google.spanner.admin.database.v1.AddSplitPointsResponse;
import com.google.spanner.admin.database.v1.Backup;
import com.google.spanner.admin.database.v1.BackupInfo;
import com.google.spanner.admin.database.v1.BackupInstancePartition;
import com.google.spanner.admin.database.v1.BackupName;
import com.google.spanner.admin.database.v1.BackupProto;
import com.google.spanner.admin.database.v1.BackupSchedule;
import com.google.spanner.admin.database.v1.BackupScheduleName;
import com.google.spanner.admin.database.v1.BackupScheduleProto;
import com.google.spanner.admin.database.v1.BackupScheduleSpec;
import com.google.spanner.admin.database.v1.CommonProto;
import com.google.spanner.admin.database.v1.CopyBackupEncryptionConfig;
import com.google.spanner.admin.database.v1.CopyBackupMetadata;
import com.google.spanner.admin.database.v1.CopyBackupRequest;
import com.google.spanner.admin.database.v1.CreateBackupEncryptionConfig;
import com.google.spanner.admin.database.v1.CreateBackupMetadata;
import com.google.spanner.admin.database.v1.CreateBackupRequest;
import com.google.spanner.admin.database.v1.CreateBackupScheduleRequest;
import com.google.spanner.admin.database.v1.CreateDatabaseMetadata;
import com.google.spanner.admin.database.v1.CreateDatabaseRequest;
import com.google.spanner.admin.database.v1.CrontabSpec;
import com.google.spanner.admin.database.v1.CryptoKeyName;
import com.google.spanner.admin.database.v1.CryptoKeyVersionName;
import com.google.spanner.admin.database.v1.Database;
import com.google.spanner.admin.database.v1.DatabaseDialect;
import com.google.spanner.admin.database.v1.DatabaseName;
import com.google.spanner.admin.database.v1.DatabaseRole;
import com.google.spanner.admin.database.v1.DdlStatementActionInfo;
import com.google.spanner.admin.database.v1.DeleteBackupRequest;
import com.google.spanner.admin.database.v1.DeleteBackupScheduleRequest;
import com.google.spanner.admin.database.v1.DropDatabaseRequest;
import com.google.spanner.admin.database.v1.EncryptionConfig;
import com.google.spanner.admin.database.v1.EncryptionInfo;
import com.google.spanner.admin.database.v1.FullBackupSpec;
import com.google.spanner.admin.database.v1.GetBackupRequest;
import com.google.spanner.admin.database.v1.GetBackupScheduleRequest;
import com.google.spanner.admin.database.v1.GetDatabaseDdlRequest;
import com.google.spanner.admin.database.v1.GetDatabaseDdlResponse;
import com.google.spanner.admin.database.v1.GetDatabaseRequest;
import com.google.spanner.admin.database.v1.IncrementalBackupSpec;
import com.google.spanner.admin.database.v1.InstanceName;
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
import com.google.spanner.admin.database.v1.OperationProgress;
import com.google.spanner.admin.database.v1.OptimizeRestoredDatabaseMetadata;
import com.google.spanner.admin.database.v1.RestoreDatabaseEncryptionConfig;
import com.google.spanner.admin.database.v1.RestoreDatabaseMetadata;
import com.google.spanner.admin.database.v1.RestoreDatabaseRequest;
import com.google.spanner.admin.database.v1.RestoreInfo;
import com.google.spanner.admin.database.v1.RestoreSourceType;
import com.google.spanner.admin.database.v1.SpannerDatabaseAdminProto;
import com.google.spanner.admin.database.v1.SplitPoints;
import com.google.spanner.admin.database.v1.UpdateBackupRequest;
import com.google.spanner.admin.database.v1.UpdateBackupScheduleRequest;
import com.google.spanner.admin.database.v1.UpdateDatabaseDdlMetadata;
import com.google.spanner.admin.database.v1.UpdateDatabaseDdlRequest;
import com.google.spanner.admin.database.v1.UpdateDatabaseMetadata;
import com.google.spanner.admin.database.v1.UpdateDatabaseRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

public class Proto_google_cloud_spanner_admin_database_v1Test {
    private static final String PROJECT = "sample-project";
    private static final String INSTANCE = "test-instance";
    private static final String DATABASE_ID = "appdb";
    private static final String BACKUP_ID = "nightly";
    private static final String SCHEDULE_ID = "daily-full";
    private static final String DATABASE = DatabaseName.format(PROJECT, INSTANCE, DATABASE_ID);
    private static final String INSTANCE_NAME = InstanceName.format(PROJECT, INSTANCE);
    private static final String BACKUP = BackupName.format(PROJECT, INSTANCE, BACKUP_ID);
    private static final String SCHEDULE = BackupScheduleName.format(PROJECT, INSTANCE, DATABASE_ID, SCHEDULE_ID);
    private static final String KMS_KEY = CryptoKeyName.format(PROJECT, "us-central1", "spanner-ring", "primary-key");
    private static final String KMS_KEY_VERSION = CryptoKeyVersionName.format(
            PROJECT, "us-central1", "spanner-ring", "primary-key", "1");

    @Test
    void resourceNamesRoundTripThroughGeneratedHelpers() {
        DatabaseName databaseName = DatabaseName.of(PROJECT, INSTANCE, DATABASE_ID);
        InstanceName instanceName = InstanceName.parse(INSTANCE_NAME);
        BackupName backupName = BackupName.of(PROJECT, INSTANCE, BACKUP_ID);
        BackupScheduleName scheduleName = BackupScheduleName.parse(SCHEDULE);
        CryptoKeyName keyName = CryptoKeyName.parse(KMS_KEY);
        CryptoKeyVersionName keyVersionName = CryptoKeyVersionName.parse(KMS_KEY_VERSION);

        assertThat(databaseName.toString()).isEqualTo(DATABASE);
        assertThat(DatabaseName.isParsableFrom(DATABASE)).isTrue();
        assertThat(DatabaseName.parse(DATABASE).getFieldValuesMap())
                .containsEntry("project", PROJECT)
                .containsEntry("instance", INSTANCE)
                .containsEntry("database", DATABASE_ID);
        assertThat(DatabaseName.toStringList(List.of(databaseName))).containsExactly(DATABASE);
        assertThat(DatabaseName.parseList(List.of(DATABASE))).containsExactly(databaseName);
        assertThat(databaseName.toBuilder().setDatabase("otherdb").build().toString())
                .isEqualTo(DatabaseName.format(PROJECT, INSTANCE, "otherdb"));

        assertThat(instanceName.getInstance()).isEqualTo(INSTANCE);
        assertThat(BackupName.parse(backupName.toString()).getBackup()).isEqualTo(BACKUP_ID);
        assertThat(scheduleName.getSchedule()).isEqualTo(SCHEDULE_ID);
        assertThat(scheduleName.getFieldValue("database")).isEqualTo(DATABASE_ID);
        assertThat(keyName.getCryptoKey()).isEqualTo("primary-key");
        assertThat(keyVersionName.getCryptoKeyVersion()).isEqualTo("1");
        assertThat(CryptoKeyVersionName.toStringList(List.of(keyVersionName))).containsExactly(KMS_KEY_VERSION);
    }

    @Test
    void descriptorsExposeDatabaseAdminServiceAndSharedMessages() {
        Descriptors.FileDescriptor adminDescriptor = SpannerDatabaseAdminProto.getDescriptor();
        Descriptors.FileDescriptor commonDescriptor = CommonProto.getDescriptor();
        Descriptors.FileDescriptor backupDescriptor = BackupProto.getDescriptor();
        Descriptors.FileDescriptor scheduleDescriptor = BackupScheduleProto.getDescriptor();

        assertThat(commonDescriptor.findMessageTypeByName("OperationProgress").getFields())
                .extracting(Descriptors.FieldDescriptor::getName)
                .containsExactly("progress_percent", "start_time", "end_time");
        assertThat(backupDescriptor.findMessageTypeByName("Backup").findFieldByName("encryption_information")
                .getMessageType().getFullName())
                .isEqualTo("google.spanner.admin.database.v1.EncryptionInfo");
        assertThat(scheduleDescriptor.findMessageTypeByName("BackupSchedule")
                .findFieldByName("incremental_backup_spec").getMessageType().getFullName())
                .isEqualTo("google.spanner.admin.database.v1.IncrementalBackupSpec");
        assertThat(adminDescriptor.findServiceByName("DatabaseAdmin").getMethods())
                .extracting(Descriptors.MethodDescriptor::getName)
                .contains(
                        "ListDatabases",
                        "CreateDatabase",
                        "UpdateDatabaseDdl",
                        "RestoreDatabase",
                        "CreateBackup",
                        "CopyBackup",
                        "CreateBackupSchedule",
                        "AddSplitPoints");
    }

    @Test
    void databaseMessagesPreserveEncryptionRestoreAndRoundTripParsing() throws Exception {
        Timestamp createTime = timestamp(1_700_000_000L);
        Timestamp versionTime = timestamp(1_700_000_100L);
        EncryptionConfig encryptionConfig = EncryptionConfig.newBuilder()
                .setKmsKeyName(KMS_KEY)
                .addKmsKeyNames(KMS_KEY)
                .addKmsKeyNames(CryptoKeyName.format(PROJECT, "us-east1", "spanner-ring", "secondary-key"))
                .build();
        EncryptionInfo encryptionInfo = EncryptionInfo.newBuilder()
                .setEncryptionType(EncryptionInfo.Type.CUSTOMER_MANAGED_ENCRYPTION)
                .setEncryptionStatus(Status.newBuilder().setCode(0).setMessage("ok"))
                .setKmsKeyVersion(KMS_KEY_VERSION)
                .build();
        BackupInfo backupInfo = BackupInfo.newBuilder()
                .setBackup(BACKUP)
                .setVersionTime(versionTime)
                .setCreateTime(createTime)
                .setSourceDatabase(DATABASE)
                .build();
        RestoreInfo restoreInfo = RestoreInfo.newBuilder()
                .setSourceType(RestoreSourceType.BACKUP)
                .setBackupInfo(backupInfo)
                .build();

        Database database = Database.newBuilder()
                .setName(DATABASE)
                .setState(Database.State.READY_OPTIMIZING)
                .setCreateTime(createTime)
                .setRestoreInfo(restoreInfo)
                .setEncryptionConfig(encryptionConfig)
                .addEncryptionInfo(encryptionInfo)
                .setVersionRetentionPeriod("7d")
                .setEarliestVersionTime(timestamp(1_699_000_000L))
                .setDefaultLeader("us-central1")
                .setDatabaseDialect(DatabaseDialect.POSTGRESQL)
                .setEnableDropProtection(true)
                .setReconciling(true)
                .build();

        assertThat(database.isInitialized()).isTrue();
        assertThat(database.hasRestoreInfo()).isTrue();
        assertThat(database.getRestoreInfo().getSourceInfoCase()).isEqualTo(RestoreInfo.SourceInfoCase.BACKUP_INFO);
        assertThat(database.getRestoreInfo().getBackupInfo()).isEqualTo(backupInfo);
        assertThat(database.getEncryptionConfig().getKmsKeyNamesList()).hasSize(2);
        assertThat(database.getEncryptionInfo(0).getEncryptionType())
                .isEqualTo(EncryptionInfo.Type.CUSTOMER_MANAGED_ENCRYPTION);
        assertThat(database.getDatabaseDialectValue()).isEqualTo(DatabaseDialect.POSTGRESQL_VALUE);
        assertThat(database.getEnableDropProtection()).isTrue();
        assertThat(database.getReconciling()).isTrue();

        byte[] bytes = database.toByteArray();
        assertThat(Database.parseFrom(bytes)).isEqualTo(database);
        assertThat(Database.parseFrom(ByteString.copyFrom(bytes))).isEqualTo(database);
        assertThat(Database.parseFrom(ByteBuffer.wrap(bytes))).isEqualTo(database);
        assertThat(Database.parser().parseFrom(bytes)).isEqualTo(database);

        ByteArrayOutputStream delimitedBytes = new ByteArrayOutputStream();
        database.writeDelimitedTo(delimitedBytes);
        assertThat(Database.parseDelimitedFrom(new ByteArrayInputStream(delimitedBytes.toByteArray())))
                .isEqualTo(database);

        Database editedDatabase = database.toBuilder()
                .setState(Database.State.READY)
                .setDefaultLeader("us-east1")
                .clearReconciling()
                .build();
        assertThat(editedDatabase.getState()).isEqualTo(Database.State.READY);
        assertThat(editedDatabase.getDefaultLeader()).isEqualTo("us-east1");
        assertThat(editedDatabase.getReconciling()).isFalse();
    }

    @Test
    void databaseRequestsResponsesAndOperationMetadataCoverDdlAndRoles() {
        Database database = sampleDatabase();
        FieldMask databaseMask = FieldMask.newBuilder()
                .addPaths("enable_drop_protection")
                .addPaths("default_leader")
                .build();
        OperationProgress progress = OperationProgress.newBuilder()
                .setProgressPercent(80)
                .setStartTime(timestamp(1_700_000_000L))
                .setEndTime(timestamp(1_700_000_060L))
                .build();
        DdlStatementActionInfo actionInfo = DdlStatementActionInfo.newBuilder()
                .setAction("CREATE")
                .setEntityType("TABLE")
                .addEntityNames("Singers")
                .build();
        Operation operation = Operation.newBuilder()
                .setName(DATABASE + "/operations/update-ddl")
                .setDone(false)
                .build();

        CreateDatabaseRequest createDatabase = CreateDatabaseRequest.newBuilder()
                .setParent(INSTANCE_NAME)
                .setCreateStatement("CREATE DATABASE `" + DATABASE_ID + "`")
                .addExtraStatements("CREATE TABLE Singers (SingerId INT64 NOT NULL) PRIMARY KEY (SingerId)")
                .setEncryptionConfig(database.getEncryptionConfig())
                .setDatabaseDialect(DatabaseDialect.GOOGLE_STANDARD_SQL)
                .setProtoDescriptors(ByteString.copyFromUtf8("descriptor-bytes"))
                .build();
        CreateDatabaseMetadata createMetadata = CreateDatabaseMetadata.newBuilder().setDatabase(DATABASE).build();
        ListDatabasesRequest listDatabases = ListDatabasesRequest.newBuilder()
                .setParent(INSTANCE_NAME)
                .setPageSize(10)
                .setPageToken("database-page")
                .build();
        ListDatabasesResponse listDatabasesResponse = ListDatabasesResponse.newBuilder()
                .addDatabases(database)
                .setNextPageToken("next-database-page")
                .build();
        GetDatabaseRequest getDatabase = GetDatabaseRequest.newBuilder().setName(DATABASE).build();
        UpdateDatabaseRequest updateDatabase = UpdateDatabaseRequest.newBuilder()
                .setDatabase(database)
                .setUpdateMask(databaseMask)
                .build();
        UpdateDatabaseMetadata updateMetadata = UpdateDatabaseMetadata.newBuilder()
                .setRequest(updateDatabase)
                .setProgress(progress)
                .setCancelTime(timestamp(1_700_000_120L))
                .build();
        DropDatabaseRequest dropDatabase = DropDatabaseRequest.newBuilder().setDatabase(DATABASE).build();
        GetDatabaseDdlRequest getDdl = GetDatabaseDdlRequest.newBuilder().setDatabase(DATABASE).build();
        GetDatabaseDdlResponse ddlResponse = GetDatabaseDdlResponse.newBuilder()
                .addStatements("CREATE TABLE Singers (SingerId INT64 NOT NULL) PRIMARY KEY (SingerId)")
                .setProtoDescriptors(ByteString.copyFromUtf8("descriptor-bytes"))
                .build();
        UpdateDatabaseDdlRequest updateDdl = UpdateDatabaseDdlRequest.newBuilder()
                .setDatabase(DATABASE)
                .addStatements("ALTER TABLE Singers ADD COLUMN Name STRING(MAX)")
                .setOperationId("ddl-operation")
                .setProtoDescriptors(ByteString.copyFromUtf8("descriptor-bytes"))
                .setThroughputMode(true)
                .build();
        UpdateDatabaseDdlMetadata ddlMetadata = UpdateDatabaseDdlMetadata.newBuilder()
                .setDatabase(DATABASE)
                .addStatements(updateDdl.getStatements(0))
                .addCommitTimestamps(timestamp(1_700_000_180L))
                .setThrottled(true)
                .addProgress(progress)
                .addActions(actionInfo)
                .build();
        ListDatabaseOperationsRequest listOperations = ListDatabaseOperationsRequest.newBuilder()
                .setParent(INSTANCE_NAME)
                .setFilter("metadata.database:" + DATABASE_ID)
                .setPageSize(5)
                .build();
        ListDatabaseOperationsResponse operationsResponse = ListDatabaseOperationsResponse.newBuilder()
                .addOperations(operation)
                .setNextPageToken("next-operation-page")
                .build();
        DatabaseRole role = DatabaseRole.newBuilder().setName(DATABASE + "/databaseRoles/reader").build();
        ListDatabaseRolesRequest rolesRequest = ListDatabaseRolesRequest.newBuilder()
                .setParent(DATABASE)
                .setPageSize(2)
                .setPageToken("role-page")
                .build();
        ListDatabaseRolesResponse rolesResponse = ListDatabaseRolesResponse.newBuilder()
                .addDatabaseRoles(role)
                .setNextPageToken("next-role-page")
                .build();

        assertThat(createDatabase.getParent()).isEqualTo(INSTANCE_NAME);
        assertThat(createDatabase.getExtraStatementsList()).hasSize(1);
        assertThat(createMetadata.getDatabase()).isEqualTo(DATABASE);
        assertThat(listDatabases.getPageToken()).isEqualTo("database-page");
        assertThat(listDatabasesResponse.getDatabasesList()).containsExactly(database);
        assertThat(getDatabase.getName()).isEqualTo(DATABASE);
        assertThat(updateDatabase.getUpdateMask().getPathsList())
                .containsExactly("enable_drop_protection", "default_leader");
        assertThat(updateMetadata.getRequest()).isEqualTo(updateDatabase);
        assertThat(updateMetadata.getProgress().getProgressPercent()).isEqualTo(80);
        assertThat(dropDatabase.getDatabase()).isEqualTo(DATABASE);
        assertThat(getDdl.getDatabase()).isEqualTo(DATABASE);
        assertThat(ddlResponse.getStatementsList())
                .containsExactly("CREATE TABLE Singers (SingerId INT64 NOT NULL) PRIMARY KEY (SingerId)");
        assertThat(updateDdl.getThroughputMode()).isTrue();
        assertThat(ddlMetadata.getCommitTimestamps(0).getSeconds()).isEqualTo(1_700_000_180L);
        assertThat(ddlMetadata.getActions(0).getEntityNamesList()).containsExactly("Singers");
        assertThat(listOperations.getFilter()).contains(DATABASE_ID);
        assertThat(operationsResponse.getOperationsList()).containsExactly(operation);
        assertThat(rolesRequest.getParent()).isEqualTo(DATABASE);
        assertThat(rolesResponse.getDatabaseRolesList()).containsExactly(role);
    }

    @Test
    void backupMessagesRequestsAndMetadataPreserveEncryptionAndReferences() throws Exception {
        EncryptionInfo encryptionInfo = EncryptionInfo.newBuilder()
                .setEncryptionType(EncryptionInfo.Type.CUSTOMER_MANAGED_ENCRYPTION)
                .setEncryptionStatus(Status.newBuilder().setCode(0).setMessage("ok"))
                .setKmsKeyVersion(KMS_KEY_VERSION)
                .build();
        BackupInstancePartition partition = BackupInstancePartition.newBuilder()
                .setInstancePartition(INSTANCE_NAME + "/instancePartitions/read-only")
                .build();
        Backup backup = Backup.newBuilder()
                .setDatabase(DATABASE)
                .setVersionTime(timestamp(1_700_000_000L))
                .setExpireTime(timestamp(1_701_000_000L))
                .setName(BACKUP)
                .setCreateTime(timestamp(1_700_000_010L))
                .setSizeBytes(1024L)
                .setFreeableSizeBytes(256L)
                .setExclusiveSizeBytes(768L)
                .setState(Backup.State.READY)
                .addReferencingDatabases(DATABASE)
                .setEncryptionInfo(encryptionInfo)
                .addEncryptionInformation(encryptionInfo)
                .setDatabaseDialect(DatabaseDialect.GOOGLE_STANDARD_SQL)
                .addReferencingBackups(BackupName.format(PROJECT, INSTANCE, "copy"))
                .setMaxExpireTime(timestamp(1_702_000_000L))
                .addBackupSchedules(SCHEDULE)
                .setIncrementalBackupChainId("chain-1")
                .setOldestVersionTime(timestamp(1_699_000_000L))
                .addInstancePartitions(partition)
                .build();
        CreateBackupEncryptionConfig createEncryption = CreateBackupEncryptionConfig.newBuilder()
                .setEncryptionType(CreateBackupEncryptionConfig.EncryptionType.CUSTOMER_MANAGED_ENCRYPTION)
                .setKmsKeyName(KMS_KEY)
                .addKmsKeyNames(KMS_KEY)
                .build();
        CopyBackupEncryptionConfig copyEncryption = CopyBackupEncryptionConfig.newBuilder()
                .setEncryptionType(CopyBackupEncryptionConfig.EncryptionType.USE_CONFIG_DEFAULT_OR_BACKUP_ENCRYPTION)
                .addKmsKeyNames(KMS_KEY)
                .build();
        OperationProgress progress = OperationProgress.newBuilder().setProgressPercent(50).build();

        assertThat(Backup.parseFrom(backup.toByteArray())).isEqualTo(backup);
        assertThat(Backup.parseFrom(ByteBuffer.wrap(backup.toByteArray()))).isEqualTo(backup);
        assertThat(backup.getEncryptionInformationList()).containsExactly(encryptionInfo);
        assertThat(backup.getBackupSchedulesList()).containsExactly(SCHEDULE);
        assertThat(backup.getInstancePartitionsList()).containsExactly(partition);
        assertThat(backup.toBuilder().clearIncrementalBackupChainId().build().getIncrementalBackupChainId()).isEmpty();

        CreateBackupRequest createBackup = CreateBackupRequest.newBuilder()
                .setParent(INSTANCE_NAME)
                .setBackupId(BACKUP_ID)
                .setBackup(backup)
                .setEncryptionConfig(createEncryption)
                .build();
        CreateBackupMetadata createMetadata = CreateBackupMetadata.newBuilder()
                .setName(BACKUP)
                .setDatabase(DATABASE)
                .setProgress(progress)
                .setCancelTime(timestamp(1_700_000_100L))
                .build();
        CopyBackupRequest copyBackup = CopyBackupRequest.newBuilder()
                .setParent(INSTANCE_NAME)
                .setBackupId("copy")
                .setSourceBackup(BACKUP)
                .setExpireTime(timestamp(1_702_000_000L))
                .setEncryptionConfig(copyEncryption)
                .build();
        CopyBackupMetadata copyMetadata = CopyBackupMetadata.newBuilder()
                .setName(BackupName.format(PROJECT, INSTANCE, "copy"))
                .setSourceBackup(BACKUP)
                .setProgress(progress)
                .setCancelTime(timestamp(1_700_000_100L))
                .build();
        FieldMask backupMask = FieldMask.newBuilder().addPaths("expire_time").build();
        UpdateBackupRequest updateBackup = UpdateBackupRequest.newBuilder()
                .setBackup(backup)
                .setUpdateMask(backupMask)
                .build();
        GetBackupRequest getBackup = GetBackupRequest.newBuilder().setName(BACKUP).build();
        ListBackupsRequest listBackups = ListBackupsRequest.newBuilder()
                .setParent(INSTANCE_NAME)
                .setFilter("state:READY")
                .setPageSize(3)
                .setPageToken("backup-page")
                .build();
        ListBackupsResponse backupsResponse = ListBackupsResponse.newBuilder()
                .addBackups(backup)
                .setNextPageToken("next-backup-page")
                .build();
        DeleteBackupRequest deleteBackup = DeleteBackupRequest.newBuilder().setName(BACKUP).build();
        Operation backupOperation = Operation.newBuilder().setName(BACKUP + "/operations/create").setDone(true).build();
        ListBackupOperationsRequest listBackupOperations = ListBackupOperationsRequest.newBuilder()
                .setParent(INSTANCE_NAME)
                .setFilter("metadata.name:" + BACKUP_ID)
                .setPageSize(2)
                .build();
        ListBackupOperationsResponse operationsResponse = ListBackupOperationsResponse.newBuilder()
                .addOperations(backupOperation)
                .setNextPageToken("next-backup-operation-page")
                .build();

        assertThat(createBackup.getBackupId()).isEqualTo(BACKUP_ID);
        assertThat(createBackup.getEncryptionConfig().getEncryptionType())
                .isEqualTo(CreateBackupEncryptionConfig.EncryptionType.CUSTOMER_MANAGED_ENCRYPTION);
        assertThat(createMetadata.getProgress()).isEqualTo(progress);
        assertThat(copyBackup.getSourceBackup()).isEqualTo(BACKUP);
        assertThat(copyBackup.getEncryptionConfig().getEncryptionType())
                .isEqualTo(CopyBackupEncryptionConfig.EncryptionType.USE_CONFIG_DEFAULT_OR_BACKUP_ENCRYPTION);
        assertThat(copyMetadata.getSourceBackup()).isEqualTo(BACKUP);
        assertThat(updateBackup.getUpdateMask()).isEqualTo(backupMask);
        assertThat(getBackup.getName()).isEqualTo(BACKUP);
        assertThat(listBackups.getFilter()).isEqualTo("state:READY");
        assertThat(backupsResponse.getBackupsList()).containsExactly(backup);
        assertThat(deleteBackup.getName()).isEqualTo(BACKUP);
        assertThat(listBackupOperations.getFilter()).contains(BACKUP_ID);
        assertThat(operationsResponse.getOperationsList()).containsExactly(backupOperation);
    }

    @Test
    void backupSchedulesSupportCronSpecsBackupTypeOneofsAndCrudRequests() {
        CrontabSpec crontab = CrontabSpec.newBuilder()
                .setText("0 2 * * *")
                .setTimeZone("America/Los_Angeles")
                .setCreationWindow(Duration.newBuilder().setSeconds(14_400L))
                .build();
        BackupScheduleSpec scheduleSpec = BackupScheduleSpec.newBuilder()
                .setCronSpec(crontab)
                .build();
        CreateBackupEncryptionConfig encryption = CreateBackupEncryptionConfig.newBuilder()
                .setEncryptionType(CreateBackupEncryptionConfig.EncryptionType.USE_DATABASE_ENCRYPTION)
                .build();
        BackupSchedule fullSchedule = BackupSchedule.newBuilder()
                .setName(SCHEDULE)
                .setSpec(scheduleSpec)
                .setRetentionDuration(Duration.newBuilder().setSeconds(604_800L))
                .setEncryptionConfig(encryption)
                .setFullBackupSpec(FullBackupSpec.newBuilder())
                .setUpdateTime(timestamp(1_700_000_000L))
                .build();
        BackupSchedule incrementalSchedule = fullSchedule.toBuilder()
                .setIncrementalBackupSpec(IncrementalBackupSpec.newBuilder())
                .build();

        assertThat(scheduleSpec.getScheduleSpecCase()).isEqualTo(BackupScheduleSpec.ScheduleSpecCase.CRON_SPEC);
        assertThat(fullSchedule.getBackupTypeSpecCase()).isEqualTo(BackupSchedule.BackupTypeSpecCase.FULL_BACKUP_SPEC);
        assertThat(fullSchedule.getSpec().getCronSpec().getCreationWindow().getSeconds()).isEqualTo(14_400L);
        assertThat(fullSchedule.getRetentionDuration().getSeconds()).isEqualTo(604_800L);
        assertThat(incrementalSchedule.getBackupTypeSpecCase())
                .isEqualTo(BackupSchedule.BackupTypeSpecCase.INCREMENTAL_BACKUP_SPEC);
        assertThat(incrementalSchedule.hasFullBackupSpec()).isFalse();
        assertThat(incrementalSchedule.hasIncrementalBackupSpec()).isTrue();

        CreateBackupScheduleRequest createRequest = CreateBackupScheduleRequest.newBuilder()
                .setParent(DATABASE)
                .setBackupScheduleId(SCHEDULE_ID)
                .setBackupSchedule(fullSchedule)
                .build();
        FieldMask updateMask = FieldMask.newBuilder().addPaths("retention_duration").build();
        UpdateBackupScheduleRequest updateRequest = UpdateBackupScheduleRequest.newBuilder()
                .setBackupSchedule(incrementalSchedule)
                .setUpdateMask(updateMask)
                .build();
        GetBackupScheduleRequest getRequest = GetBackupScheduleRequest.newBuilder().setName(SCHEDULE).build();
        ListBackupSchedulesRequest listRequest = ListBackupSchedulesRequest.newBuilder()
                .setParent(DATABASE)
                .setPageSize(4)
                .setPageToken("schedule-page")
                .build();
        ListBackupSchedulesResponse listResponse = ListBackupSchedulesResponse.newBuilder()
                .addBackupSchedules(fullSchedule)
                .setNextPageToken("next-schedule-page")
                .build();
        DeleteBackupScheduleRequest deleteRequest = DeleteBackupScheduleRequest.newBuilder().setName(SCHEDULE).build();

        assertThat(createRequest.getParent()).isEqualTo(DATABASE);
        assertThat(createRequest.getBackupScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(updateRequest.getUpdateMask()).isEqualTo(updateMask);
        assertThat(updateRequest.getBackupSchedule().getBackupTypeSpecCase())
                .isEqualTo(BackupSchedule.BackupTypeSpecCase.INCREMENTAL_BACKUP_SPEC);
        assertThat(getRequest.getName()).isEqualTo(SCHEDULE);
        assertThat(listRequest.getPageToken()).isEqualTo("schedule-page");
        assertThat(listResponse.getBackupSchedulesList()).containsExactly(fullSchedule);
        assertThat(deleteRequest.getName()).isEqualTo(SCHEDULE);
    }

    @Test
    void internalGraphOperationUpdatesCarryProgressStatusAndServiceDescriptor() throws Exception {
        Status status = Status.newBuilder()
                .setCode(0)
                .setMessage("graph operation updated")
                .build();
        InternalUpdateGraphOperationRequest request = InternalUpdateGraphOperationRequest.newBuilder()
                .setDatabase(DATABASE)
                .setOperationId("graph-operation")
                .setVmIdentityToken("identity-token")
                .setProgress(0.75D)
                .setStatus(status)
                .build();
        Descriptors.MethodDescriptor method = SpannerDatabaseAdminProto.getDescriptor()
                .findServiceByName("DatabaseAdmin")
                .findMethodByName("InternalUpdateGraphOperation");
        InternalUpdateGraphOperationResponse response = InternalUpdateGraphOperationResponse.newBuilder().build();

        assertThat(method.getInputType().getFullName())
                .isEqualTo("google.spanner.admin.database.v1.InternalUpdateGraphOperationRequest");
        assertThat(method.getOutputType().getFullName())
                .isEqualTo("google.spanner.admin.database.v1.InternalUpdateGraphOperationResponse");
        assertThat(InternalUpdateGraphOperationRequest.getDescriptor().getFields())
                .extracting(Descriptors.FieldDescriptor::getName)
                .containsExactly("database", "operation_id", "vm_identity_token", "progress", "status");
        assertThat(request.getDatabase()).isEqualTo(DATABASE);
        assertThat(request.getOperationId()).isEqualTo("graph-operation");
        assertThat(request.getVmIdentityToken()).isEqualTo("identity-token");
        assertThat(request.getProgress()).isEqualTo(0.75D);
        assertThat(request.hasStatus()).isTrue();
        assertThat(request.getStatus()).isEqualTo(status);
        assertThat(request.toBuilder().clearStatus().setProgress(1.0D).build())
                .satisfies(updatedRequest -> {
                    assertThat(updatedRequest.hasStatus()).isFalse();
                    assertThat(updatedRequest.getProgress()).isEqualTo(1.0D);
                });
        assertThat(response.isInitialized()).isTrue();
        assertThat(InternalUpdateGraphOperationResponse.getDescriptor().getFields()).isEmpty();
    }

    @Test
    void addSplitPointsResponseIsEmptyAcknowledgementForDatabaseAdminRpc() {
        Descriptors.MethodDescriptor method = SpannerDatabaseAdminProto.getDescriptor()
                .findServiceByName("DatabaseAdmin")
                .findMethodByName("AddSplitPoints");
        AddSplitPointsResponse response = AddSplitPointsResponse.newBuilder().build();

        assertThat(method.getOutputType().getFullName())
                .isEqualTo(AddSplitPointsResponse.getDescriptor().getFullName());
        assertThat(method.isClientStreaming()).isFalse();
        assertThat(method.isServerStreaming()).isFalse();
        assertThat(AddSplitPointsResponse.getDescriptor().getFields()).isEmpty();
        assertThat(response.isInitialized()).isTrue();
        assertThat(response.getDefaultInstanceForType()).isEqualTo(AddSplitPointsResponse.getDefaultInstance());
        assertThat(response.toBuilder().build()).isEqualTo(response);
    }

    @Test
    void restoreDatabaseAndSplitPointMessagesTrackOneofsProgressAndKeys() {
        BackupInfo backupInfo = BackupInfo.newBuilder()
                .setBackup(BACKUP)
                .setVersionTime(timestamp(1_700_000_000L))
                .setSourceDatabase(DATABASE)
                .build();
        RestoreDatabaseEncryptionConfig encryptionConfig = RestoreDatabaseEncryptionConfig.newBuilder()
                .setEncryptionType(RestoreDatabaseEncryptionConfig.EncryptionType.CUSTOMER_MANAGED_ENCRYPTION)
                .setKmsKeyName(KMS_KEY)
                .addKmsKeyNames(KMS_KEY)
                .build();
        RestoreDatabaseRequest restoreRequest = RestoreDatabaseRequest.newBuilder()
                .setParent(INSTANCE_NAME)
                .setDatabaseId("restored-db")
                .setBackup(BACKUP)
                .setEncryptionConfig(encryptionConfig)
                .build();
        OperationProgress progress = OperationProgress.newBuilder()
                .setProgressPercent(100)
                .setStartTime(timestamp(1_700_000_000L))
                .setEndTime(timestamp(1_700_000_300L))
                .build();
        RestoreDatabaseMetadata restoreMetadata = RestoreDatabaseMetadata.newBuilder()
                .setName(DatabaseName.format(PROJECT, INSTANCE, "restored-db"))
                .setSourceType(RestoreSourceType.BACKUP)
                .setBackupInfo(backupInfo)
                .setProgress(progress)
                .setCancelTime(timestamp(1_700_000_120L))
                .setOptimizeDatabaseOperationName(DATABASE + "/operations/optimize")
                .build();
        OptimizeRestoredDatabaseMetadata optimizeMetadata = OptimizeRestoredDatabaseMetadata.newBuilder()
                .setName(restoreMetadata.getName())
                .setProgress(progress)
                .build();
        SplitPoints.Key singerKey = SplitPoints.Key.newBuilder()
                .setKeyParts(ListValue.newBuilder()
                        .addValues(Value.newBuilder().setStringValue("singer-1"))
                        .addValues(Value.newBuilder().setNumberValue(10D)))
                .build();
        SplitPoints splitPoints = SplitPoints.newBuilder()
                .setTable("Singers")
                .setIndex("SingersByName")
                .addKeys(singerKey)
                .setExpireTime(timestamp(1_700_086_400L))
                .build();
        AddSplitPointsRequest addSplitPoints = AddSplitPointsRequest.newBuilder()
                .setDatabase(DATABASE)
                .addSplitPoints(splitPoints)
                .setInitiator("integration-test")
                .build();

        assertThat(restoreRequest.getSourceCase()).isEqualTo(RestoreDatabaseRequest.SourceCase.BACKUP);
        assertThat(restoreRequest.getEncryptionConfig().getEncryptionType())
                .isEqualTo(RestoreDatabaseEncryptionConfig.EncryptionType.CUSTOMER_MANAGED_ENCRYPTION);
        assertThat(restoreMetadata.getSourceInfoCase()).isEqualTo(RestoreDatabaseMetadata.SourceInfoCase.BACKUP_INFO);
        assertThat(restoreMetadata.getSourceType()).isEqualTo(RestoreSourceType.BACKUP);
        assertThat(restoreMetadata.getProgress().getProgressPercent()).isEqualTo(100);
        assertThat(restoreMetadata.getOptimizeDatabaseOperationName()).endsWith("/operations/optimize");
        assertThat(optimizeMetadata.getProgress()).isEqualTo(progress);
        assertThat(splitPoints.getKeys(0).getKeyParts().getValues(0).getStringValue()).isEqualTo("singer-1");
        assertThat(addSplitPoints.getDatabase()).isEqualTo(DATABASE);
        assertThat(addSplitPoints.getSplitPointsList()).containsExactly(splitPoints);
        assertThat(addSplitPoints.getInitiator()).isEqualTo("integration-test");
    }

    private static Database sampleDatabase() {
        return Database.newBuilder()
                .setName(DATABASE)
                .setState(Database.State.READY)
                .setCreateTime(timestamp(1_700_000_000L))
                .setEncryptionConfig(EncryptionConfig.newBuilder().setKmsKeyName(KMS_KEY))
                .addEncryptionInfo(EncryptionInfo.newBuilder()
                        .setEncryptionType(EncryptionInfo.Type.CUSTOMER_MANAGED_ENCRYPTION)
                        .setKmsKeyVersion(KMS_KEY_VERSION))
                .setVersionRetentionPeriod("7d")
                .setDefaultLeader("us-central1")
                .setDatabaseDialect(DatabaseDialect.GOOGLE_STANDARD_SQL)
                .setEnableDropProtection(true)
                .build();
    }

    private static Timestamp timestamp(long seconds) {
        return Timestamp.newBuilder().setSeconds(seconds).build();
    }
}
