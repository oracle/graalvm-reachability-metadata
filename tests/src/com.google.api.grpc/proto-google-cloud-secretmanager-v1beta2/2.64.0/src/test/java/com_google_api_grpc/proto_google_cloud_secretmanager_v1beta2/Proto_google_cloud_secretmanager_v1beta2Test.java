/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_cloud_secretmanager_v1beta2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.cloud.secretmanager.v1beta2.AccessSecretVersionRequest;
import com.google.cloud.secretmanager.v1beta2.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1beta2.AddSecretVersionRequest;
import com.google.cloud.secretmanager.v1beta2.CreateSecretRequest;
import com.google.cloud.secretmanager.v1beta2.CustomerManagedEncryption;
import com.google.cloud.secretmanager.v1beta2.CustomerManagedEncryptionStatus;
import com.google.cloud.secretmanager.v1beta2.DeleteSecretRequest;
import com.google.cloud.secretmanager.v1beta2.DestroySecretVersionRequest;
import com.google.cloud.secretmanager.v1beta2.DisableSecretVersionRequest;
import com.google.cloud.secretmanager.v1beta2.EnableSecretVersionRequest;
import com.google.cloud.secretmanager.v1beta2.GetSecretRequest;
import com.google.cloud.secretmanager.v1beta2.GetSecretVersionRequest;
import com.google.cloud.secretmanager.v1beta2.ListSecretVersionsRequest;
import com.google.cloud.secretmanager.v1beta2.ListSecretVersionsResponse;
import com.google.cloud.secretmanager.v1beta2.ListSecretsRequest;
import com.google.cloud.secretmanager.v1beta2.ListSecretsResponse;
import com.google.cloud.secretmanager.v1beta2.LocationName;
import com.google.cloud.secretmanager.v1beta2.ProjectName;
import com.google.cloud.secretmanager.v1beta2.Replication;
import com.google.cloud.secretmanager.v1beta2.ReplicationStatus;
import com.google.cloud.secretmanager.v1beta2.Rotation;
import com.google.cloud.secretmanager.v1beta2.Secret;
import com.google.cloud.secretmanager.v1beta2.SecretName;
import com.google.cloud.secretmanager.v1beta2.SecretPayload;
import com.google.cloud.secretmanager.v1beta2.SecretVersion;
import com.google.cloud.secretmanager.v1beta2.SecretVersionName;
import com.google.cloud.secretmanager.v1beta2.Topic;
import com.google.cloud.secretmanager.v1beta2.UpdateSecretRequest;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Timestamp;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32C;
import org.junit.jupiter.api.Test;

public class Proto_google_cloud_secretmanager_v1beta2Test {
    private static final String PROJECT = "test-project";
    private static final String LOCATION = "europe-west1";
    private static final String SECRET_ID = "database-password";
    private static final String VERSION = "3";
    private static final String SECRET_NAME = "projects/test-project/secrets/database-password";
    private static final String REGIONAL_SECRET_NAME =
            "projects/test-project/locations/europe-west1/secrets/database-password";
    private static final String VERSION_NAME = "projects/test-project/secrets/database-password/versions/3";
    private static final String REGIONAL_VERSION_NAME =
            "projects/test-project/locations/europe-west1/secrets/database-password/versions/3";
    private static final String KMS_KEY =
            "projects/test-project/locations/europe-west1/keyRings/ring/cryptoKeys/key";
    private static final String KMS_KEY_VERSION =
            "projects/test-project/locations/europe-west1/keyRings/ring/cryptoKeys/key/cryptoKeyVersions/7";

