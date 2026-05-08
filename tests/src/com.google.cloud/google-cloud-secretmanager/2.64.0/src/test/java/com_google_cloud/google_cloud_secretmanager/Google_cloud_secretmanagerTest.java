/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_secretmanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.cloud.secretmanager.v1.AccessSecretVersionRequest;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.AddSecretVersionRequest;
import com.google.cloud.secretmanager.v1.CreateSecretRequest;
import com.google.cloud.secretmanager.v1.CustomerManagedEncryption;
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
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Empty;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.google.iam.v1.Binding;
import com.google.iam.v1.GetIamPolicyRequest;
import com.google.iam.v1.Policy;
import com.google.iam.v1.SetIamPolicyRequest;
import com.google.iam.v1.TestIamPermissionsRequest;
import com.google.iam.v1.TestIamPermissionsResponse;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class Google_cloud_secretmanagerTest {
    private static final String PROJECT_ID = "native-secret-project";
    private static final String SECRET_ID = "database-password";
    private static final String PARENT = ProjectName.of(PROJECT_ID).toString();
    private static final String SECRET_NAME = SecretName.of(PROJECT_ID, SECRET_ID).toString();
    private static final String VERSION_NAME = SecretVersionName.of(PROJECT_ID, SECRET_ID, "1").toString();

    @Test
    @Timeout(value = 10)
    void resourceNameHelpersFormatParseAndConvertLists() {
        SecretName regionalSecret = SecretName.ofProjectLocationSecretName(PROJECT_ID, "us-central1", SECRET_ID);
        SecretName parsedRegionalSecret = SecretName.parse(regionalSecret.toString());
        SecretVersionName versionName = SecretVersionName.of(PROJECT_ID, SECRET_ID, "7");
        SecretVersionName parsedVersionName = SecretVersionName.parse(versionName.toString());
        LocationName locationName = LocationName.of(PROJECT_ID, "europe-west1");

        assertThat(SecretName.isParsableFrom(SECRET_NAME)).isTrue();
        assertThat(regionalSecret.toString())
                .isEqualTo("projects/native-secret-project/locations/us-central1/secrets/database-password");
        assertThat(parsedRegionalSecret.getProject()).isEqualTo(PROJECT_ID);
        assertThat(parsedRegionalSecret.getLocation()).isEqualTo("us-central1");
        assertThat(parsedRegionalSecret.getSecret()).isEqualTo(SECRET_ID);
        assertThat(parsedRegionalSecret.getFieldValue("secret")).isEqualTo(SECRET_ID);
        assertThat(parsedVersionName.getSecretVersion()).isEqualTo("7");
        assertThat(locationName.toString()).isEqualTo("projects/native-secret-project/locations/europe-west1");
        assertThat(SecretName.toStringList(List.of(SecretName.of(PROJECT_ID, "alpha"), regionalSecret)))
                .containsExactly(
                        "projects/native-secret-project/secrets/alpha",
                        "projects/native-secret-project/locations/us-central1/secrets/database-password");
        assertThat(SecretName.parseList(List.of(SECRET_NAME))).containsExactly(SecretName.of(PROJECT_ID, SECRET_ID));
    }

    @Test
    @Timeout(value = 10)
    void modelBuildersPreserveReplicationMetadataAndPayloadFields() {
        CustomerManagedEncryption encryption = CustomerManagedEncryption.newBuilder()
                .setKmsKeyName("projects/native-secret-project/locations/global/keyRings/test/cryptoKeys/key")
                .build();
        Secret secret = Secret.newBuilder()
                .setName(SECRET_NAME)
                .setReplication(Replication.newBuilder()
                        .setUserManaged(Replication.UserManaged.newBuilder()
                                .addReplicas(Replication.UserManaged.Replica.newBuilder()
                                        .setLocation("us-central1")
                                        .setCustomerManagedEncryption(encryption))))
                .putLabels("environment", "test")
                .putAnnotations("owner", "native-image")
                .putVersionAliases("current", 1L)
                .setVersionDestroyTtl(Duration.newBuilder().setSeconds(86_400))
                .setEtag("secret-etag")
                .build();
        SecretPayload payload = SecretPayload.newBuilder()
                .setData(ByteString.copyFromUtf8("hunter2"))
                .setDataCrc32C(1_234_567L)
                .build();

        assertThat(secret.getReplication().getUserManaged().getReplicas(0).getLocation()).isEqualTo("us-central1");
        assertThat(secret.getReplication()
                .getUserManaged()
                .getReplicas(0)
                .getCustomerManagedEncryption()
                .getKmsKeyName())
                .endsWith("cryptoKeys/key");
        assertThat(secret.getLabelsMap()).containsEntry("environment", "test");
        assertThat(secret.getAnnotationsMap()).containsEntry("owner", "native-image");
        assertThat(secret.getVersionAliasesMap()).containsEntry("current", 1L);
        assertThat(secret.getVersionDestroyTtl().getSeconds()).isEqualTo(86_400);
        assertThat(secret.toBuilder().putLabels("tier", "backend").build().getLabelsMap())
                .containsEntry("tier", "backend");
        assertThat(payload.getData().toStringUtf8()).isEqualTo("hunter2");
        assertThat(payload.getDataCrc32C()).isEqualTo(1_234_567L);
    }

    @Test
    @Timeout(value = 10)
    void secretSchedulingPoliciesPreserveExpirationRotationAndNotificationTopics() {
        Timestamp expireTime = Timestamp.newBuilder().setSeconds(1_700_000_000L).build();
        Timestamp nextRotationTime = Timestamp.newBuilder().setSeconds(1_700_086_400L).build();
        Rotation rotation = Rotation.newBuilder()
                .setNextRotationTime(nextRotationTime)
                .setRotationPeriod(Duration.newBuilder().setSeconds(86_400L))
                .build();
        Topic notificationTopic = Topic.newBuilder()
                .setName("projects/native-secret-project/topics/secret-rotation")
                .build();
        Secret expiringSecret = Secret.newBuilder()
                .setName(SECRET_NAME)
                .addTopics(notificationTopic)
                .setExpireTime(expireTime)
                .setRotation(rotation)
                .build();
        Secret ttlSecret = expiringSecret.toBuilder()
                .clearExpireTime()
                .setTtl(Duration.newBuilder().setSeconds(3_600L))
                .build();

        assertThat(expiringSecret.getTopicsList()).extracting(Topic::getName)
                .containsExactly("projects/native-secret-project/topics/secret-rotation");
        assertThat(expiringSecret.getExpirationCase()).isEqualTo(Secret.ExpirationCase.EXPIRE_TIME);
        assertThat(expiringSecret.getExpireTime()).isEqualTo(expireTime);
        assertThat(expiringSecret.getRotation().getNextRotationTime()).isEqualTo(nextRotationTime);
        assertThat(expiringSecret.getRotation().getRotationPeriod().getSeconds()).isEqualTo(86_400L);
        assertThat(ttlSecret.getExpirationCase()).isEqualTo(Secret.ExpirationCase.TTL);
        assertThat(ttlSecret.getTtl().getSeconds()).isEqualTo(3_600L);
    }

    @Test
    @Timeout(value = 10)
    void settingsBuildersExposeGrpcAndHttpJsonTransports() throws IOException {
        SecretManagerServiceSettings grpcSettings = SecretManagerServiceSettings.newBuilder()
                .setEndpoint("localhost:0")
                .setCredentialsProvider(NoCredentialsProvider.create())
                .build();
        SecretManagerServiceSettings httpJsonSettings = SecretManagerServiceSettings.newHttpJsonBuilder()
                .setEndpoint("http://localhost:0")
                .setCredentialsProvider(NoCredentialsProvider.create())
                .build();

        assertThat(SecretManagerServiceSettings.getDefaultEndpoint()).isEqualTo("secretmanager.googleapis.com:443");
        assertThat(SecretManagerServiceSettings.getDefaultServiceScopes())
                .contains("https://www.googleapis.com/auth/cloud-platform");
        assertThat(grpcSettings.getEndpoint()).isEqualTo("localhost:0");
        assertThat(httpJsonSettings.getEndpoint()).isEqualTo("http://localhost:0");
        assertThat(grpcSettings.createSecretSettings().getRetrySettings()).isNotNull();
        assertThat(httpJsonSettings.accessSecretVersionSettings().getRetrySettings()).isNotNull();
    }

    @Test
    @Timeout(value = 10)
    void callableApiHandlesPagedListRequestsAndUnaryResponses() throws Exception {
        FakeSecretManagerService service = new FakeSecretManagerService();
        String serverName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        SecretManagerServiceSettings settings = SecretManagerServiceSettings.newBuilder()
                .setCredentialsProvider(NoCredentialsProvider.create())
                .setTransportChannelProvider(
                        FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
                .build();

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create(settings)) {
            Secret secretTemplate = Secret.newBuilder()
                    .setReplication(Replication.newBuilder().setAutomatic(Replication.Automatic.newBuilder()))
                    .build();
            for (String secretId : List.of("alpha", "beta", "gamma")) {
                CreateSecretRequest request = CreateSecretRequest.newBuilder()
                        .setParent(PARENT)
                        .setSecretId(secretId)
                        .setSecret(secretTemplate)
                        .build();
                client.createSecretCallable().futureCall(request).get(5, TimeUnit.SECONDS);
            }

            SecretManagerServiceClient.ListSecretsPage firstPage = client.listSecretsPagedCallable()
                    .call(ListSecretsRequest.newBuilder().setParent(PARENT).setPageSize(2).build())
                    .getPage();
            List<String> firstPageNames = new ArrayList<>();
            firstPage.getValues().forEach(secret -> firstPageNames.add(secret.getName()));
            SecretManagerServiceClient.ListSecretsPage secondPage = firstPage.getNextPage();
            List<String> secondPageNames = new ArrayList<>();
            secondPage.getValues().forEach(secret -> secondPageNames.add(secret.getName()));

            assertThat(firstPageNames).containsExactly(PARENT + "/secrets/alpha", PARENT + "/secrets/beta");
            assertThat(firstPage.hasNextPage()).isTrue();
            assertThat(secondPageNames).containsExactly(PARENT + "/secrets/gamma");
            assertThat(secondPage.hasNextPage()).isFalse();
            assertThat(service.listSecretsPageTokens).containsExactly("", "2");

            SecretPayload payload = SecretPayload.newBuilder()
                    .setData(ByteString.copyFromUtf8("callable-secret"))
                    .build();
            SecretVersion version = client.addSecretVersionCallable()
                    .futureCall(AddSecretVersionRequest.newBuilder()
                            .setParent(SecretName.of(PROJECT_ID, "alpha").toString())
                            .setPayload(payload)
                            .build())
                    .get(5, TimeUnit.SECONDS);
            AccessSecretVersionResponse accessed = client.accessSecretVersionCallable()
                    .futureCall(AccessSecretVersionRequest.newBuilder().setName(version.getName()).build())
                    .get(5, TimeUnit.SECONDS);

            assertThat(version.getName()).isEqualTo(PARENT + "/secrets/alpha/versions/1");
            assertThat(accessed.getPayload().getData().toStringUtf8()).isEqualTo("callable-secret");
        } finally {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(value = 10)
    void clientCallsSecretLifecycleAgainstInProcessGrpcService() throws Exception {
        FakeSecretManagerService service = new FakeSecretManagerService();
        String serverName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        SecretManagerServiceSettings settings = SecretManagerServiceSettings.newBuilder()
                .setCredentialsProvider(NoCredentialsProvider.create())
                .setTransportChannelProvider(
                        FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
                .build();

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create(settings)) {
            Secret secretTemplate = Secret.newBuilder()
                    .setReplication(Replication.newBuilder().setAutomatic(Replication.Automatic.newBuilder()))
                    .putLabels("environment", "test")
                    .build();
            Secret created = client.createSecret(PARENT, SECRET_ID, secretTemplate);

            assertThat(created.getName()).isEqualTo(SECRET_NAME);
            assertThat(service.createSecretRequests).hasSize(1);
            assertThat(service.createSecretRequests.get(0).getSecretId()).isEqualTo(SECRET_ID);

            SecretPayload payload = SecretPayload.newBuilder().setData(ByteString.copyFromUtf8("super-secret")).build();
            SecretVersion version = client.addSecretVersion(SecretName.of(PROJECT_ID, SECRET_ID), payload);
            AccessSecretVersionResponse accessed = client.accessSecretVersion(VERSION_NAME);

            assertThat(version.getName()).isEqualTo(VERSION_NAME);
            assertThat(version.getState()).isEqualTo(SecretVersion.State.ENABLED);
            assertThat(accessed.getPayload().getData().toStringUtf8()).isEqualTo("super-secret");

            Secret fetchedSecret = client.getSecret(SECRET_NAME);
            SecretVersion fetchedVersion = client.getSecretVersion(SecretVersionName.of(PROJECT_ID, SECRET_ID, "1"));

            assertThat(fetchedSecret.getLabelsMap()).containsEntry("environment", "test");
            assertThat(fetchedVersion.getName()).isEqualTo(VERSION_NAME);

            Secret updated = client.updateSecret(
                    fetchedSecret.toBuilder().putLabels("rotated", "true").build(),
                    FieldMask.newBuilder().addPaths("labels").build());
            assertThat(updated.getLabelsMap()).containsEntry("rotated", "true");
            assertThat(service.updateMasks).containsExactly("labels");

            List<Secret> listedSecrets = new ArrayList<>();
            client.listSecrets(ProjectName.of(PROJECT_ID)).iterateAll().forEach(listedSecrets::add);
            List<SecretVersion> listedVersions = new ArrayList<>();
            client.listSecretVersions(SecretName.of(PROJECT_ID, SECRET_ID)).iterateAll().forEach(listedVersions::add);

            assertThat(listedSecrets).extracting(Secret::getName).containsExactly(SECRET_NAME);
            assertThat(listedVersions).extracting(SecretVersion::getName).containsExactly(VERSION_NAME);

            assertThat(client.disableSecretVersion(VERSION_NAME).getState()).isEqualTo(SecretVersion.State.DISABLED);
            assertThat(client.enableSecretVersion(VERSION_NAME).getState()).isEqualTo(SecretVersion.State.ENABLED);
            assertThat(client.destroySecretVersion(VERSION_NAME).getState()).isEqualTo(SecretVersion.State.DESTROYED);

            Policy policy = Policy.newBuilder()
                    .addBindings(Binding.newBuilder()
                            .setRole("roles/secretmanager.secretAccessor")
                            .addMembers("user:reader@example.com"))
                    .build();
            Policy storedPolicy = client.setIamPolicy(SetIamPolicyRequest.newBuilder()
                    .setResource(SECRET_NAME)
                    .setPolicy(policy)
                    .build());
            Policy fetchedPolicy = client.getIamPolicy(GetIamPolicyRequest.newBuilder()
                    .setResource(SECRET_NAME)
                    .build());
            TestIamPermissionsResponse permissions = client.testIamPermissions(TestIamPermissionsRequest.newBuilder()
                    .setResource(SECRET_NAME)
                    .addPermissions("secretmanager.versions.access")
                    .build());

            assertThat(storedPolicy.getBindings(0).getRole()).isEqualTo("roles/secretmanager.secretAccessor");
            assertThat(fetchedPolicy).isEqualTo(storedPolicy);
            assertThat(permissions.getPermissionsList()).containsExactly("secretmanager.versions.access");

            client.deleteSecret(SecretName.of(PROJECT_ID, SECRET_ID));
            assertThat(service.deletedSecrets).containsExactly(SECRET_NAME);
        } finally {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static final class FakeSecretManagerService implements BindableService {
        private static final String SERVICE_NAME = "google.cloud.secretmanager.v1.SecretManagerService";

        private final Map<String, Secret> secrets = new LinkedHashMap<>();
        private final Map<String, SecretVersion> versions = new LinkedHashMap<>();
        private final Map<String, SecretPayload> payloads = new LinkedHashMap<>();
        private final List<CreateSecretRequest> createSecretRequests = new ArrayList<>();
        private final List<String> listSecretsPageTokens = new ArrayList<>();
        private final List<String> deletedSecrets = new ArrayList<>();
        private final List<String> updateMasks = new ArrayList<>();
        private Policy policy = Policy.getDefaultInstance();

        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(unaryMethod("ListSecrets", ListSecretsRequest.getDefaultInstance(),
                            ListSecretsResponse.getDefaultInstance()), ServerCalls.asyncUnaryCall(this::listSecrets))
                    .addMethod(unaryMethod("CreateSecret", CreateSecretRequest.getDefaultInstance(),
                            Secret.getDefaultInstance()), ServerCalls.asyncUnaryCall(this::createSecret))
                    .addMethod(unaryMethod("AddSecretVersion", AddSecretVersionRequest.getDefaultInstance(),
                            SecretVersion.getDefaultInstance()), ServerCalls.asyncUnaryCall(this::addSecretVersion))
                    .addMethod(unaryMethod("GetSecret", GetSecretRequest.getDefaultInstance(),
                            Secret.getDefaultInstance()), ServerCalls.asyncUnaryCall(this::getSecret))
                    .addMethod(unaryMethod("UpdateSecret", UpdateSecretRequest.getDefaultInstance(),
                            Secret.getDefaultInstance()), ServerCalls.asyncUnaryCall(this::updateSecret))
                    .addMethod(unaryMethod("DeleteSecret", DeleteSecretRequest.getDefaultInstance(),
                            Empty.getDefaultInstance()), ServerCalls.asyncUnaryCall(this::deleteSecret))
                    .addMethod(unaryMethod("ListSecretVersions", ListSecretVersionsRequest.getDefaultInstance(),
                            ListSecretVersionsResponse.getDefaultInstance()),
                            ServerCalls.asyncUnaryCall(this::listSecretVersions))
                    .addMethod(unaryMethod("GetSecretVersion", GetSecretVersionRequest.getDefaultInstance(),
                            SecretVersion.getDefaultInstance()), ServerCalls.asyncUnaryCall(this::getSecretVersion))
                    .addMethod(unaryMethod("AccessSecretVersion", AccessSecretVersionRequest.getDefaultInstance(),
                            AccessSecretVersionResponse.getDefaultInstance()),
                            ServerCalls.asyncUnaryCall(this::accessSecretVersion))
                    .addMethod(unaryMethod("DisableSecretVersion", DisableSecretVersionRequest.getDefaultInstance(),
                            SecretVersion.getDefaultInstance()), ServerCalls.asyncUnaryCall(this::disableSecretVersion))
                    .addMethod(unaryMethod("EnableSecretVersion", EnableSecretVersionRequest.getDefaultInstance(),
                            SecretVersion.getDefaultInstance()), ServerCalls.asyncUnaryCall(this::enableSecretVersion))
                    .addMethod(unaryMethod("DestroySecretVersion", DestroySecretVersionRequest.getDefaultInstance(),
                            SecretVersion.getDefaultInstance()), ServerCalls.asyncUnaryCall(this::destroySecretVersion))
                    .addMethod(unaryMethod("SetIamPolicy", SetIamPolicyRequest.getDefaultInstance(),
                            Policy.getDefaultInstance()), ServerCalls.asyncUnaryCall(this::setIamPolicy))
                    .addMethod(unaryMethod("GetIamPolicy", GetIamPolicyRequest.getDefaultInstance(),
                            Policy.getDefaultInstance()), ServerCalls.asyncUnaryCall(this::getIamPolicy))
                    .addMethod(unaryMethod("TestIamPermissions", TestIamPermissionsRequest.getDefaultInstance(),
                            TestIamPermissionsResponse.getDefaultInstance()),
                            ServerCalls.asyncUnaryCall(this::testIamPermissions))
                    .build();
        }

        private void listSecrets(ListSecretsRequest request, StreamObserver<ListSecretsResponse> observer) {
            List<Secret> allSecrets = new ArrayList<>(secrets.values());
            int startIndex = pageStartIndex(request.getPageToken());
            int endIndex = pageEndIndex(startIndex, request.getPageSize(), allSecrets.size());
            listSecretsPageTokens.add(request.getPageToken());
            observer.onNext(ListSecretsResponse.newBuilder()
                    .addAllSecrets(allSecrets.subList(startIndex, endIndex))
                    .setNextPageToken(nextPageToken(endIndex, allSecrets.size()))
                    .build());
            observer.onCompleted();
        }

        private void createSecret(CreateSecretRequest request, StreamObserver<Secret> observer) {
            createSecretRequests.add(request);
            String name = request.getParent() + "/secrets/" + request.getSecretId();
            Secret secret = request.getSecret().toBuilder().setName(name).setEtag("created-etag").build();
            secrets.put(name, secret);
            observer.onNext(secret);
            observer.onCompleted();
        }

        private void addSecretVersion(AddSecretVersionRequest request, StreamObserver<SecretVersion> observer) {
            String versionName = request.getParent() + "/versions/" + (versions.size() + 1);
            SecretVersion version = SecretVersion.newBuilder()
                    .setName(versionName)
                    .setState(SecretVersion.State.ENABLED)
                    .setEtag("version-etag")
                    .build();
            versions.put(versionName, version);
            payloads.put(versionName, request.getPayload());
            observer.onNext(version);
            observer.onCompleted();
        }

        private void getSecret(GetSecretRequest request, StreamObserver<Secret> observer) {
            observer.onNext(secrets.get(request.getName()));
            observer.onCompleted();
        }

        private void updateSecret(UpdateSecretRequest request, StreamObserver<Secret> observer) {
            updateMasks.addAll(request.getUpdateMask().getPathsList());
            secrets.put(request.getSecret().getName(), request.getSecret());
            observer.onNext(request.getSecret());
            observer.onCompleted();
        }

        private void deleteSecret(DeleteSecretRequest request, StreamObserver<Empty> observer) {
            deletedSecrets.add(request.getName());
            secrets.remove(request.getName());
            observer.onNext(Empty.getDefaultInstance());
            observer.onCompleted();
        }

        private void listSecretVersions(
                ListSecretVersionsRequest request, StreamObserver<ListSecretVersionsResponse> observer) {
            observer.onNext(ListSecretVersionsResponse.newBuilder().addAllVersions(versions.values()).build());
            observer.onCompleted();
        }

        private void getSecretVersion(GetSecretVersionRequest request, StreamObserver<SecretVersion> observer) {
            observer.onNext(versions.get(request.getName()));
            observer.onCompleted();
        }

        private void accessSecretVersion(
                AccessSecretVersionRequest request, StreamObserver<AccessSecretVersionResponse> observer) {
            observer.onNext(AccessSecretVersionResponse.newBuilder()
                    .setName(request.getName())
                    .setPayload(payloads.get(request.getName()))
                    .build());
            observer.onCompleted();
        }

        private void disableSecretVersion(
                DisableSecretVersionRequest request, StreamObserver<SecretVersion> observer) {
            observer.onNext(updateVersionState(request.getName(), SecretVersion.State.DISABLED));
            observer.onCompleted();
        }

        private void enableSecretVersion(EnableSecretVersionRequest request, StreamObserver<SecretVersion> observer) {
            observer.onNext(updateVersionState(request.getName(), SecretVersion.State.ENABLED));
            observer.onCompleted();
        }

        private void destroySecretVersion(
                DestroySecretVersionRequest request, StreamObserver<SecretVersion> observer) {
            observer.onNext(updateVersionState(request.getName(), SecretVersion.State.DESTROYED));
            observer.onCompleted();
        }

        private void setIamPolicy(SetIamPolicyRequest request, StreamObserver<Policy> observer) {
            policy = request.getPolicy();
            observer.onNext(policy);
            observer.onCompleted();
        }

        private void getIamPolicy(GetIamPolicyRequest request, StreamObserver<Policy> observer) {
            observer.onNext(policy);
            observer.onCompleted();
        }

        private void testIamPermissions(
                TestIamPermissionsRequest request, StreamObserver<TestIamPermissionsResponse> observer) {
            observer.onNext(TestIamPermissionsResponse.newBuilder()
                    .addAllPermissions(request.getPermissionsList())
                    .build());
            observer.onCompleted();
        }

        private SecretVersion updateVersionState(String name, SecretVersion.State state) {
            SecretVersion version = versions.get(name).toBuilder().setState(state).build();
            versions.put(name, version);
            return version;
        }

        private static int pageStartIndex(String pageToken) {
            if (pageToken.isEmpty()) {
                return 0;
            }
            return Integer.parseInt(pageToken);
        }

        private static int pageEndIndex(int startIndex, int pageSize, int valueCount) {
            if (pageSize <= 0) {
                return valueCount;
            }
            return Math.min(startIndex + pageSize, valueCount);
        }

        private static String nextPageToken(int endIndex, int valueCount) {
            if (endIndex >= valueCount) {
                return "";
            }
            return String.valueOf(endIndex);
        }

        private static <ReqT extends Message, RespT extends Message> MethodDescriptor<ReqT, RespT> unaryMethod(
                String methodName, ReqT requestDefaultInstance, RespT responseDefaultInstance) {
            return MethodDescriptor.<ReqT, RespT>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, methodName))
                    .setRequestMarshaller(ProtoUtils.marshaller(requestDefaultInstance))
                    .setResponseMarshaller(ProtoUtils.marshaller(responseDefaultInstance))
                    .build();
        }
    }
}
