/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_secretmanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.core.BackgroundResource;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcCallContext;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.PageContext;
import com.google.api.gax.rpc.PagedListDescriptor;
import com.google.api.gax.rpc.UnaryCallable;
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
import com.google.cloud.secretmanager.v1.Rotation;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.cloud.secretmanager.v1.Topic;
import com.google.cloud.secretmanager.v1.UpdateSecretRequest;
import com.google.cloud.secretmanager.v1.stub.SecretManagerServiceStub;
import com.google.iam.v1.Binding;
import com.google.iam.v1.GetIamPolicyRequest;
import com.google.iam.v1.Policy;
import com.google.iam.v1.SetIamPolicyRequest;
import com.google.iam.v1.TestIamPermissionsRequest;
import com.google.iam.v1.TestIamPermissionsResponse;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Timestamp;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class Google_cloud_secretmanagerTest {
    private static final String PROJECT = "secret-project";
    private static final String LOCATION = "us-central1";
    private static final String SECRET = "database-password";
    private static final String VERSION = "7";
    private static final String SECRET_ID = "database-password";
    private static final String LOCAL_ENDPOINT = "localhost:1";
    private static final String KMS_KEY =
            "projects/secret-project/locations/us-central1/keyRings/app/cryptoKeys/secrets";

    @Test
    void resourceNamesAndSecretModelsPreserveConfiguredFields() {
        ProjectName projectName = ProjectName.of(PROJECT);
        LocationName locationName = LocationName.of(PROJECT, LOCATION);
        SecretName secretName = SecretName.of(PROJECT, SECRET);
        SecretName regionalSecretName = SecretName.ofProjectLocationSecretName(PROJECT, LOCATION, SECRET);
        SecretVersionName versionName = SecretVersionName.of(PROJECT, SECRET, VERSION);
        SecretVersionName regionalVersionName =
                SecretVersionName.ofProjectLocationSecretSecretVersionName(PROJECT, LOCATION, SECRET, VERSION);
        Timestamp createTime = Timestamp.newBuilder().setSeconds(1_700_000_000L).build();
        Rotation rotation = Rotation.newBuilder()
                .setNextRotationTime(Timestamp.newBuilder().setSeconds(1_700_086_400L).build())
                .setRotationPeriod(com.google.protobuf.Duration.newBuilder().setSeconds(86_400).build())
                .build();
        Replication replication = Replication.newBuilder()
                .setUserManaged(Replication.UserManaged.newBuilder()
                        .addReplicas(Replication.UserManaged.Replica.newBuilder()
                                .setLocation(LOCATION)
                                .setCustomerManagedEncryption(CustomerManagedEncryption.newBuilder()
                                        .setKmsKeyName(KMS_KEY)
                                        .build())
                                .build())
                        .addReplicas(Replication.UserManaged.Replica.newBuilder()
                                .setLocation("us-east1")
                                .build())
                        .build())
                .build();
        Secret secret = Secret.newBuilder()
                .setName(regionalSecretName.toString())
                .setReplication(replication)
                .setCreateTime(createTime)
                .putLabels("env", "test")
                .putAnnotations("owner", "platform")
                .putVersionAliases("current", Long.parseLong(VERSION))
                .addTopics(Topic.newBuilder().setName("projects/secret-project/topics/secret-events"))
                .setTtl(com.google.protobuf.Duration.newBuilder().setSeconds(604_800).build())
                .setVersionDestroyTtl(com.google.protobuf.Duration.newBuilder().setSeconds(86_400).build())
                .setRotation(rotation)
                .setEtag("etag-1")
                .build();

        assertThat(projectName.toString()).isEqualTo("projects/" + PROJECT);
        assertThat(locationName.toString()).isEqualTo("projects/" + PROJECT + "/locations/" + LOCATION);
        assertThat(secretName.toString()).isEqualTo("projects/" + PROJECT + "/secrets/" + SECRET);
        assertThat(regionalSecretName.toString())
                .isEqualTo("projects/" + PROJECT + "/locations/" + LOCATION + "/secrets/" + SECRET);
        assertThat(SecretName.parse(regionalSecretName.toString()).getLocation()).isEqualTo(LOCATION);
        assertThat(SecretName.isParsableFrom(secretName.toString())).isTrue();
        assertThat(versionName.toString())
                .isEqualTo("projects/" + PROJECT + "/secrets/" + SECRET + "/versions/" + VERSION);
        assertThat(SecretVersionName.parse(regionalVersionName.toString()).getSecretVersion()).isEqualTo(VERSION);
        assertThat(secret.getReplication().getUserManaged().getReplicasList())
                .extracting(Replication.UserManaged.Replica::getLocation)
                .containsExactly(LOCATION, "us-east1");
        assertThat(secret.getReplication().getUserManaged().getReplicas(0)
                        .getCustomerManagedEncryption().getKmsKeyName())
                .isEqualTo(KMS_KEY);
        assertThat(secret.getLabelsMap()).containsEntry("env", "test");
        assertThat(secret.getAnnotationsMap()).containsEntry("owner", "platform");
        assertThat(secret.getVersionAliasesMap()).containsEntry("current", Long.parseLong(VERSION));
        assertThat(secret.getTopics(0).getName()).endsWith("secret-events");
        assertThat(secret.getRotation().getRotationPeriod().getSeconds()).isEqualTo(86_400L);
        assertThat(secret.toBuilder().putLabels("team", "security").build().getLabelsMap())
                .containsEntry("team", "security");
    }

    @Test
    void requestPayloadAndSecretVersionMessagesExposeLifecycleConfiguration() {
        SecretName secretName = SecretName.of(PROJECT, SECRET);
        SecretVersionName versionName = SecretVersionName.of(PROJECT, SECRET, VERSION);
        SecretPayload payload = SecretPayload.newBuilder()
                .setData(ByteString.copyFromUtf8("correct-horse-battery-staple"))
                .setDataCrc32C(2_755_162_048L)
                .build();
        ReplicationStatus replicationStatus = ReplicationStatus.newBuilder()
                .setUserManaged(ReplicationStatus.UserManagedStatus.newBuilder()
                        .addReplicas(ReplicationStatus.UserManagedStatus.ReplicaStatus.newBuilder()
                                .setLocation(LOCATION)
                                .setCustomerManagedEncryption(CustomerManagedEncryptionStatus.newBuilder()
                                        .setKmsKeyVersionName(KMS_KEY + "/cryptoKeyVersions/1")
                                        .build())
                                .build())
                        .build())
                .build();
        SecretVersion secretVersion = SecretVersion.newBuilder()
                .setName(versionName.toString())
                .setCreateTime(Timestamp.newBuilder().setSeconds(1_700_000_100L).build())
                .setState(SecretVersion.State.ENABLED)
                .setReplicationStatus(replicationStatus)
                .setEtag("version-etag")
                .setClientSpecifiedPayloadChecksum(true)
                .setScheduledDestroyTime(Timestamp.newBuilder().setSeconds(1_800_000_000L).build())
                .setCustomerManagedEncryption(CustomerManagedEncryptionStatus.newBuilder()
                        .setKmsKeyVersionName(KMS_KEY + "/cryptoKeyVersions/1")
                        .build())
                .build();
        FieldMask labelsAndRotation = FieldMask.newBuilder()
                .addPaths("labels")
                .addPaths("rotation")
                .build();

        assertThat(CreateSecretRequest.newBuilder()
                .setParent(ProjectName.of(PROJECT).toString())
                .setSecretId(SECRET_ID)
                .setSecret(sampleSecret())
                .build()
                .getSecretId()).isEqualTo(SECRET_ID);
        assertThat(AddSecretVersionRequest.newBuilder()
                .setParent(secretName.toString())
                .setPayload(payload)
                .build()
                .getPayload()
                .getData()
                .toStringUtf8()).contains("horse");
        assertThat(GetSecretRequest.newBuilder().setName(secretName.toString()).build().getName())
                .isEqualTo(secretName.toString());
        assertThat(UpdateSecretRequest.newBuilder()
                .setSecret(sampleSecret().toBuilder().putLabels("updated", "true"))
                .setUpdateMask(labelsAndRotation)
                .build()
                .getUpdateMask()
                .getPathsList()).containsExactly("labels", "rotation");
        assertThat(ListSecretsRequest.newBuilder()
                .setParent(ProjectName.of(PROJECT).toString())
                .setFilter("labels.env=test")
                .setPageSize(5)
                .build()
                .getFilter()).isEqualTo("labels.env=test");
        assertThat(ListSecretVersionsRequest.newBuilder()
                .setParent(secretName.toString())
                .setFilter("state=ENABLED")
                .setPageToken("next")
                .build()
                .getPageToken()).isEqualTo("next");
        assertThat(AccessSecretVersionResponse.newBuilder()
                .setName(versionName.toString())
                .setPayload(payload)
                .build()
                .getPayload()
                .getDataCrc32C()).isEqualTo(2_755_162_048L);
        assertThat(secretVersion.getState()).isEqualTo(SecretVersion.State.ENABLED);
        assertThat(secretVersion.getReplicationStatus().getUserManaged().getReplicas(0).getLocation())
                .isEqualTo(LOCATION);
        assertThat(secretVersion.getClientSpecifiedPayloadChecksum()).isTrue();
        assertThat(secretVersion.toBuilder().setState(SecretVersion.State.DISABLED).build().getState())
                .isEqualTo(SecretVersion.State.DISABLED);
    }

    @Test
    void settingsBuildersCreateGrpcAndHttpJsonClientsWithoutApplicationCredentials()
            throws IOException, InterruptedException {
        SecretManagerServiceSettings.Builder grpcBuilder = SecretManagerServiceSettings.newBuilder()
                .setEndpoint(LOCAL_ENDPOINT)
                .setCredentialsProvider(NoCredentialsProvider.create());
        grpcBuilder.listSecretsSettings().setRetrySettings(
                grpcBuilder.listSecretsSettings().getRetrySettings().toBuilder()
                        .setMaxAttempts(2)
                        .setTotalTimeoutDuration(Duration.ofSeconds(3))
                        .build());
        SecretManagerServiceSettings grpcSettings = grpcBuilder.build();
        SecretManagerServiceSettings httpJsonSettings = SecretManagerServiceSettings.newHttpJsonBuilder()
                .setEndpoint("http://localhost:1")
                .setCredentialsProvider(NoCredentialsProvider.create())
                .build();

        assertThat(SecretManagerServiceSettings.getDefaultEndpoint()).isEqualTo("secretmanager.googleapis.com:443");
        assertThat(SecretManagerServiceSettings.getDefaultServiceScopes())
                .contains("https://www.googleapis.com/auth/cloud-platform");
        assertThat(grpcSettings.getEndpoint()).isEqualTo(LOCAL_ENDPOINT);
        assertThat(grpcSettings.getCredentialsProvider().getCredentials()).isNull();
        assertThat(grpcSettings.listSecretsSettings().getRetrySettings().getMaxAttempts()).isEqualTo(2);
        assertThat(grpcSettings.createSecretSettings()).isNotNull();
        assertThat(grpcSettings.accessSecretVersionSettings()).isNotNull();
        assertThat(grpcSettings.destroySecretVersionSettings()).isNotNull();
        assertThat(grpcSettings.testIamPermissionsSettings()).isNotNull();
        assertThat(httpJsonSettings.getTransportChannelProvider().getTransportName()).contains("httpjson");

        SecretManagerServiceClient client = SecretManagerServiceClient.create(grpcSettings);
        try {
            assertThat(client.getSettings()).isSameAs(grpcSettings);
            assertThat(client.getStub()).isNotNull();
        } finally {
            client.shutdownNow();
            client.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void secretManagerClientDispatchesSecretLifecycleCallsToStub() throws Exception {
        FakeSecretManagerServiceStub stub = new FakeSecretManagerServiceStub();
        SecretName secretName = SecretName.of(PROJECT, SECRET);
        SecretVersionName versionName = SecretVersionName.of(PROJECT, SECRET, VERSION);
        SecretPayload payload = SecretPayload.newBuilder().setData(ByteString.copyFromUtf8("offline-secret")).build();

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create(stub)) {
            assertThat(client.createSecret(ProjectName.of(PROJECT), SECRET_ID, sampleSecret()).getName())
                    .isEqualTo(secretName.toString());
            CreateSecretRequest createRequest = stub.createSecret.getLastRequest();
            assertThat(createRequest.getParent()).isEqualTo(ProjectName.of(PROJECT).toString());
            assertThat(createRequest.getSecretId()).isEqualTo(SECRET_ID);

            assertThat(client.addSecretVersion(secretName, payload).getName()).isEqualTo(versionName.toString());
            AddSecretVersionRequest addRequest = stub.addSecretVersion.getLastRequest();
            assertThat(addRequest.getParent()).isEqualTo(secretName.toString());
            assertThat(addRequest.getPayload().getData().toStringUtf8()).isEqualTo("offline-secret");

            assertThat(client.getSecret(secretName).getLabelsMap()).containsEntry("env", "test");
            assertThat(stub.getSecret.getLastRequest().getName()).isEqualTo(secretName.toString());

            FieldMask updateMask = FieldMask.newBuilder().addPaths("labels").build();
            assertThat(client.updateSecret(sampleSecret(), updateMask).getName()).isEqualTo(secretName.toString());
            assertThat(stub.updateSecret.getLastRequest().getUpdateMask()).isEqualTo(updateMask);

            client.deleteSecret(secretName);
            assertThat(stub.deleteSecret.getLastRequest().getName()).isEqualTo(secretName.toString());
        }

        assertThat(stub.closed).isTrue();
    }

    @Test
    void secretManagerClientListsVersionsAccessesPayloadsAndUpdatesIamPolicy() throws Exception {
        FakeSecretManagerServiceStub stub = new FakeSecretManagerServiceStub();
        SecretName secretName = SecretName.of(PROJECT, SECRET);
        SecretVersionName versionName = SecretVersionName.of(PROJECT, SECRET, VERSION);

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create(stub)) {
            assertThat(client.listSecrets(ProjectName.of(PROJECT)).iterateAll())
                    .extracting(Secret::getName)
                    .containsExactly(secretName.toString());
            assertThat(stub.listSecrets.getLastRequest().getParent()).isEqualTo(ProjectName.of(PROJECT).toString());

            assertThat(client.listSecretVersions(secretName).iterateAll())
                    .extracting(SecretVersion::getState)
                    .containsExactly(SecretVersion.State.ENABLED);
            assertThat(stub.listSecretVersions.getLastRequest().getParent()).isEqualTo(secretName.toString());

            assertThat(client.getSecretVersion(versionName).getName()).isEqualTo(versionName.toString());
            assertThat(stub.getSecretVersion.getLastRequest().getName()).isEqualTo(versionName.toString());

            assertThat(client.accessSecretVersion(versionName).getPayload().getData().toStringUtf8())
                    .isEqualTo("stored-secret");
            assertThat(stub.accessSecretVersion.getLastRequest().getName()).isEqualTo(versionName.toString());

            assertThat(client.disableSecretVersion(versionName).getState()).isEqualTo(SecretVersion.State.DISABLED);
            assertThat(client.enableSecretVersion(versionName).getState()).isEqualTo(SecretVersion.State.ENABLED);
            assertThat(client.destroySecretVersion(versionName).getState()).isEqualTo(SecretVersion.State.DESTROYED);
            assertThat(stub.destroySecretVersion.getLastRequest().getName()).isEqualTo(versionName.toString());

            Policy policy = Policy.newBuilder()
                    .addBindings(Binding.newBuilder()
                            .setRole("roles/secretmanager.secretAccessor")
                            .addMembers("serviceAccount:app@secret-project.iam.gserviceaccount.com"))
                    .build();
            Policy updatedPolicy = client.setIamPolicy(SetIamPolicyRequest.newBuilder()
                    .setResource(secretName.toString())
                    .setPolicy(policy)
                    .build());
            assertThat(updatedPolicy.getBindings(0).getRole()).isEqualTo("roles/secretmanager.secretAccessor");
            assertThat(stub.setIamPolicy.getLastRequest().getResource()).isEqualTo(secretName.toString());

            assertThat(client.getIamPolicy(GetIamPolicyRequest.newBuilder()
                    .setResource(secretName.toString())
                    .build()).getBindings(0).getMembersList())
                    .contains("serviceAccount:app@secret-project.iam.gserviceaccount.com");
            TestIamPermissionsResponse permissions = client.testIamPermissions(TestIamPermissionsRequest.newBuilder()
                    .setResource(secretName.toString())
                    .addPermissions("secretmanager.versions.access")
                    .build());
            assertThat(permissions.getPermissionsList()).containsExactly("secretmanager.versions.access");
        }
    }

    @Test
    void clientDispatchesConditionalMutationRequestsWithEtags() throws Exception {
        FakeSecretManagerServiceStub stub = new FakeSecretManagerServiceStub();
        SecretName secretName = SecretName.of(PROJECT, SECRET);
        SecretVersionName versionName = SecretVersionName.of(PROJECT, SECRET, VERSION);

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create(stub)) {
            client.deleteSecret(DeleteSecretRequest.newBuilder()
                    .setName(secretName.toString())
                    .setEtag("secret-etag")
                    .build());
            assertThat(stub.deleteSecret.getLastRequest().getName()).isEqualTo(secretName.toString());
            assertThat(stub.deleteSecret.getLastRequest().getEtag()).isEqualTo("secret-etag");

            SecretVersion disabled = client.disableSecretVersion(DisableSecretVersionRequest.newBuilder()
                    .setName(versionName.toString())
                    .setEtag("disable-etag")
                    .build());
            assertThat(disabled.getState()).isEqualTo(SecretVersion.State.DISABLED);
            assertThat(stub.disableSecretVersion.getLastRequest().getEtag()).isEqualTo("disable-etag");

            SecretVersion enabled = client.enableSecretVersion(EnableSecretVersionRequest.newBuilder()
                    .setName(versionName.toString())
                    .setEtag("enable-etag")
                    .build());
            assertThat(enabled.getState()).isEqualTo(SecretVersion.State.ENABLED);
            assertThat(stub.enableSecretVersion.getLastRequest().getEtag()).isEqualTo("enable-etag");

            SecretVersion destroyed = client.destroySecretVersion(DestroySecretVersionRequest.newBuilder()
                    .setName(versionName.toString())
                    .setEtag("destroy-etag")
                    .build());
            assertThat(destroyed.getState()).isEqualTo(SecretVersion.State.DESTROYED);
            assertThat(stub.destroySecretVersion.getLastRequest().getEtag()).isEqualTo("destroy-etag");
        }
    }

    @Test
    void legacyBetaPackagesExposeCompatibleResourceNamesAndPayloadModels() {
        com.google.cloud.secretmanager.v1beta2.SecretName beta2SecretName =
                com.google.cloud.secretmanager.v1beta2.SecretName.ofProjectLocationSecretName(
                        PROJECT, LOCATION, SECRET);
        com.google.cloud.secretmanager.v1beta2.SecretVersionName beta2VersionName =
                com.google.cloud.secretmanager.v1beta2.SecretVersionName.ofProjectLocationSecretSecretVersionName(
                        PROJECT, LOCATION, SECRET, VERSION);
        com.google.cloud.secretmanager.v1beta2.Secret beta2Secret =
                com.google.cloud.secretmanager.v1beta2.Secret.newBuilder()
                        .setName(beta2SecretName.toString())
                        .setReplication(com.google.cloud.secretmanager.v1beta2.Replication.newBuilder()
                                .setAutomatic(com.google.cloud.secretmanager.v1beta2.Replication.Automatic
                                        .getDefaultInstance()))
                        .putLabels("stage", "beta2")
                        .putAnnotations("format", "regional")
                        .build();
        com.google.cloud.secretmanager.v1beta1.SecretName beta1SecretName =
                com.google.cloud.secretmanager.v1beta1.SecretName.of(PROJECT, SECRET);
        com.google.cloud.secretmanager.v1beta1.SecretPayload beta1Payload =
                com.google.cloud.secretmanager.v1beta1.SecretPayload.newBuilder()
                        .setData(ByteString.copyFromUtf8("beta-one"))
                        .build();

        assertThat(com.google.cloud.secretmanager.v1beta2.SecretName.parse(beta2SecretName.toString()).getLocation())
                .isEqualTo(LOCATION);
        assertThat(com.google.cloud.secretmanager.v1beta2.SecretVersionName.parse(beta2VersionName.toString())
                .getSecretVersion()).isEqualTo(VERSION);
        assertThat(beta2Secret.getLabelsMap()).containsEntry("stage", "beta2");
        assertThat(beta2Secret.getAnnotationsMap()).containsEntry("format", "regional");
        assertThat(beta1SecretName.toString()).isEqualTo("projects/" + PROJECT + "/secrets/" + SECRET);
        assertThat(beta1Payload.getData().toStringUtf8()).isEqualTo("beta-one");
    }

    private static Secret sampleSecret() {
        return Secret.newBuilder()
                .setName(SecretName.of(PROJECT, SECRET).toString())
                .setReplication(Replication.newBuilder()
                        .setAutomatic(Replication.Automatic.newBuilder()
                                .setCustomerManagedEncryption(CustomerManagedEncryption.newBuilder()
                                        .setKmsKeyName(KMS_KEY)
                                        .build())
                                .build())
                        .build())
                .putLabels("env", "test")
                .setEtag("secret-etag")
                .build();
    }

    private static SecretVersion sampleVersion(SecretVersion.State state) {
        return SecretVersion.newBuilder()
                .setName(SecretVersionName.of(PROJECT, SECRET, VERSION).toString())
                .setState(state)
                .setEtag("version-etag")
                .build();
    }

    private static PagedListDescriptor<ListSecretsRequest, ListSecretsResponse, Secret> secretsPageDescriptor() {
        return new SimplePagedListDescriptor<>(
                (request, token) -> request.toBuilder().setPageToken(token).build(),
                (request, pageSize) -> request.toBuilder().setPageSize(pageSize).build(),
                ListSecretsRequest::getPageSize,
                ListSecretsResponse::getNextPageToken,
                ListSecretsResponse::getSecretsList);
    }

    private static PagedListDescriptor<ListSecretVersionsRequest, ListSecretVersionsResponse, SecretVersion>
            secretVersionsPageDescriptor() {
        return new SimplePagedListDescriptor<>(
                (request, token) -> request.toBuilder().setPageToken(token).build(),
                (request, pageSize) -> request.toBuilder().setPageSize(pageSize).build(),
                ListSecretVersionsRequest::getPageSize,
                ListSecretVersionsResponse::getNextPageToken,
                ListSecretVersionsResponse::getVersionsList);
    }

    private static Policy samplePolicy() {
        return Policy.newBuilder()
                .addBindings(Binding.newBuilder()
                        .setRole("roles/secretmanager.secretAccessor")
                        .addMembers("serviceAccount:app@secret-project.iam.gserviceaccount.com"))
                .build();
    }

    private static final class CapturingUnaryCallable<RequestT, ResponseT> extends UnaryCallable<RequestT, ResponseT> {
        private final ResponseT response;
        private RequestT lastRequest;

        private CapturingUnaryCallable(ResponseT response) {
            this.response = response;
        }

        @Override
        public ApiFuture<ResponseT> futureCall(RequestT request, ApiCallContext context) {
            lastRequest = request;
            return ApiFutures.immediateFuture(response);
        }

        private RequestT getLastRequest() {
            return lastRequest;
        }
    }

    @FunctionalInterface
    private interface PagedResponseFactory<RequestT, ResponseT, ResourceT, PagedResponseT> {
        ApiFuture<PagedResponseT> create(
                PageContext<RequestT, ResponseT, ResourceT> pageContext,
                ApiFuture<ResponseT> responseFuture);
    }

    private static final class CapturingPagedCallable<RequestT, ResponseT, ResourceT, PagedResponseT>
            extends UnaryCallable<RequestT, PagedResponseT> {
        private final CapturingUnaryCallable<RequestT, ResponseT> responseCallable;
        private final PagedListDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor;
        private final PagedResponseFactory<RequestT, ResponseT, ResourceT, PagedResponseT> responseFactory;

        private CapturingPagedCallable(
                ResponseT response,
                PagedListDescriptor<RequestT, ResponseT, ResourceT> pageDescriptor,
                PagedResponseFactory<RequestT, ResponseT, ResourceT, PagedResponseT> responseFactory) {
            this.responseCallable = new CapturingUnaryCallable<>(response);
            this.pageDescriptor = pageDescriptor;
            this.responseFactory = responseFactory;
        }

        @Override
        public ApiFuture<PagedResponseT> futureCall(RequestT request, ApiCallContext context) {
            ApiCallContext callContext = context == null ? GrpcCallContext.createDefault() : context;
            ApiFuture<ResponseT> responseFuture = responseCallable.futureCall(request, callContext);
            PageContext<RequestT, ResponseT, ResourceT> pageContext =
                    PageContext.create(responseCallable, pageDescriptor, request, callContext);
            return responseFactory.create(pageContext, responseFuture);
        }

        private RequestT getLastRequest() {
            return responseCallable.getLastRequest();
        }
    }

    private static final class SimplePagedListDescriptor<RequestT, ResponseT, ResourceT>
            implements PagedListDescriptor<RequestT, ResponseT, ResourceT> {
        private final BiFunction<RequestT, String, RequestT> tokenInjector;
        private final BiFunction<RequestT, Integer, RequestT> pageSizeInjector;
        private final Function<RequestT, Integer> pageSizeExtractor;
        private final Function<ResponseT, String> nextTokenExtractor;
        private final Function<ResponseT, Iterable<ResourceT>> resourcesExtractor;

        private SimplePagedListDescriptor(
                BiFunction<RequestT, String, RequestT> tokenInjector,
                BiFunction<RequestT, Integer, RequestT> pageSizeInjector,
                Function<RequestT, Integer> pageSizeExtractor,
                Function<ResponseT, String> nextTokenExtractor,
                Function<ResponseT, Iterable<ResourceT>> resourcesExtractor) {
            this.tokenInjector = tokenInjector;
            this.pageSizeInjector = pageSizeInjector;
            this.pageSizeExtractor = pageSizeExtractor;
            this.nextTokenExtractor = nextTokenExtractor;
            this.resourcesExtractor = resourcesExtractor;
        }

        @Override
        public String emptyToken() {
            return "";
        }

        @Override
        public RequestT injectToken(RequestT request, String token) {
            return tokenInjector.apply(request, token);
        }

        @Override
        public RequestT injectPageSize(RequestT request, int pageSize) {
            return pageSizeInjector.apply(request, pageSize);
        }

        @Override
        public Integer extractPageSize(RequestT request) {
            return pageSizeExtractor.apply(request);
        }

        @Override
        public String extractNextToken(ResponseT response) {
            return nextTokenExtractor.apply(response);
        }

        @Override
        public Iterable<ResourceT> extractResources(ResponseT response) {
            return resourcesExtractor.apply(response);
        }
    }

    private interface ImmediateBackgroundResource extends BackgroundResource {
        @Override
        default void shutdown() {
        }

        @Override
        default boolean isShutdown() {
            return true;
        }

        @Override
        default boolean isTerminated() {
            return true;
        }

        @Override
        default void shutdownNow() {
        }

        @Override
        default boolean awaitTermination(long duration, TimeUnit unit) {
            return true;
        }
    }

    private static final class FakeSecretManagerServiceStub extends SecretManagerServiceStub
            implements ImmediateBackgroundResource {
        private final CapturingPagedCallable<ListSecretsRequest,
                        ListSecretsResponse,
                        Secret,
                        SecretManagerServiceClient.ListSecretsPagedResponse> listSecrets =
                new CapturingPagedCallable<>(
                        ListSecretsResponse.newBuilder().addSecrets(sampleSecret()).build(),
                        secretsPageDescriptor(),
                        SecretManagerServiceClient.ListSecretsPagedResponse::createAsync);
        private final CapturingUnaryCallable<ListSecretsRequest, ListSecretsResponse> listSecretsUnary =
                new CapturingUnaryCallable<>(ListSecretsResponse.newBuilder().addSecrets(sampleSecret()).build());
        private final CapturingUnaryCallable<CreateSecretRequest, Secret> createSecret =
                new CapturingUnaryCallable<>(sampleSecret());
        private final CapturingUnaryCallable<AddSecretVersionRequest, SecretVersion> addSecretVersion =
                new CapturingUnaryCallable<>(sampleVersion(SecretVersion.State.ENABLED));
        private final CapturingUnaryCallable<GetSecretRequest, Secret> getSecret =
                new CapturingUnaryCallable<>(sampleSecret());
        private final CapturingUnaryCallable<UpdateSecretRequest, Secret> updateSecret =
                new CapturingUnaryCallable<>(sampleSecret());
        private final CapturingUnaryCallable<DeleteSecretRequest, Empty> deleteSecret =
                new CapturingUnaryCallable<>(Empty.getDefaultInstance());
        private final CapturingPagedCallable<ListSecretVersionsRequest,
                        ListSecretVersionsResponse,
                        SecretVersion,
                        SecretManagerServiceClient.ListSecretVersionsPagedResponse> listSecretVersions =
                new CapturingPagedCallable<>(
                        ListSecretVersionsResponse.newBuilder()
                                .addVersions(sampleVersion(SecretVersion.State.ENABLED))
                                .build(),
                        secretVersionsPageDescriptor(),
                        SecretManagerServiceClient.ListSecretVersionsPagedResponse::createAsync);
        private final CapturingUnaryCallable<ListSecretVersionsRequest, ListSecretVersionsResponse>
                listSecretVersionsUnary = new CapturingUnaryCallable<>(ListSecretVersionsResponse.newBuilder()
                        .addVersions(sampleVersion(SecretVersion.State.ENABLED))
                        .build());
        private final CapturingUnaryCallable<GetSecretVersionRequest, SecretVersion> getSecretVersion =
                new CapturingUnaryCallable<>(sampleVersion(SecretVersion.State.ENABLED));
        private final CapturingUnaryCallable<AccessSecretVersionRequest, AccessSecretVersionResponse>
                accessSecretVersion = new CapturingUnaryCallable<>(AccessSecretVersionResponse.newBuilder()
                        .setName(SecretVersionName.of(PROJECT, SECRET, VERSION).toString())
                        .setPayload(SecretPayload.newBuilder().setData(ByteString.copyFromUtf8("stored-secret")))
                        .build());
        private final CapturingUnaryCallable<DisableSecretVersionRequest, SecretVersion> disableSecretVersion =
                new CapturingUnaryCallable<>(sampleVersion(SecretVersion.State.DISABLED));
        private final CapturingUnaryCallable<EnableSecretVersionRequest, SecretVersion> enableSecretVersion =
                new CapturingUnaryCallable<>(sampleVersion(SecretVersion.State.ENABLED));
        private final CapturingUnaryCallable<DestroySecretVersionRequest, SecretVersion> destroySecretVersion =
                new CapturingUnaryCallable<>(sampleVersion(SecretVersion.State.DESTROYED));
        private final CapturingUnaryCallable<SetIamPolicyRequest, Policy> setIamPolicy =
                new CapturingUnaryCallable<>(samplePolicy());
        private final CapturingUnaryCallable<GetIamPolicyRequest, Policy> getIamPolicy =
                new CapturingUnaryCallable<>(samplePolicy());
        private final CapturingUnaryCallable<TestIamPermissionsRequest, TestIamPermissionsResponse> testIamPermissions =
                new CapturingUnaryCallable<>(TestIamPermissionsResponse.newBuilder()
                        .addPermissions("secretmanager.versions.access")
                        .build());
        private boolean closed;

        @Override
        public UnaryCallable<ListSecretsRequest, SecretManagerServiceClient.ListSecretsPagedResponse>
                listSecretsPagedCallable() {
            return listSecrets;
        }

        @Override
        public UnaryCallable<ListSecretsRequest, ListSecretsResponse> listSecretsCallable() {
            return listSecretsUnary;
        }

        @Override
        public UnaryCallable<CreateSecretRequest, Secret> createSecretCallable() {
            return createSecret;
        }

        @Override
        public UnaryCallable<AddSecretVersionRequest, SecretVersion> addSecretVersionCallable() {
            return addSecretVersion;
        }

        @Override
        public UnaryCallable<GetSecretRequest, Secret> getSecretCallable() {
            return getSecret;
        }

        @Override
        public UnaryCallable<UpdateSecretRequest, Secret> updateSecretCallable() {
            return updateSecret;
        }

        @Override
        public UnaryCallable<DeleteSecretRequest, Empty> deleteSecretCallable() {
            return deleteSecret;
        }

        @Override
        public UnaryCallable<ListSecretVersionsRequest, SecretManagerServiceClient.ListSecretVersionsPagedResponse>
                listSecretVersionsPagedCallable() {
            return listSecretVersions;
        }

        @Override
        public UnaryCallable<ListSecretVersionsRequest, ListSecretVersionsResponse> listSecretVersionsCallable() {
            return listSecretVersionsUnary;
        }

        @Override
        public UnaryCallable<GetSecretVersionRequest, SecretVersion> getSecretVersionCallable() {
            return getSecretVersion;
        }

        @Override
        public UnaryCallable<AccessSecretVersionRequest, AccessSecretVersionResponse> accessSecretVersionCallable() {
            return accessSecretVersion;
        }

        @Override
        public UnaryCallable<DisableSecretVersionRequest, SecretVersion> disableSecretVersionCallable() {
            return disableSecretVersion;
        }

        @Override
        public UnaryCallable<EnableSecretVersionRequest, SecretVersion> enableSecretVersionCallable() {
            return enableSecretVersion;
        }

        @Override
        public UnaryCallable<DestroySecretVersionRequest, SecretVersion> destroySecretVersionCallable() {
            return destroySecretVersion;
        }

        @Override
        public UnaryCallable<SetIamPolicyRequest, Policy> setIamPolicyCallable() {
            return setIamPolicy;
        }

        @Override
        public UnaryCallable<GetIamPolicyRequest, Policy> getIamPolicyCallable() {
            return getIamPolicy;
        }

        @Override
        public UnaryCallable<TestIamPermissionsRequest, TestIamPermissionsResponse> testIamPermissionsCallable() {
            return testIamPermissions;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
