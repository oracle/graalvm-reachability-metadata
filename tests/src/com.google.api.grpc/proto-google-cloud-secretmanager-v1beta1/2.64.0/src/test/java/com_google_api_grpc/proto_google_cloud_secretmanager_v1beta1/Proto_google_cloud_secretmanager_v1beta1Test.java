/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_cloud_secretmanager_v1beta1;

import java.util.List;

import com.google.api.AnnotationsProto;
import com.google.api.ClientProto;
import com.google.api.FieldBehavior;
import com.google.api.FieldBehaviorProto;
import com.google.api.HttpRule;
import com.google.api.ResourceDescriptor;
import com.google.api.ResourceProto;
import com.google.cloud.secretmanager.v1beta1.AccessSecretVersionRequest;
import com.google.cloud.secretmanager.v1beta1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1beta1.AddSecretVersionRequest;
import com.google.cloud.secretmanager.v1beta1.CreateSecretRequest;
import com.google.cloud.secretmanager.v1beta1.DeleteSecretRequest;
import com.google.cloud.secretmanager.v1beta1.DestroySecretVersionRequest;
import com.google.cloud.secretmanager.v1beta1.DisableSecretVersionRequest;
import com.google.cloud.secretmanager.v1beta1.EnableSecretVersionRequest;
import com.google.cloud.secretmanager.v1beta1.GetSecretRequest;
import com.google.cloud.secretmanager.v1beta1.GetSecretVersionRequest;
import com.google.cloud.secretmanager.v1beta1.ListSecretVersionsRequest;
import com.google.cloud.secretmanager.v1beta1.ListSecretVersionsResponse;
import com.google.cloud.secretmanager.v1beta1.ListSecretsRequest;
import com.google.cloud.secretmanager.v1beta1.ListSecretsResponse;
import com.google.cloud.secretmanager.v1beta1.ProjectName;
import com.google.cloud.secretmanager.v1beta1.Replication;
import com.google.cloud.secretmanager.v1beta1.ResourcesProto;
import com.google.cloud.secretmanager.v1beta1.Secret;
import com.google.cloud.secretmanager.v1beta1.SecretName;
import com.google.cloud.secretmanager.v1beta1.SecretPayload;
import com.google.cloud.secretmanager.v1beta1.SecretVersion;
import com.google.cloud.secretmanager.v1beta1.SecretVersionName;
import com.google.cloud.secretmanager.v1beta1.ServiceProto;
import com.google.cloud.secretmanager.v1beta1.UpdateSecretRequest;
import com.google.iam.v1.Binding;
import com.google.iam.v1.GetIamPolicyRequest;
import com.google.iam.v1.GetPolicyOptions;
import com.google.iam.v1.Policy;
import com.google.iam.v1.SetIamPolicyRequest;
import com.google.iam.v1.TestIamPermissionsRequest;
import com.google.iam.v1.TestIamPermissionsResponse;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Timestamp;
import com.google.type.Expr;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Proto_google_cloud_secretmanager_v1beta1Test {
    private static final String PROJECT = "sample-project";
    private static final String SECRET = "database-password";
    private static final String VERSION = "3";
    private static final String PROJECT_RESOURCE = "projects/" + PROJECT;
    private static final String SECRET_RESOURCE = PROJECT_RESOURCE + "/secrets/" + SECRET;
    private static final String VERSION_RESOURCE = SECRET_RESOURCE + "/versions/" + VERSION;

    @Test
    void resourceNamesFormatParseAndExposeFieldValues() {
        ProjectName projectName = ProjectName.of(PROJECT);
        SecretName secretName = SecretName.of(PROJECT, SECRET);
        SecretVersionName versionName = SecretVersionName.newBuilder()
                .setProject(PROJECT)
                .setSecret(SECRET)
                .setSecretVersion(VERSION)
                .build();

        assertThat(projectName.toString()).isEqualTo(PROJECT_RESOURCE);
        assertThat(ProjectName.parse(PROJECT_RESOURCE)).isEqualTo(projectName);
        assertThat(projectName.getFieldValuesMap()).containsEntry("project", PROJECT);
        assertThat(projectName.getFieldValue("project")).isEqualTo(PROJECT);
        assertThat(ProjectName.isParsableFrom(PROJECT_RESOURCE)).isTrue();
        assertThat(ProjectName.isParsableFrom("folders/123456")).isFalse();

        assertThat(secretName.toString()).isEqualTo(SECRET_RESOURCE);
        assertThat(SecretName.format(PROJECT, SECRET)).isEqualTo(SECRET_RESOURCE);
        assertThat(SecretName.parse(SECRET_RESOURCE).getSecret()).isEqualTo(SECRET);
        assertThat(secretName.toBuilder().setSecret("api-key").build().toString())
                .isEqualTo(PROJECT_RESOURCE + "/secrets/api-key");
        assertThat(secretName.getFieldValuesMap())
                .containsEntry("project", PROJECT)
                .containsEntry("secret", SECRET);

        assertThat(versionName.toString()).isEqualTo(VERSION_RESOURCE);
        assertThat(SecretVersionName.parse(SecretVersionName.format(PROJECT, SECRET, VERSION)))
                .isEqualTo(versionName);
        assertThat(versionName.getFieldValue("secret_version")).isEqualTo(VERSION);
        assertThat(SecretVersionName.parse(SECRET_RESOURCE + "/versions/latest").getSecretVersion())
                .isEqualTo("latest");
        assertThat(SecretVersionName.toStringList(List.of(versionName))).containsExactly(VERSION_RESOURCE);
        assertThat(SecretVersionName.parseList(List.of(VERSION_RESOURCE))).containsExactly(versionName);
    }

    @Test
    void automaticSecretPreservesReplicationLabelsTimestampsAndBuilderChanges() {
        Timestamp createTime = timestamp(1_700_000_000L);
        Replication automaticReplication = Replication.newBuilder()
                .setAutomatic(Replication.Automatic.newBuilder())
                .build();
        Secret secret = Secret.newBuilder()
                .setName(SECRET_RESOURCE)
                .setReplication(automaticReplication)
                .setCreateTime(createTime)
                .putLabels("env", "test")
                .putLabels("team", "security")
                .build();

        Secret updatedSecret = secret.toBuilder()
                .putLabels("rotation", "quarterly")
                .removeLabels("env")
                .build();

        assertThat(secret.getName()).isEqualTo(SECRET_RESOURCE);
        assertThat(secret.hasReplication()).isTrue();
        assertThat(secret.getReplication().getReplicationCase()).isEqualTo(Replication.ReplicationCase.AUTOMATIC);
        assertThat(secret.getReplication().hasAutomatic()).isTrue();
        assertThat(secret.getReplication().hasUserManaged()).isFalse();
        assertThat(secret.hasCreateTime()).isTrue();
        assertThat(secret.getCreateTime().getSeconds()).isEqualTo(1_700_000_000L);
        assertThat(secret.getLabelsMap()).containsEntry("env", "test").containsEntry("team", "security");
        assertThat(updatedSecret.getLabelsMap())
                .containsEntry("team", "security")
                .containsEntry("rotation", "quarterly")
                .doesNotContainKey("env");
    }

    @Test
    void userManagedReplicationModelsReplicasAndOneofSwitching() {
        Replication.UserManaged.Replica usCentral = Replication.UserManaged.Replica.newBuilder()
                .setLocation("us-central1")
                .build();
        Replication.UserManaged.Replica europeWest = Replication.UserManaged.Replica.newBuilder()
                .setLocation("europe-west1")
                .build();
        Replication.UserManaged userManaged = Replication.UserManaged.newBuilder()
                .addReplicas(usCentral)
                .addReplicas(europeWest)
                .build();
        Replication replication = Replication.newBuilder()
                .setUserManaged(userManaged)
                .build();

        Replication automatic = replication.toBuilder()
                .setAutomatic(Replication.Automatic.getDefaultInstance())
                .build();

        assertThat(replication.getReplicationCase()).isEqualTo(Replication.ReplicationCase.USER_MANAGED);
        assertThat(replication.getUserManaged().getReplicasList()).containsExactly(usCentral, europeWest);
        assertThat(replication.getUserManaged().getReplicas(0).getLocation()).isEqualTo("us-central1");
        assertThat(replication.getUserManaged().toBuilder()
                .addReplicas(Replication.UserManaged.Replica.newBuilder().setLocation("asia-northeast1"))
                .build()
                .getReplicasList())
                .extracting(Replication.UserManaged.Replica::getLocation)
                .containsExactly("us-central1", "europe-west1", "asia-northeast1");
        assertThat(automatic.getReplicationCase()).isEqualTo(Replication.ReplicationCase.AUTOMATIC);
        assertThat(automatic.hasUserManaged()).isFalse();
    }

    @Test
    void secretVersionsPayloadsAndAccessResponsesRepresentStateAndData() {
        SecretPayload payload = SecretPayload.newBuilder()
                .setData(ByteString.copyFromUtf8("super-secret-value"))
                .build();
        SecretVersion enabledVersion = SecretVersion.newBuilder()
                .setName(VERSION_RESOURCE)
                .setCreateTime(timestamp(1_700_000_100L))
                .setState(SecretVersion.State.ENABLED)
                .build();
        SecretVersion destroyedVersion = enabledVersion.toBuilder()
                .setState(SecretVersion.State.DESTROYED)
                .setDestroyTime(timestamp(1_700_010_000L))
                .build();
        AccessSecretVersionResponse response = AccessSecretVersionResponse.newBuilder()
                .setName(VERSION_RESOURCE)
                .setPayload(payload)
                .build();

        assertThat(payload.getData().toStringUtf8()).isEqualTo("super-secret-value");
        assertThat(enabledVersion.getState()).isEqualTo(SecretVersion.State.ENABLED);
        assertThat(enabledVersion.getStateValue()).isEqualTo(SecretVersion.State.ENABLED.getNumber());
        assertThat(enabledVersion.hasDestroyTime()).isFalse();
        assertThat(destroyedVersion.getState()).isEqualTo(SecretVersion.State.DESTROYED);
        assertThat(destroyedVersion.getDestroyTime().getSeconds()).isEqualTo(1_700_010_000L);
        assertThat(SecretVersion.State.forNumber(SecretVersion.State.DISABLED_VALUE))
                .isEqualTo(SecretVersion.State.DISABLED);
        assertThat(response.hasPayload()).isTrue();
        assertThat(response.getPayload().getData()).isEqualTo(payload.getData());
        assertThat(response.toBuilder().clearPayload().build().hasPayload()).isFalse();
    }

    @Test
    void secretVersionStatePreservesUnknownNumericValuesForForwardCompatibility() {
        SecretVersion unknownStateVersion = SecretVersion.newBuilder()
                .setName(VERSION_RESOURCE)
                .setStateValue(99)
                .build();
        SecretVersion disabledVersion = unknownStateVersion.toBuilder()
                .setState(SecretVersion.State.DISABLED)
                .build();

        assertThat(unknownStateVersion.getName()).isEqualTo(VERSION_RESOURCE);
        assertThat(unknownStateVersion.getStateValue()).isEqualTo(99);
        assertThat(unknownStateVersion.getState()).isEqualTo(SecretVersion.State.UNRECOGNIZED);
        assertThat(SecretVersion.State.forNumber(99)).isNull();
        assertThat(disabledVersion.getState()).isEqualTo(SecretVersion.State.DISABLED);
        assertThat(disabledVersion.getStateValue()).isEqualTo(SecretVersion.State.DISABLED_VALUE);
    }

    @Test
    void secretCrudAndPagingRequestsCarryNamesMasksAndResponses() {
        Secret automaticSecret = Secret.newBuilder()
                .setName(SECRET_RESOURCE)
                .setReplication(Replication.newBuilder().setAutomatic(Replication.Automatic.newBuilder()))
                .putLabels("owner", "platform")
                .build();
        FieldMask labelsMask = FieldMask.newBuilder().addPaths("labels").build();

        ListSecretsRequest listRequest = ListSecretsRequest.newBuilder()
                .setParent(ProjectName.format(PROJECT))
                .setPageSize(25)
                .setPageToken("secret-page-1")
                .build();
        ListSecretsResponse listResponse = ListSecretsResponse.newBuilder()
                .addSecrets(automaticSecret)
                .setNextPageToken("secret-page-2")
                .setTotalSize(1)
                .build();
        CreateSecretRequest createRequest = CreateSecretRequest.newBuilder()
                .setParent(ProjectName.format(PROJECT))
                .setSecretId(SECRET)
                .setSecret(automaticSecret)
                .build();
        GetSecretRequest getRequest = GetSecretRequest.newBuilder()
                .setName(SecretName.format(PROJECT, SECRET))
                .build();
        UpdateSecretRequest updateRequest = UpdateSecretRequest.newBuilder()
                .setSecret(automaticSecret.toBuilder().putLabels("owner", "security"))
                .setUpdateMask(labelsMask)
                .build();
        DeleteSecretRequest deleteRequest = DeleteSecretRequest.newBuilder()
                .setName(SECRET_RESOURCE)
                .build();

        assertThat(listRequest.getParent()).isEqualTo(PROJECT_RESOURCE);
        assertThat(listRequest.getPageSize()).isEqualTo(25);
        assertThat(listRequest.getPageToken()).isEqualTo("secret-page-1");
        assertThat(listResponse.getSecretsList()).containsExactly(automaticSecret);
        assertThat(listResponse.getNextPageToken()).isEqualTo("secret-page-2");
        assertThat(listResponse.getTotalSize()).isEqualTo(1);
        assertThat(createRequest.hasSecret()).isTrue();
        assertThat(createRequest.getSecretId()).isEqualTo(SECRET);
        assertThat(getRequest.getName()).isEqualTo(SECRET_RESOURCE);
        assertThat(updateRequest.getSecret().getLabelsOrThrow("owner")).isEqualTo("security");
        assertThat(updateRequest.getUpdateMask().getPathsList()).containsExactly("labels");
        assertThat(deleteRequest.getName()).isEqualTo(SECRET_RESOURCE);
    }

    @Test
    void secretVersionLifecycleRequestsAndResponsesUseVersionResourceNames() {
        SecretPayload payload = SecretPayload.newBuilder()
                .setData(ByteString.copyFromUtf8("new-version-data"))
                .build();
        SecretVersion enabledVersion = SecretVersion.newBuilder()
                .setName(VERSION_RESOURCE)
                .setCreateTime(timestamp(1_700_000_200L))
                .setState(SecretVersion.State.ENABLED)
                .build();
        SecretVersion disabledVersion = enabledVersion.toBuilder()
                .setState(SecretVersion.State.DISABLED)
                .build();

        AddSecretVersionRequest addRequest = AddSecretVersionRequest.newBuilder()
                .setParent(SECRET_RESOURCE)
                .setPayload(payload)
                .build();
        ListSecretVersionsRequest listRequest = ListSecretVersionsRequest.newBuilder()
                .setParent(SECRET_RESOURCE)
                .setPageSize(10)
                .setPageToken("version-page-1")
                .build();
        ListSecretVersionsResponse listResponse = ListSecretVersionsResponse.newBuilder()
                .addVersions(enabledVersion)
                .addVersions(disabledVersion)
                .setNextPageToken("version-page-2")
                .setTotalSize(2)
                .build();
        GetSecretVersionRequest getRequest = GetSecretVersionRequest.newBuilder()
                .setName(VERSION_RESOURCE)
                .build();
        AccessSecretVersionRequest accessRequest = AccessSecretVersionRequest.newBuilder()
                .setName(SECRET_RESOURCE + "/versions/latest")
                .build();
        DisableSecretVersionRequest disableRequest = DisableSecretVersionRequest.newBuilder()
                .setName(VERSION_RESOURCE)
                .build();
        EnableSecretVersionRequest enableRequest = EnableSecretVersionRequest.newBuilder()
                .setName(VERSION_RESOURCE)
                .build();
        DestroySecretVersionRequest destroyRequest = DestroySecretVersionRequest.newBuilder()
                .setName(VERSION_RESOURCE)
                .build();

        assertThat(addRequest.getParent()).isEqualTo(SECRET_RESOURCE);
        assertThat(addRequest.getPayload().getData().toStringUtf8()).isEqualTo("new-version-data");
        assertThat(listRequest.getPageSize()).isEqualTo(10);
        assertThat(listResponse.getVersionsList())
                .extracting(SecretVersion::getState)
                .containsExactly(SecretVersion.State.ENABLED, SecretVersion.State.DISABLED);
        assertThat(listResponse.getNextPageToken()).isEqualTo("version-page-2");
        assertThat(listResponse.getTotalSize()).isEqualTo(2);
        assertThat(getRequest.getName()).isEqualTo(VERSION_RESOURCE);
        assertThat(accessRequest.getName()).endsWith("/versions/latest");
        assertThat(disableRequest.getName()).isEqualTo(VERSION_RESOURCE);
        assertThat(enableRequest.getName()).isEqualTo(VERSION_RESOURCE);
        assertThat(destroyRequest.getName()).isEqualTo(VERSION_RESOURCE);
    }

    @Test
    void serviceDescriptorExposesSecretManagerMethodsHttpBindingsAndIamOperations() {
        Descriptors.FileDescriptor descriptor = ServiceProto.getDescriptor();
        Descriptors.ServiceDescriptor service = descriptor.findServiceByName("SecretManagerService");

        assertThat(service).isNotNull();
        assertThat(service.getFullName()).isEqualTo("google.cloud.secrets.v1beta1.SecretManagerService");
        assertThat(service.getOptions().getExtension(ClientProto.defaultHost))
                .isEqualTo("secretmanager.googleapis.com");
        assertThat(service.getOptions().getExtension(ClientProto.oauthScopes))
                .isEqualTo("https://www.googleapis.com/auth/cloud-platform");
        assertThat(service.getMethods())
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

        assertHttp(service.findMethodByName("ListSecrets"), HttpRule.PatternCase.GET,
                "/v1beta1/{parent=projects/*}/secrets", "");
        assertHttp(service.findMethodByName("CreateSecret"), HttpRule.PatternCase.POST,
                "/v1beta1/{parent=projects/*}/secrets", "secret");
        assertHttp(service.findMethodByName("AddSecretVersion"), HttpRule.PatternCase.POST,
                "/v1beta1/{parent=projects/*/secrets/*}:addVersion", "*");
        assertHttp(service.findMethodByName("UpdateSecret"), HttpRule.PatternCase.PATCH,
                "/v1beta1/{secret.name=projects/*/secrets/*}", "secret");
        assertHttp(service.findMethodByName("DeleteSecret"), HttpRule.PatternCase.DELETE,
                "/v1beta1/{name=projects/*/secrets/*}", "");
        assertHttp(service.findMethodByName("AccessSecretVersion"), HttpRule.PatternCase.GET,
                "/v1beta1/{name=projects/*/secrets/*/versions/*}:access", "");
        assertHttp(service.findMethodByName("DestroySecretVersion"), HttpRule.PatternCase.POST,
                "/v1beta1/{name=projects/*/secrets/*/versions/*}:destroy", "*");

        assertThat(service.findMethodByName("SetIamPolicy").getInputType().getFullName())
                .isEqualTo("google.iam.v1.SetIamPolicyRequest");
        assertThat(service.findMethodByName("GetIamPolicy").getOutputType().getFullName())
                .isEqualTo("google.iam.v1.Policy");
        assertThat(service.findMethodByName("TestIamPermissions").getOutputType().getFullName())
                .isEqualTo("google.iam.v1.TestIamPermissionsResponse");
    }

    @Test
    void serviceDescriptorExposesFlattenedMethodSignaturesForClientHelpers() {
        Descriptors.ServiceDescriptor service = ServiceProto.getDescriptor()
                .findServiceByName("SecretManagerService");

        assertThat(service).isNotNull();
        assertMethodSignatures(service, "ListSecrets", "parent");
        assertMethodSignatures(service, "CreateSecret", "parent,secret_id,secret");
        assertMethodSignatures(service, "AddSecretVersion", "parent,payload");
        assertMethodSignatures(service, "UpdateSecret", "secret,update_mask");
        assertMethodSignatures(service, "AccessSecretVersion", "name");
        assertMethodSignatures(service, "DestroySecretVersion", "name");
    }

    @Test
    void iamPolicyRequestsTargetSecretResourcesAndPreserveConditions() {
        Binding accessorBinding = Binding.newBuilder()
                .setRole("roles/secretmanager.secretAccessor")
                .addMembers("serviceAccount:worker@example.iam.gserviceaccount.com")
                .setCondition(Expr.newBuilder()
                        .setTitle("business-hours")
                        .setDescription("Permit reads only during a controlled window.")
                        .setExpression("request.time.getHours() >= 8 && request.time.getHours() < 18"))
                .build();
        Binding adminBinding = Binding.newBuilder()
                .setRole("roles/secretmanager.admin")
                .addMembers("group:admins@example.com")
                .build();
        Policy policy = Policy.newBuilder()
                .setVersion(3)
                .addBindings(accessorBinding)
                .addBindings(adminBinding)
                .setEtag(ByteString.copyFromUtf8("policy-etag"))
                .build();

        SetIamPolicyRequest setRequest = SetIamPolicyRequest.newBuilder()
                .setResource(SECRET_RESOURCE)
                .setPolicy(policy)
                .setUpdateMask(FieldMask.newBuilder().addPaths("bindings").addPaths("etag"))
                .build();
        GetIamPolicyRequest getRequest = GetIamPolicyRequest.newBuilder()
                .setResource(SECRET_RESOURCE)
                .setOptions(GetPolicyOptions.newBuilder().setRequestedPolicyVersion(3))
                .build();
        TestIamPermissionsRequest testRequest = TestIamPermissionsRequest.newBuilder()
                .setResource(SECRET_RESOURCE)
                .addPermissions("secretmanager.secrets.get")
                .addPermissions("secretmanager.versions.access")
                .build();
        TestIamPermissionsResponse testResponse = TestIamPermissionsResponse.newBuilder()
                .addPermissions("secretmanager.secrets.get")
                .build();

        assertThat(setRequest.getResource()).isEqualTo(SECRET_RESOURCE);
        assertThat(setRequest.getPolicy().getVersion()).isEqualTo(3);
        assertThat(setRequest.getPolicy().getBindingsList()).containsExactly(accessorBinding, adminBinding);
        assertThat(setRequest.getPolicy().getBindings(0).getCondition().getExpression())
                .contains("request.time.getHours()");
        assertThat(setRequest.getUpdateMask().getPathsList()).containsExactly("bindings", "etag");
        assertThat(getRequest.getOptions().getRequestedPolicyVersion()).isEqualTo(3);
        assertThat(testRequest.getPermissionsList())
                .containsExactly("secretmanager.secrets.get", "secretmanager.versions.access");
        assertThat(testResponse.getPermissionsList()).containsExactly("secretmanager.secrets.get");
    }

    @Test
    void descriptorsExposeResourceAnnotationsFieldBehaviorAndMessageShape() {
        Descriptors.FileDescriptor resourcesDescriptor = ResourcesProto.getDescriptor();
        Descriptors.Descriptor secretDescriptor = resourcesDescriptor.findMessageTypeByName("Secret");
        Descriptors.Descriptor secretVersionDescriptor = resourcesDescriptor.findMessageTypeByName("SecretVersion");
        Descriptors.Descriptor replicationDescriptor = resourcesDescriptor.findMessageTypeByName("Replication");
        ResourceDescriptor secretResource = secretDescriptor.getOptions().getExtension(ResourceProto.resource);
        ResourceDescriptor versionResource = secretVersionDescriptor.getOptions().getExtension(ResourceProto.resource);

        assertThat(secretResource.getType()).isEqualTo("secretmanager.googleapis.com/Secret");
        assertThat(secretResource.getPatternList()).containsExactly("projects/{project}/secrets/{secret}");
        assertThat(versionResource.getType()).isEqualTo("secretmanager.googleapis.com/SecretVersion");
        assertThat(versionResource.getPatternList())
                .containsExactly("projects/{project}/secrets/{secret}/versions/{secret_version}");
        assertThat(secretDescriptor.findFieldByName("name").getOptions()
                .getExtension(FieldBehaviorProto.fieldBehavior))
                .containsExactly(FieldBehavior.OUTPUT_ONLY);
        assertThat(secretDescriptor.findFieldByName("replication").getOptions()
                .getExtension(FieldBehaviorProto.fieldBehavior))
                .containsExactly(FieldBehavior.IMMUTABLE, FieldBehavior.REQUIRED);
        assertThat(secretVersionDescriptor.findFieldByName("state").getOptions()
                .getExtension(FieldBehaviorProto.fieldBehavior))
                .containsExactly(FieldBehavior.OUTPUT_ONLY);
        assertThat(replicationDescriptor.getOneofs())
                .extracting(Descriptors.OneofDescriptor::getName)
                .containsExactly("replication");
        assertThat(replicationDescriptor.findNestedTypeByName("UserManaged").findNestedTypeByName("Replica")
                .findFieldByName("location").getType())
                .isEqualTo(Descriptors.FieldDescriptor.Type.STRING);
        assertThat(Secret.getDescriptor().findFieldByName("labels").isMapField()).isTrue();
        assertThat(SecretPayload.getDescriptor().findFieldByName("data").getType())
                .isEqualTo(Descriptors.FieldDescriptor.Type.BYTES);
    }

    private static void assertMethodSignatures(
            Descriptors.ServiceDescriptor service,
            String methodName,
            String... expectedSignatures) {
        Descriptors.MethodDescriptor method = service.findMethodByName(methodName);

        assertThat(method).isNotNull();
        assertThat(method.getOptions().getExtension(ClientProto.methodSignature))
                .containsExactly(expectedSignatures);
    }

    private static void assertHttp(
            Descriptors.MethodDescriptor method,
            HttpRule.PatternCase patternCase,
            String path,
            String body) {
        assertThat(method).isNotNull();
        assertThat(method.isClientStreaming()).isFalse();
        assertThat(method.isServerStreaming()).isFalse();
        assertThat(method.getOptions().hasExtension(AnnotationsProto.http)).isTrue();
        HttpRule httpRule = method.getOptions().getExtension(AnnotationsProto.http);

        assertThat(httpRule.getPatternCase()).isEqualTo(patternCase);
        if (patternCase == HttpRule.PatternCase.GET) {
            assertThat(httpRule.getGet()).isEqualTo(path);
        } else if (patternCase == HttpRule.PatternCase.POST) {
            assertThat(httpRule.getPost()).isEqualTo(path);
        } else if (patternCase == HttpRule.PatternCase.PATCH) {
            assertThat(httpRule.getPatch()).isEqualTo(path);
        } else if (patternCase == HttpRule.PatternCase.DELETE) {
            assertThat(httpRule.getDelete()).isEqualTo(path);
        }
        assertThat(httpRule.getBody()).isEqualTo(body);
    }

    private static Timestamp timestamp(long seconds) {
        return Timestamp.newBuilder().setSeconds(seconds).build();
    }
}
