/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_cloud_secretmanager_v1;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32C;

import com.google.cloud.secretmanager.v1.AccessSecretVersionRequest;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.AddSecretVersionRequest;
import com.google.cloud.secretmanager.v1.CreateSecretRequest;
import com.google.cloud.secretmanager.v1.CustomerManagedEncryption;
import com.google.cloud.secretmanager.v1.CustomerManagedEncryptionStatus;
import com.google.cloud.secretmanager.v1.DeleteSecretRequest;
import com.google.cloud.secretmanager.v1.DestroySecretVersionRequest;
import com.google.cloud.secretmanager.v1.DisableSecretVersionRequest;
import com.google.cloud.secretmanager.v1.EnableSecretVersionRequest;
import com.google.cloud.secretmanager.v1.GetSecretRequest;
import com.google.cloud.secretmanager.v1.GetSecretVersionRequest;
import com.google.cloud.secretmanager.v1.ListSecretVersionsRequest;
import com.google.cloud.secretmanager.v1.ListSecretVersionsResponse;
import com.google.cloud.secretmanager.v1.ListSecretsRequest;
import com.google.cloud.secretmanager.v1.ListSecretsResponse;
import com.google.cloud.secretmanager.v1.LocationName;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Replication;
import com.google.cloud.secretmanager.v1.ReplicationStatus;
import com.google.cloud.secretmanager.v1.ResourcesProto;
import com.google.cloud.secretmanager.v1.Rotation;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.cloud.secretmanager.v1.ServiceProto;
import com.google.cloud.secretmanager.v1.Topic;
import com.google.cloud.secretmanager.v1.UpdateSecretRequest;
import com.google.iam.v1.Binding;
import com.google.iam.v1.GetIamPolicyRequest;
import com.google.iam.v1.GetPolicyOptions;
import com.google.iam.v1.Policy;
import com.google.iam.v1.SetIamPolicyRequest;
import com.google.iam.v1.TestIamPermissionsRequest;
import com.google.iam.v1.TestIamPermissionsResponse;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Duration;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Proto_google_cloud_secretmanager_v1Test {
    private static final String PROJECT = "sample-project";
    private static final String LOCATION = "us-central1";
    private static final String SECOND_LOCATION = "europe-west1";
    private static final String SECRET = "database-password";
    private static final String VERSION = "5";
    private static final String SECRET_NAME = SecretName.format(PROJECT, SECRET);
    private static final String REGIONAL_SECRET_NAME = SecretName.formatProjectLocationSecretName(
            PROJECT, LOCATION, SECRET);
    private static final String SECRET_VERSION_NAME = SecretVersionName.format(PROJECT, SECRET, VERSION);
    private static final String REGIONAL_SECRET_VERSION_NAME =
            SecretVersionName.formatProjectLocationSecretSecretVersionName(PROJECT, LOCATION, SECRET, VERSION);

    @Test
    void resourceNamesRoundTripThroughGeneratedHelpers() {
        ProjectName projectName = ProjectName.of(PROJECT);
        LocationName locationName = LocationName.of(PROJECT, LOCATION);
        SecretName secretName = SecretName.of(PROJECT, SECRET);
        SecretName regionalSecretName = SecretName.ofProjectLocationSecretName(PROJECT, LOCATION, SECRET);
        SecretVersionName secretVersionName = SecretVersionName.of(PROJECT, SECRET, VERSION);
        SecretVersionName regionalVersionName = SecretVersionName.ofProjectLocationSecretSecretVersionName(
                PROJECT, LOCATION, SECRET, VERSION);

        assertThat(projectName.toString()).isEqualTo("projects/" + PROJECT);
        assertThat(ProjectName.parse(projectName.toString())).isEqualTo(projectName);
        assertThat(projectName.toBuilder().setProject("other-project").build().toString())
                .isEqualTo("projects/other-project");

        assertThat(locationName.toString()).isEqualTo("projects/" + PROJECT + "/locations/" + LOCATION);
        assertThat(LocationName.parse(locationName.toString()).getFieldValuesMap())
                .containsEntry("project", PROJECT)
                .containsEntry("location", LOCATION);
        assertThat(LocationName.isParsableFrom(locationName.toString())).isTrue();

        assertThat(secretName.toString()).isEqualTo(SECRET_NAME);
        assertThat(SecretName.parse(SECRET_NAME).getSecret()).isEqualTo(SECRET);
        assertThat(SecretName.parse(REGIONAL_SECRET_NAME).getLocation()).isEqualTo(LOCATION);
        assertThat(regionalSecretName.getFieldValue("secret")).isEqualTo(SECRET);
        assertThat(SecretName.toStringList(List.of(secretName, regionalSecretName)))
                .containsExactly(SECRET_NAME, REGIONAL_SECRET_NAME);
        assertThat(SecretName.parseList(List.of(SECRET_NAME, REGIONAL_SECRET_NAME)))
                .containsExactly(secretName, regionalSecretName);

        assertThat(secretVersionName.toString()).isEqualTo(SECRET_VERSION_NAME);
        assertThat(SecretVersionName.parse(SECRET_VERSION_NAME).getSecretVersion()).isEqualTo(VERSION);
        assertThat(SecretVersionName.parse(REGIONAL_SECRET_VERSION_NAME).getLocation()).isEqualTo(LOCATION);
        assertThat(regionalVersionName.getFieldValuesMap())
                .containsEntry("project", PROJECT)
                .containsEntry("location", LOCATION)
                .containsEntry("secret", SECRET)
                .containsEntry("secret_version", VERSION);
    }

    @Test
    void descriptorsExposeSecretResourcesAndServiceMethods() {
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        ResourcesProto.registerAllExtensions(registry);
        ServiceProto.registerAllExtensions(registry);

        Descriptors.FileDescriptor resourcesDescriptor = ResourcesProto.getDescriptor();
        Descriptors.FileDescriptor serviceDescriptor = ServiceProto.getDescriptor();

        assertThat(resourcesDescriptor.findMessageTypeByName("Secret").getFields())
                .extracting(Descriptors.FieldDescriptor::getName)
                .contains(
                        "name",
                        "replication",
                        "labels",
                        "topics",
                        "ttl",
                        "version_aliases",
                        "annotations",
                        "customer_managed_encryption");
        assertThat(resourcesDescriptor.findMessageTypeByName("Secret")
                .findFieldByName("replication")
                .getMessageType()
                .getFullName())
                .isEqualTo("google.cloud.secretmanager.v1.Replication");
        assertThat(resourcesDescriptor.findMessageTypeByName("Replication")
                .getOneofs()
                .get(0)
                .getFields())
                .extracting(Descriptors.FieldDescriptor::getName)
                .containsExactly("automatic", "user_managed");
        assertThat(serviceDescriptor.findServiceByName("SecretManagerService").getMethods())
                .extracting(Descriptors.MethodDescriptor::getName)
                .containsExactly(
                        "ListSecrets",
                        "CreateSecret",
                        "AddSecretVersion",
                        "GetSecret",
                        "UpdateSecret",
                        "DeleteSecret",
                        "ListSecretVersions",
                        "GetSecretVersion",
                        "AccessSecretVersion",
                        "DisableSecretVersion",
                        "EnableSecretVersion",
                        "DestroySecretVersion",
                        "SetIamPolicy",
                        "GetIamPolicy",
                        "TestIamPermissions");
    }

    @Test
    void buildsIamPolicyRequestsForSecretResources() {
        Binding accessorBinding = Binding.newBuilder()
                .setRole("roles/secretmanager.secretAccessor")
                .addMembers("serviceAccount:secret-reader@" + PROJECT + ".iam.gserviceaccount.com")
                .build();
        Policy policy = Policy.newBuilder()
                .setVersion(1)
                .addBindings(accessorBinding)
                .setEtag(ByteString.copyFromUtf8("policy-etag"))
                .build();
        FieldMask updateMask = FieldMask.newBuilder()
                .addPaths("bindings")
                .addPaths("etag")
                .build();
        SetIamPolicyRequest setIamPolicy = SetIamPolicyRequest.newBuilder()
                .setResource(SECRET_NAME)
                .setPolicy(policy)
                .setUpdateMask(updateMask)
                .build();
        GetIamPolicyRequest getIamPolicy = GetIamPolicyRequest.newBuilder()
                .setResource(SECRET_NAME)
                .setOptions(GetPolicyOptions.newBuilder().setRequestedPolicyVersion(1))
                .build();
        TestIamPermissionsRequest testIamPermissions = TestIamPermissionsRequest.newBuilder()
                .setResource(SECRET_NAME)
                .addPermissions("secretmanager.versions.access")
                .addPermissions("secretmanager.secrets.get")
                .build();
        TestIamPermissionsResponse testIamPermissionsResponse = TestIamPermissionsResponse.newBuilder()
                .addPermissions("secretmanager.versions.access")
                .build();

        assertThat(policy.getBindingsList()).containsExactly(accessorBinding);
        assertThat(policy.getEtag().toStringUtf8()).isEqualTo("policy-etag");
        assertThat(setIamPolicy.getResource()).isEqualTo(SECRET_NAME);
        assertThat(setIamPolicy.getPolicy().getBindings(0).getRole())
                .isEqualTo("roles/secretmanager.secretAccessor");
        assertThat(setIamPolicy.getUpdateMask().getPathsList()).containsExactly("bindings", "etag");
        assertThat(getIamPolicy.getOptions().getRequestedPolicyVersion()).isEqualTo(1);
        assertThat(testIamPermissions.getPermissionsList())
                .containsExactly("secretmanager.versions.access", "secretmanager.secrets.get");
        assertThat(testIamPermissionsResponse.getPermissionsList())
                .containsExactly("secretmanager.versions.access");
    }

    @Test
    void buildsAutomaticSecretWithMetadataMapsTopicsRotationAndExpirationOneof() {
        Timestamp createTime = Timestamp.newBuilder().setSeconds(1_700_000_000L).build();
        Timestamp expireTime = Timestamp.newBuilder().setSeconds(1_800_000_000L).build();
        Timestamp nextRotationTime = Timestamp.newBuilder().setSeconds(1_710_000_000L).build();
        Duration rotationPeriod = Duration.newBuilder().setSeconds(2_592_000L).build();
        Duration destroyTtl = Duration.newBuilder().setSeconds(86_400L).build();
        CustomerManagedEncryption encryption = CustomerManagedEncryption.newBuilder()
                .setKmsKeyName(kmsKeyName(LOCATION))
                .build();
        Replication automaticReplication = Replication.newBuilder()
                .setAutomatic(Replication.Automatic.newBuilder()
                        .setCustomerManagedEncryption(encryption))
                .build();
        Rotation rotation = Rotation.newBuilder()
                .setNextRotationTime(nextRotationTime)
                .setRotationPeriod(rotationPeriod)
                .build();
        Topic topic = Topic.newBuilder()
                .setName("projects/" + PROJECT + "/topics/secret-updates")
                .build();

        Secret secret = Secret.newBuilder()
                .setName(SECRET_NAME)
                .setReplication(automaticReplication)
                .setCreateTime(createTime)
                .putLabels("env", "test")
                .putLabels("owner", "security")
                .addTopics(topic)
                .setExpireTime(expireTime)
                .setEtag("secret-etag")
                .setRotation(rotation)
                .putVersionAliases("current", 5L)
                .putVersionAliases("previous", 4L)
                .putAnnotations("purpose", "integration-test")
                .setVersionDestroyTtl(destroyTtl)
                .setCustomerManagedEncryption(encryption)
                .build();

        assertThat(secret.getName()).isEqualTo(SECRET_NAME);
        assertThat(secret.getReplication().getReplicationCase()).isEqualTo(Replication.ReplicationCase.AUTOMATIC);
        assertThat(secret.getReplication().getAutomatic().getCustomerManagedEncryption().getKmsKeyName())
                .isEqualTo(kmsKeyName(LOCATION));
        assertThat(secret.getLabelsMap()).containsEntry("env", "test").containsEntry("owner", "security");
        assertThat(secret.getTopicsList()).containsExactly(topic);
        assertThat(secret.getExpirationCase()).isEqualTo(Secret.ExpirationCase.EXPIRE_TIME);
        assertThat(secret.getExpireTime()).isEqualTo(expireTime);
        assertThat(secret.getVersionAliasesMap()).containsEntry("current", 5L).containsEntry("previous", 4L);
        assertThat(secret.getAnnotationsOrThrow("purpose")).isEqualTo("integration-test");
        assertThat(secret.getRotation().getRotationPeriod()).isEqualTo(rotationPeriod);
        assertThat(secret.getVersionDestroyTtl()).isEqualTo(destroyTtl);
        assertThat(secret.toBuilder().setTtl(Duration.newBuilder().setSeconds(3_600L)).build().getExpirationCase())
                .isEqualTo(Secret.ExpirationCase.TTL);
    }

    @Test
    void buildsUserManagedReplicationAndSecretVersionStatus() {
        CustomerManagedEncryption primaryEncryption = CustomerManagedEncryption.newBuilder()
                .setKmsKeyName(kmsKeyName(LOCATION))
                .build();
        CustomerManagedEncryption secondaryEncryption = CustomerManagedEncryption.newBuilder()
                .setKmsKeyName(kmsKeyName(SECOND_LOCATION))
                .build();
        Replication.UserManaged.Replica primaryReplica = Replication.UserManaged.Replica.newBuilder()
                .setLocation(LOCATION)
                .setCustomerManagedEncryption(primaryEncryption)
                .build();
        Replication.UserManaged.Replica secondaryReplica = Replication.UserManaged.Replica.newBuilder()
                .setLocation(SECOND_LOCATION)
                .setCustomerManagedEncryption(secondaryEncryption)
                .build();
        Replication userManagedReplication = Replication.newBuilder()
                .setUserManaged(Replication.UserManaged.newBuilder()
                        .addReplicas(primaryReplica)
                        .addReplicas(secondaryReplica))
                .build();
        CustomerManagedEncryptionStatus primaryStatus = CustomerManagedEncryptionStatus.newBuilder()
                .setKmsKeyVersionName(kmsKeyVersionName(LOCATION))
                .build();
        CustomerManagedEncryptionStatus secondaryStatus = CustomerManagedEncryptionStatus.newBuilder()
                .setKmsKeyVersionName(kmsKeyVersionName(SECOND_LOCATION))
                .build();
        ReplicationStatus.UserManagedStatus.ReplicaStatus primaryReplicaStatus =
                ReplicationStatus.UserManagedStatus.ReplicaStatus.newBuilder()
                        .setLocation(LOCATION)
                        .setCustomerManagedEncryption(primaryStatus)
                        .build();
        ReplicationStatus.UserManagedStatus.ReplicaStatus secondaryReplicaStatus =
                ReplicationStatus.UserManagedStatus.ReplicaStatus.newBuilder()
                        .setLocation(SECOND_LOCATION)
                        .setCustomerManagedEncryption(secondaryStatus)
                        .build();
        ReplicationStatus replicationStatus = ReplicationStatus.newBuilder()
                .setUserManaged(ReplicationStatus.UserManagedStatus.newBuilder()
                        .addReplicas(primaryReplicaStatus)
                        .addReplicas(secondaryReplicaStatus))
                .build();
        Timestamp createTime = Timestamp.newBuilder().setSeconds(1_700_000_100L).build();
        Timestamp scheduledDestroyTime = Timestamp.newBuilder().setSeconds(1_700_086_500L).build();

        SecretVersion secretVersion = SecretVersion.newBuilder()
                .setName(REGIONAL_SECRET_VERSION_NAME)
                .setCreateTime(createTime)
                .setState(SecretVersion.State.ENABLED)
                .setReplicationStatus(replicationStatus)
                .setEtag("version-etag")
                .setClientSpecifiedPayloadChecksum(true)
                .setScheduledDestroyTime(scheduledDestroyTime)
                .setCustomerManagedEncryption(primaryStatus)
                .build();

        assertThat(userManagedReplication.getReplicationCase()).isEqualTo(Replication.ReplicationCase.USER_MANAGED);
        assertThat(userManagedReplication.getUserManaged().getReplicasList())
                .containsExactly(primaryReplica, secondaryReplica);
        assertThat(userManagedReplication.getUserManaged().getReplicas(0).getCustomerManagedEncryption())
                .isEqualTo(primaryEncryption);
        assertThat(secretVersion.getName()).isEqualTo(REGIONAL_SECRET_VERSION_NAME);
        assertThat(secretVersion.getState()).isEqualTo(SecretVersion.State.ENABLED);
        assertThat(secretVersion.getStateValue()).isEqualTo(SecretVersion.State.ENABLED.getNumber());
        assertThat(secretVersion.getReplicationStatus().getReplicationStatusCase())
                .isEqualTo(ReplicationStatus.ReplicationStatusCase.USER_MANAGED);
        assertThat(secretVersion.getReplicationStatus().getUserManaged().getReplicasList())
                .containsExactly(primaryReplicaStatus, secondaryReplicaStatus);
        assertThat(secretVersion.getClientSpecifiedPayloadChecksum()).isTrue();
        assertThat(secretVersion.getScheduledDestroyTime()).isEqualTo(scheduledDestroyTime);
        assertThat(secretVersion.toBuilder().setState(SecretVersion.State.DISABLED).build().getState())
                .isEqualTo(SecretVersion.State.DISABLED);
    }

    @Test
    void buildsAutomaticReplicationStatusForSecretVersion() {
        CustomerManagedEncryptionStatus encryptionStatus = CustomerManagedEncryptionStatus.newBuilder()
                .setKmsKeyVersionName(kmsKeyVersionName(LOCATION))
                .build();
        ReplicationStatus.AutomaticStatus automaticStatus = ReplicationStatus.AutomaticStatus.newBuilder()
                .setCustomerManagedEncryption(encryptionStatus)
                .build();
        ReplicationStatus replicationStatus = ReplicationStatus.newBuilder()
                .setAutomatic(automaticStatus)
                .build();

        SecretVersion secretVersion = SecretVersion.newBuilder()
                .setName(SECRET_VERSION_NAME)
                .setState(SecretVersion.State.DESTROYED)
                .setReplicationStatus(replicationStatus)
                .build();

        assertThat(replicationStatus.getReplicationStatusCase())
                .isEqualTo(ReplicationStatus.ReplicationStatusCase.AUTOMATIC);
        assertThat(replicationStatus.hasAutomatic()).isTrue();
        assertThat(replicationStatus.getAutomatic()).isEqualTo(automaticStatus);
        assertThat(replicationStatus.getAutomatic().getCustomerManagedEncryption().getKmsKeyVersionName())
                .isEqualTo(kmsKeyVersionName(LOCATION));
        assertThat(secretVersion.hasReplicationStatus()).isTrue();
        assertThat(secretVersion.getReplicationStatus()).isEqualTo(replicationStatus);
        assertThat(secretVersion.getState()).isEqualTo(SecretVersion.State.DESTROYED);
        assertThat(replicationStatus.toBuilder().clearAutomatic().build().getReplicationStatusCase())
                .isEqualTo(ReplicationStatus.ReplicationStatusCase.REPLICATIONSTATUS_NOT_SET);
    }

    @Test
    void buildsSecretManagerRequestsAndResponses() {
        Secret secret = Secret.newBuilder()
                .setName(SECRET_NAME)
                .setReplication(Replication.newBuilder().setAutomatic(Replication.Automatic.newBuilder()))
                .putLabels("env", "test")
                .build();
        SecretPayload payload = payload("super-secret-value");
        SecretVersion version = SecretVersion.newBuilder()
                .setName(SECRET_VERSION_NAME)
                .setState(SecretVersion.State.ENABLED)
                .build();
        FieldMask updateMask = FieldMask.newBuilder().addPaths("labels").addPaths("topics").build();

        ListSecretsRequest listSecrets = ListSecretsRequest.newBuilder()
                .setParent(ProjectName.format(PROJECT))
                .setPageSize(20)
                .setPageToken("secret-page")
                .setFilter("labels.env:test")
                .build();
        ListSecretsResponse listSecretsResponse = ListSecretsResponse.newBuilder()
                .addSecrets(secret)
                .setNextPageToken("next-secret-page")
                .setTotalSize(1)
                .build();
        CreateSecretRequest createSecret = CreateSecretRequest.newBuilder()
                .setParent(ProjectName.format(PROJECT))
                .setSecretId(SECRET)
                .setSecret(secret)
                .build();
        UpdateSecretRequest updateSecret = UpdateSecretRequest.newBuilder()
                .setSecret(secret.toBuilder().putLabels("rotated", "true"))
                .setUpdateMask(updateMask)
                .build();
        AddSecretVersionRequest addVersion = AddSecretVersionRequest.newBuilder()
                .setParent(SECRET_NAME)
                .setPayload(payload)
                .build();
        GetSecretRequest getSecret = GetSecretRequest.newBuilder().setName(SECRET_NAME).build();
        DeleteSecretRequest deleteSecret = DeleteSecretRequest.newBuilder()
                .setName(SECRET_NAME)
                .setEtag("secret-etag")
                .build();
        ListSecretVersionsRequest listVersions = ListSecretVersionsRequest.newBuilder()
                .setParent(SECRET_NAME)
                .setPageSize(10)
                .setPageToken("version-page")
                .setFilter("state=ENABLED")
                .build();
        ListSecretVersionsResponse listVersionsResponse = ListSecretVersionsResponse.newBuilder()
                .addVersions(version)
                .setNextPageToken("next-version-page")
                .setTotalSize(1)
                .build();
        GetSecretVersionRequest getVersion = GetSecretVersionRequest.newBuilder()
                .setName(SECRET_VERSION_NAME)
                .build();
        AccessSecretVersionRequest accessVersion = AccessSecretVersionRequest.newBuilder()
                .setName(SECRET_VERSION_NAME)
                .build();
        AccessSecretVersionResponse accessResponse = AccessSecretVersionResponse.newBuilder()
                .setName(SECRET_VERSION_NAME)
                .setPayload(payload)
                .build();
        DisableSecretVersionRequest disableVersion = DisableSecretVersionRequest.newBuilder()
                .setName(SECRET_VERSION_NAME)
                .setEtag("version-etag")
                .build();
        EnableSecretVersionRequest enableVersion = EnableSecretVersionRequest.newBuilder()
                .setName(SECRET_VERSION_NAME)
                .setEtag("version-etag")
                .build();
        DestroySecretVersionRequest destroyVersion = DestroySecretVersionRequest.newBuilder()
                .setName(SECRET_VERSION_NAME)
                .setEtag("version-etag")
                .build();

        assertThat(listSecrets.getParent()).isEqualTo(ProjectName.format(PROJECT));
        assertThat(listSecrets.getFilter()).isEqualTo("labels.env:test");
        assertThat(listSecretsResponse.getSecretsList()).containsExactly(secret);
        assertThat(listSecretsResponse.getTotalSize()).isEqualTo(1);
        assertThat(createSecret.getSecretId()).isEqualTo(SECRET);
        assertThat(updateSecret.getUpdateMask().getPathsList()).containsExactly("labels", "topics");
        assertThat(addVersion.getPayload()).isEqualTo(payload);
        assertThat(getSecret.getName()).isEqualTo(SECRET_NAME);
        assertThat(deleteSecret.getEtag()).isEqualTo("secret-etag");
        assertThat(listVersions.getFilter()).isEqualTo("state=ENABLED");
        assertThat(listVersionsResponse.getVersionsList()).containsExactly(version);
        assertThat(getVersion.getName()).isEqualTo(SECRET_VERSION_NAME);
        assertThat(accessVersion.getName()).isEqualTo(SECRET_VERSION_NAME);
        assertThat(accessResponse.getPayload().getData().toStringUtf8()).isEqualTo("super-secret-value");
        assertThat(disableVersion.getEtag()).isEqualTo("version-etag");
        assertThat(enableVersion.getName()).isEqualTo(SECRET_VERSION_NAME);
        assertThat(destroyVersion.getName()).isEqualTo(SECRET_VERSION_NAME);
    }

    @Test
    void payloadParsersHandleByteStringDelimitedStreamsAndDefaultInstances() throws Exception {
        SecretPayload payload = payload("parse-me");

        SecretPayload fromBytes = SecretPayload.parseFrom(payload.toByteArray());
        SecretPayload fromByteString = SecretPayload.parseFrom(payload.toByteString());
        SecretPayload fromParser = SecretPayload.parser().parseFrom(payload.toByteArray());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        payload.writeDelimitedTo(output);
        SecretPayload fromDelimitedStream = SecretPayload.parseDelimitedFrom(
                new ByteArrayInputStream(output.toByteArray()));

        assertThat(fromBytes).isEqualTo(payload);
        assertThat(fromByteString).isEqualTo(payload);
        assertThat(fromParser).isEqualTo(payload);
        assertThat(fromDelimitedStream).isEqualTo(payload);
        assertThat(SecretPayload.getDefaultInstance().hasDataCrc32C()).isFalse();
        assertThat(SecretPayload.getDefaultInstance().getParserForType().parseFrom(payload.toByteArray()))
                .isEqualTo(payload);
    }

    private static SecretPayload payload(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        CRC32C crc32C = new CRC32C();
        crc32C.update(bytes, 0, bytes.length);
        return SecretPayload.newBuilder()
                .setData(ByteString.copyFrom(bytes))
                .setDataCrc32C(crc32C.getValue())
                .build();
    }

    private static String kmsKeyName(String location) {
        return "projects/" + PROJECT + "/locations/" + location + "/keyRings/test-ring/cryptoKeys/test-key";
    }

    private static String kmsKeyVersionName(String location) {
        return kmsKeyName(location) + "/cryptoKeyVersions/1";
    }
}