    @Test
    void resourceNameHelpersFormatParseAndRoundTripLists() {
        ProjectName projectName = ProjectName.of(PROJECT);
        LocationName locationName = LocationName.of(PROJECT, LOCATION);
        SecretName globalSecret = SecretName.ofProjectSecretName(PROJECT, SECRET_ID);
        SecretName regionalSecret = SecretName.ofProjectLocationSecretName(PROJECT, LOCATION, SECRET_ID);
        SecretVersionName globalVersion =
                SecretVersionName.ofProjectSecretSecretVersionName(PROJECT, SECRET_ID, VERSION);
        SecretVersionName regionalVersion =
                SecretVersionName.ofProjectLocationSecretSecretVersionName(PROJECT, LOCATION, SECRET_ID, VERSION);

        assertThat(projectName.toString()).isEqualTo("projects/test-project");
        assertThat(projectName.getFieldValuesMap()).containsEntry("project", PROJECT);
        assertThat(locationName.toString()).isEqualTo("projects/test-project/locations/europe-west1");
        assertThat(locationName.getFieldValue("location")).isEqualTo(LOCATION);

        assertThat(globalSecret.toString()).isEqualTo(SECRET_NAME);
        assertThat(globalSecret.getProject()).isEqualTo(PROJECT);
        assertThat(globalSecret.getLocation()).isNull();
        assertThat(globalSecret.getSecret()).isEqualTo(SECRET_ID);
        assertThat(regionalSecret.toString()).isEqualTo(REGIONAL_SECRET_NAME);
        assertThat(regionalSecret.getLocation()).isEqualTo(LOCATION);
        assertThat(SecretName.formatProjectSecretName(PROJECT, SECRET_ID)).isEqualTo(SECRET_NAME);
        assertThat(SecretName.formatProjectLocationSecretName(PROJECT, LOCATION, SECRET_ID))
                .isEqualTo(REGIONAL_SECRET_NAME);
        assertThat(SecretName.parse(REGIONAL_SECRET_NAME)).isEqualTo(regionalSecret);
        assertThat(SecretName.isParsableFrom(REGIONAL_SECRET_NAME)).isTrue();
        assertThat(SecretName.isParsableFrom("projects/test-project/topics/not-a-secret")).isFalse();

        assertThat(globalVersion.toString()).isEqualTo(VERSION_NAME);
        assertThat(globalVersion.getSecretVersion()).isEqualTo(VERSION);
        assertThat(regionalVersion.toString()).isEqualTo(REGIONAL_VERSION_NAME);
        assertThat(regionalVersion.getLocation()).isEqualTo(LOCATION);
        assertThat(SecretVersionName.parse(REGIONAL_VERSION_NAME)).isEqualTo(regionalVersion);
        assertThat(SecretVersionName.toStringList(List.of(globalVersion, regionalVersion)))
                .containsExactly(VERSION_NAME, REGIONAL_VERSION_NAME);
        assertThat(SecretVersionName.parseList(List.of(VERSION_NAME, REGIONAL_VERSION_NAME)))
                .containsExactly(globalVersion, regionalVersion);
    }

    @Test
    void secretBuilderModelsReplicationExpirationMapsTopicsAndRotation() {
        Timestamp createTime = Timestamp.newBuilder().setSeconds(1_700_000_000L).setNanos(123_000_000).build();
        Timestamp expireTime = Timestamp.newBuilder().setSeconds(1_800_000_000L).build();
        Timestamp nextRotation = Timestamp.newBuilder().setSeconds(1_750_000_000L).build();
        Duration ttl = Duration.newBuilder().setSeconds(86_400L).build();
        Duration rotationPeriod = Duration.newBuilder().setSeconds(2_592_000L).build();
        Duration destroyTtl = Duration.newBuilder().setSeconds(604_800L).build();
        CustomerManagedEncryption encryption = CustomerManagedEncryption.newBuilder().setKmsKeyName(KMS_KEY).build();
        Replication automaticReplication = Replication.newBuilder()
                .setAutomatic(Replication.Automatic.newBuilder().setCustomerManagedEncryption(encryption))
                .build();
        Rotation rotation = Rotation.newBuilder()
                .setNextRotationTime(nextRotation)
                .setRotationPeriod(rotationPeriod)
                .build();
        Topic topic = Topic.newBuilder()
                .setName("projects/test-project/topics/secret-events")
                .build();

        Secret secret = Secret.newBuilder()
                .setName(REGIONAL_SECRET_NAME)
                .setReplication(automaticReplication)
                .setCreateTime(createTime)
                .putLabels("env", "test")
                .putLabels("component", "payments")
                .addTopics(topic)
                .setExpireTime(expireTime)
                .setTtl(ttl)
                .setEtag("etag-1")
                .setRotation(rotation)
                .putVersionAliases("current", 3L)
                .putVersionAliases("previous", 2L)
                .putAnnotations("owner", "platform")
                .setVersionDestroyTtl(destroyTtl)
                .setCustomerManagedEncryption(encryption)
                .build();

        assertThat(secret.getName()).isEqualTo(REGIONAL_SECRET_NAME);
        assertThat(secret.getReplication().getReplicationCase()).isEqualTo(Replication.ReplicationCase.AUTOMATIC);
        assertThat(secret.getReplication().getAutomatic().getCustomerManagedEncryption().getKmsKeyName())
                .isEqualTo(KMS_KEY);
        assertThat(secret.getCreateTime()).isEqualTo(createTime);
        assertThat(secret.getLabelsMap()).containsEntry("env", "test").containsEntry("component", "payments");
        assertThat(secret.getTopicsList()).containsExactly(topic);
        assertThat(secret.getExpirationCase()).isEqualTo(Secret.ExpirationCase.TTL);
        assertThat(secret.hasExpireTime()).isFalse();
        assertThat(secret.getTtl()).isEqualTo(ttl);
        assertThat(secret.getRotation()).isEqualTo(rotation);
        assertThat(secret.getVersionAliasesMap()).containsEntry("current", 3L).containsEntry("previous", 2L);
        assertThat(secret.getAnnotationsMap()).containsEntry("owner", "platform");
        assertThat(secret.getVersionDestroyTtl()).isEqualTo(destroyTtl);
        assertThat(secret.getCustomerManagedEncryption()).isEqualTo(encryption);
        assertThat(secret.toBuilder().clearTtl().setExpireTime(expireTime).build().getExpirationCase())
                .isEqualTo(Secret.ExpirationCase.EXPIRE_TIME);
        assertThatThrownBy(() -> secret.getLabelsMap().put("mutated", "nope"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void userManagedReplicationAndVersionStatusCapturePerReplicaEncryptionState() {
        CustomerManagedEncryption encryption = CustomerManagedEncryption.newBuilder().setKmsKeyName(KMS_KEY).build();
        CustomerManagedEncryptionStatus encryptionStatus = CustomerManagedEncryptionStatus.newBuilder()
                .setKmsKeyVersionName(KMS_KEY_VERSION)
                .build();
        Replication.UserManaged.Replica replica = Replication.UserManaged.Replica.newBuilder()
                .setLocation(LOCATION)
                .setCustomerManagedEncryption(encryption)
                .build();
        Replication userManagedReplication = Replication.newBuilder()
                .setUserManaged(Replication.UserManaged.newBuilder().addReplicas(replica))
                .build();
        ReplicationStatus.UserManagedStatus.ReplicaStatus replicaStatus =
                ReplicationStatus.UserManagedStatus.ReplicaStatus.newBuilder()
                        .setLocation(LOCATION)
                        .setCustomerManagedEncryption(encryptionStatus)
                        .build();
        ReplicationStatus replicationStatus = ReplicationStatus.newBuilder()
                .setUserManaged(ReplicationStatus.UserManagedStatus.newBuilder().addReplicas(replicaStatus))
                .build();
        Timestamp createTime = Timestamp.newBuilder().setSeconds(1_700_000_001L).build();
        Timestamp scheduledDestroyTime = Timestamp.newBuilder().setSeconds(1_700_100_001L).build();

        SecretVersion version = SecretVersion.newBuilder()
                .setName(REGIONAL_VERSION_NAME)
                .setCreateTime(createTime)
                .setState(SecretVersion.State.DISABLED)
                .setReplicationStatus(replicationStatus)
                .setEtag("version-etag")
                .setClientSpecifiedPayloadChecksum(true)
                .setScheduledDestroyTime(scheduledDestroyTime)
                .setCustomerManagedEncryption(encryptionStatus)
                .build();

        assertThat(userManagedReplication.getReplicationCase()).isEqualTo(Replication.ReplicationCase.USER_MANAGED);
        assertThat(userManagedReplication.getUserManaged().getReplicasList()).containsExactly(replica);
        assertThat(userManagedReplication.getUserManaged()
                        .getReplicas(0)
                        .getCustomerManagedEncryption()
                        .getKmsKeyName())
                .isEqualTo(KMS_KEY);
        assertThat(version.getName()).isEqualTo(REGIONAL_VERSION_NAME);
        assertThat(version.getState()).isEqualTo(SecretVersion.State.DISABLED);
        assertThat(version.getClientSpecifiedPayloadChecksum()).isTrue();
        assertThat(version.getScheduledDestroyTime()).isEqualTo(scheduledDestroyTime);
        assertThat(version.getReplicationStatus().getReplicationStatusCase())
                .isEqualTo(ReplicationStatus.ReplicationStatusCase.USER_MANAGED);
        assertThat(version.getReplicationStatus().getUserManaged().getReplicas(0).getCustomerManagedEncryption())
                .isEqualTo(encryptionStatus);
        assertThat(version.getCustomerManagedEncryption().getKmsKeyVersionName()).isEqualTo(KMS_KEY_VERSION);
        assertThat(version.toBuilder().setState(SecretVersion.State.ENABLED).build().getState())
                .isEqualTo(SecretVersion.State.ENABLED);
    }

    @Test
    void payloadRequestsAndResponsesKeepBinaryDataAndChecksum() {
        byte[] secretBytes = "s3cr3t-value".getBytes(StandardCharsets.UTF_8);
        ByteString data = ByteString.copyFrom(secretBytes);
        CRC32C crc32c = new CRC32C();
        crc32c.update(secretBytes, 0, secretBytes.length);
        SecretPayload payload = SecretPayload.newBuilder()
                .setData(data)
                .setDataCrc32C(crc32c.getValue())
                .build();

        AddSecretVersionRequest addRequest = AddSecretVersionRequest.newBuilder()
                .setParent(REGIONAL_SECRET_NAME)
                .setPayload(payload)
                .build();
        AccessSecretVersionRequest accessRequest = AccessSecretVersionRequest.newBuilder()
                .setName(REGIONAL_VERSION_NAME)
                .build();
        AccessSecretVersionResponse accessResponse = AccessSecretVersionResponse.newBuilder()
                .setName(REGIONAL_VERSION_NAME)
                .setPayload(payload)
                .build();

        assertThat(payload.getData()).isEqualTo(data);
        assertThat(payload.getData().toStringUtf8()).isEqualTo("s3cr3t-value");
        assertThat(payload.getDataCrc32C()).isEqualTo(crc32c.getValue());
        assertThat(addRequest.getParent()).isEqualTo(REGIONAL_SECRET_NAME);
        assertThat(addRequest.hasPayload()).isTrue();
        assertThat(addRequest.getPayload()).isEqualTo(payload);
        assertThat(accessRequest.getName()).isEqualTo(REGIONAL_VERSION_NAME);
        assertThat(accessResponse.hasPayload()).isTrue();
        assertThat(accessResponse.getPayload().getData()).isEqualTo(data);
        assertThat(accessResponse.toBuilder().clearPayload().build().hasPayload()).isFalse();
    }

    @Test
    void serviceRequestMessagesRepresentSecretLifecycleOperations() {
        Secret secret = Secret.newBuilder()
                .setName(REGIONAL_SECRET_NAME)
                .setReplication(Replication.newBuilder().setAutomatic(Replication.Automatic.getDefaultInstance()))
                .putLabels("purpose", "integration-test")
                .build();
        FieldMask updateMask = FieldMask.newBuilder()
                .addPaths("labels")
                .addPaths("version_aliases")
                .build();

        CreateSecretRequest createRequest = CreateSecretRequest.newBuilder()
                .setParent(LocationName.format(PROJECT, LOCATION))
                .setSecretId(SECRET_ID)
                .setSecret(secret)
                .build();
        GetSecretRequest getRequest = GetSecretRequest.newBuilder().setName(REGIONAL_SECRET_NAME).build();
        ListSecretsRequest listRequest = ListSecretsRequest.newBuilder()
                .setParent(LocationName.format(PROJECT, LOCATION))
                .setPageSize(25)
                .setPageToken("page-1")
                .setFilter("labels.purpose=integration-test")
                .build();
        UpdateSecretRequest updateRequest = UpdateSecretRequest.newBuilder()
                .setSecret(secret.toBuilder().putVersionAliases("current", 3L))
                .setUpdateMask(updateMask)
                .build();
        DeleteSecretRequest deleteRequest = DeleteSecretRequest.newBuilder()
                .setName(REGIONAL_SECRET_NAME)
                .setEtag("secret-etag")
                .build();
        ListSecretsResponse listResponse = ListSecretsResponse.newBuilder()
                .addSecrets(secret)
                .setNextPageToken("page-2")
                .setTotalSize(1)
                .build();

        assertThat(createRequest.getParent()).isEqualTo("projects/test-project/locations/europe-west1");
        assertThat(createRequest.getSecretId()).isEqualTo(SECRET_ID);
        assertThat(createRequest.getSecret()).isEqualTo(secret);
        assertThat(getRequest.getName()).isEqualTo(REGIONAL_SECRET_NAME);
        assertThat(listRequest.getPageSize()).isEqualTo(25);
        assertThat(listRequest.getPageToken()).isEqualTo("page-1");
        assertThat(listRequest.getFilter()).isEqualTo("labels.purpose=integration-test");
        assertThat(updateRequest.hasSecret()).isTrue();
        assertThat(updateRequest.getSecret().getVersionAliasesOrThrow("current")).isEqualTo(3L);
        assertThat(updateRequest.getUpdateMask().getPathsList()).containsExactly("labels", "version_aliases");
        assertThat(deleteRequest.getEtag()).isEqualTo("secret-etag");
        assertThat(listResponse.getSecretsList()).containsExactly(secret);
        assertThat(listResponse.getNextPageToken()).isEqualTo("page-2");
        assertThat(listResponse.getTotalSize()).isEqualTo(1);
    }

    @Test
    void serviceRequestMessagesRepresentVersionLifecycleOperations() {
        SecretVersion enabledVersion = SecretVersion.newBuilder()
                .setName(REGIONAL_VERSION_NAME)
                .setState(SecretVersion.State.ENABLED)
                .setEtag("version-etag")
                .build();
        SecretVersion destroyedVersion = enabledVersion.toBuilder()
                .setState(SecretVersion.State.DESTROYED)
                .build();

        ListSecretVersionsRequest listRequest = ListSecretVersionsRequest.newBuilder()
                .setParent(REGIONAL_SECRET_NAME)
                .setPageSize(10)
                .setPageToken("versions-page-1")
                .setFilter("state:ENABLED")
                .build();
        GetSecretVersionRequest getRequest = GetSecretVersionRequest.newBuilder()
                .setName(REGIONAL_VERSION_NAME)
                .build();
        DisableSecretVersionRequest disableRequest = DisableSecretVersionRequest.newBuilder()
                .setName(REGIONAL_VERSION_NAME)
                .setEtag("version-etag")
                .build();
        EnableSecretVersionRequest enableRequest = EnableSecretVersionRequest.newBuilder()
                .setName(REGIONAL_VERSION_NAME)
                .setEtag("version-etag")
                .build();
        DestroySecretVersionRequest destroyRequest = DestroySecretVersionRequest.newBuilder()
                .setName(REGIONAL_VERSION_NAME)
                .setEtag("version-etag")
                .build();
        ListSecretVersionsResponse listResponse = ListSecretVersionsResponse.newBuilder()
                .addVersions(enabledVersion)
                .addVersions(destroyedVersion)
                .setNextPageToken("versions-page-2")
                .setTotalSize(2)
                .build();

        assertThat(listRequest.getParent()).isEqualTo(REGIONAL_SECRET_NAME);
        assertThat(listRequest.getPageSize()).isEqualTo(10);
        assertThat(listRequest.getFilter()).isEqualTo("state:ENABLED");
        assertThat(getRequest.getName()).isEqualTo(REGIONAL_VERSION_NAME);
        assertThat(disableRequest.getName()).isEqualTo(REGIONAL_VERSION_NAME);
        assertThat(enableRequest.getEtag()).isEqualTo("version-etag");
        assertThat(destroyRequest.getEtag()).isEqualTo("version-etag");
        assertThat(listResponse.getVersionsList()).containsExactly(enabledVersion, destroyedVersion);
        assertThat(listResponse.getVersions(1).getState()).isEqualTo(SecretVersion.State.DESTROYED);
        assertThat(listResponse.getTotalSize()).isEqualTo(2);
    }
}
