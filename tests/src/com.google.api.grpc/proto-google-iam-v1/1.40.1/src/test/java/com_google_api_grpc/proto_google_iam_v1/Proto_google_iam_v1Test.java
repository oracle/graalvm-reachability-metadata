/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_iam_v1;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.AnnotationsProto;
import com.google.api.ClientProto;
import com.google.api.FieldBehavior;
import com.google.api.FieldBehaviorProto;
import com.google.api.HttpRule;
import com.google.api.ResourceProto;
import com.google.api.ResourceReference;
import com.google.iam.v1.AuditConfig;
import com.google.iam.v1.AuditConfigDelta;
import com.google.iam.v1.AuditLogConfig;
import com.google.iam.v1.Binding;
import com.google.iam.v1.BindingDelta;
import com.google.iam.v1.GetIamPolicyRequest;
import com.google.iam.v1.GetPolicyOptions;
import com.google.iam.v1.IamPolicyProto;
import com.google.iam.v1.Policy;
import com.google.iam.v1.PolicyDelta;
import com.google.iam.v1.PolicyProto;
import com.google.iam.v1.SetIamPolicyRequest;
import com.google.iam.v1.TestIamPermissionsRequest;
import com.google.iam.v1.TestIamPermissionsResponse;
import com.google.iam.v1.logging.AuditData;
import com.google.iam.v1.logging.AuditDataProto;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.Descriptors;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.FieldMask;
import com.google.type.Expr;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

public class Proto_google_iam_v1Test {
    private static final String RESOURCE = "projects/sample-project/topics/alerts";
    private static final ByteString ETAG = ByteString.copyFromUtf8("policy-etag");

    @Test
    void policyWithConditionalBindingsAndAuditConfigsRoundTripsThroughPublicProtoApis() throws Exception {
        Expr accessWindow = Expr.newBuilder()
                .setTitle("business-hours")
                .setDescription("Allow access only during business hours")
                .setExpression("request.time.getHours() >= 9 && request.time.getHours() < 17")
                .setLocation("iam-condition")
                .build();
        Binding viewerBinding = Binding.newBuilder()
                .setRole("roles/viewer")
                .addMembers("user:alice@example.com")
                .addMembers("group:auditors@example.com")
                .setCondition(accessWindow)
                .build();
        Binding writerBinding = Binding.newBuilder()
                .setRole("roles/logging.logWriter")
                .addMembers("serviceAccount:writer@example.iam.gserviceaccount.com")
                .build();
        AuditLogConfig readAudit = AuditLogConfig.newBuilder()
                .setLogType(AuditLogConfig.LogType.DATA_READ)
                .addExemptedMembers("user:breakglass@example.com")
                .build();
        AuditLogConfig adminAudit = AuditLogConfig.newBuilder()
                .setLogType(AuditLogConfig.LogType.ADMIN_READ)
                .build();
        AuditConfig auditConfig = AuditConfig.newBuilder()
                .setService("allServices")
                .addAuditLogConfigs(readAudit)
                .addAuditLogConfigs(adminAudit)
                .build();

        Policy policy = Policy.newBuilder()
                .setVersion(3)
                .addBindings(viewerBinding)
                .addBindings(writerBinding)
                .addAuditConfigs(auditConfig)
                .setEtag(ETAG)
                .build();

        assertThat(policy.isInitialized()).isTrue();
        assertThat(policy.getVersion()).isEqualTo(3);
        assertThat(policy.getBindingsList()).containsExactly(viewerBinding, writerBinding);
        assertThat(policy.getBindings(0).getCondition()).isEqualTo(accessWindow);
        assertThat(policy.getAuditConfigs(0).getAuditLogConfigsList()).containsExactly(readAudit, adminAudit);
        assertThat(policy.getEtag()).isEqualTo(ETAG);

        byte[] bytes = policy.toByteArray();
        assertThat(Policy.parseFrom(bytes)).isEqualTo(policy);
        assertThat(Policy.parseFrom(ByteString.copyFrom(bytes))).isEqualTo(policy);
        assertThat(Policy.parseFrom(ByteBuffer.wrap(bytes))).isEqualTo(policy);
        assertThat(Policy.parser().parseFrom(bytes)).isEqualTo(policy);

        ByteArrayOutputStream delimitedBytes = new ByteArrayOutputStream();
        policy.writeDelimitedTo(delimitedBytes);
        assertThat(Policy.parseDelimitedFrom(new ByteArrayInputStream(delimitedBytes.toByteArray()))).isEqualTo(policy);

        Policy editedPolicy = policy.toBuilder()
                .removeBindings(1)
                .addBindings(writerBinding.toBuilder().addMembers("user:bob@example.com"))
                .build();
        assertThat(editedPolicy.getBindingsCount()).isEqualTo(2);
        assertThat(editedPolicy.getBindings(1).getMembersList())
                .containsExactly("serviceAccount:writer@example.iam.gserviceaccount.com", "user:bob@example.com");
        assertThat(editedPolicy).isNotEqualTo(policy);
    }

    @Test
    void auditLogConfigPreservesForwardCompatibleLogTypeValues() {
        int futureLogType = 8675309;
        AuditLogConfig futureAuditLogConfig = AuditLogConfig.newBuilder()
                .setLogTypeValue(futureLogType)
                .addExemptedMembers("serviceAccount:future-auditor@example.iam.gserviceaccount.com")
                .build();
        AuditConfig auditConfig = AuditConfig.newBuilder()
                .setService("storage.googleapis.com")
                .addAuditLogConfigs(futureAuditLogConfig)
                .build();

        assertThat(futureAuditLogConfig.getLogTypeValue()).isEqualTo(futureLogType);
        assertThat(futureAuditLogConfig.getLogType()).isEqualTo(AuditLogConfig.LogType.UNRECOGNIZED);
        assertThat(auditConfig.getAuditLogConfigs(0).getLogTypeValue()).isEqualTo(futureLogType);
        assertThat(auditConfig.getAuditLogConfigs(0).getExemptedMembersList())
                .containsExactly("serviceAccount:future-auditor@example.iam.gserviceaccount.com");

        AuditLogConfig recognizedAuditLogConfig = futureAuditLogConfig.toBuilder()
                .setLogType(AuditLogConfig.LogType.DATA_WRITE)
                .build();
        assertThat(recognizedAuditLogConfig.getLogType()).isEqualTo(AuditLogConfig.LogType.DATA_WRITE);
        assertThat(recognizedAuditLogConfig.getLogTypeValue())
                .isEqualTo(AuditLogConfig.LogType.DATA_WRITE_VALUE);

        AuditLogConfig clearedAuditLogConfig = recognizedAuditLogConfig.toBuilder()
                .clearLogType()
                .build();
        assertThat(clearedAuditLogConfig.getLogType()).isEqualTo(AuditLogConfig.LogType.LOG_TYPE_UNSPECIFIED);
        assertThat(clearedAuditLogConfig.getLogTypeValue())
                .isEqualTo(AuditLogConfig.LogType.LOG_TYPE_UNSPECIFIED_VALUE);
    }

    @Test
    void iamPolicyRequestAndResponseMessagesPreserveNestedOptionsMasksAndRepeatedFields() throws Exception {
        Policy policy = Policy.newBuilder()
                .setVersion(1)
                .addBindings(Binding.newBuilder()
                        .setRole("roles/pubsub.viewer")
                        .addMembers("domain:example.com"))
                .setEtag(ETAG)
                .build();
        FieldMask updateMask = FieldMask.newBuilder()
                .addPaths("bindings")
                .addPaths("etag")
                .build();
        SetIamPolicyRequest setRequest = SetIamPolicyRequest.newBuilder()
                .setResource(RESOURCE)
                .setPolicy(policy)
                .setUpdateMask(updateMask)
                .build();

        assertThat(setRequest.hasPolicy()).isTrue();
        assertThat(setRequest.hasUpdateMask()).isTrue();
        assertThat(setRequest.getResource()).isEqualTo(RESOURCE);
        assertThat(setRequest.getResourceBytes()).isEqualTo(ByteString.copyFromUtf8(RESOURCE));
        assertThat(SetIamPolicyRequest.parseFrom(setRequest.toByteArray())).isEqualTo(setRequest);

        GetIamPolicyRequest getRequest = GetIamPolicyRequest.newBuilder()
                .setResource(RESOURCE)
                .setOptions(GetPolicyOptions.newBuilder().setRequestedPolicyVersion(3))
                .build();
        assertThat(getRequest.hasOptions()).isTrue();
        assertThat(getRequest.getOptions().getRequestedPolicyVersion()).isEqualTo(3);
        assertThat(GetIamPolicyRequest.parseFrom(new ByteArrayInputStream(getRequest.toByteArray())))
                .isEqualTo(getRequest);

        TestIamPermissionsRequest permissionsRequest = TestIamPermissionsRequest.newBuilder()
                .setResource(RESOURCE)
                .addPermissions("pubsub.topics.get")
                .addPermissions("pubsub.topics.publish")
                .build();
        CodedInputStream codedInputStream = CodedInputStream.newInstance(permissionsRequest.toByteArray());
        assertThat(TestIamPermissionsRequest.parseFrom(codedInputStream)).isEqualTo(permissionsRequest);
        assertThat(permissionsRequest.getPermissionsList())
                .containsExactly("pubsub.topics.get", "pubsub.topics.publish");

        TestIamPermissionsResponse permissionsResponse = TestIamPermissionsResponse.newBuilder()
                .addPermissions("pubsub.topics.get")
                .build();
        assertThat(TestIamPermissionsResponse.parseFrom(permissionsResponse.toByteArray()))
                .isEqualTo(permissionsResponse);
        assertThat(permissionsResponse.getPermissionsBytes(0)).isEqualTo(ByteString.copyFromUtf8("pubsub.topics.get"));
    }

    @Test
    void policyDeltaAndLoggingAuditDataRetainBindingAndAuditChanges() throws Exception {
        Expr condition = Expr.newBuilder()
                .setTitle("expires-soon")
                .setExpression("request.time < timestamp('2030-01-01T00:00:00Z')")
                .build();
        BindingDelta bindingAdded = BindingDelta.newBuilder()
                .setAction(BindingDelta.Action.ADD)
                .setRole("roles/storage.objectViewer")
                .setMember("user:carol@example.com")
                .setCondition(condition)
                .build();
        BindingDelta bindingRemoved = BindingDelta.newBuilder()
                .setAction(BindingDelta.Action.REMOVE)
                .setRole("roles/storage.legacyBucketReader")
                .setMember("allAuthenticatedUsers")
                .build();
        AuditConfigDelta auditAdded = AuditConfigDelta.newBuilder()
                .setAction(AuditConfigDelta.Action.ADD)
                .setService("storage.googleapis.com")
                .setExemptedMember("user:jose@example.com")
                .setLogType("DATA_READ")
                .build();
        PolicyDelta delta = PolicyDelta.newBuilder()
                .addBindingDeltas(bindingAdded)
                .addBindingDeltas(bindingRemoved)
                .addAuditConfigDeltas(auditAdded)
                .build();
        AuditData auditData = AuditData.newBuilder().setPolicyDelta(delta).build();

        assertThat(BindingDelta.Action.forNumber(BindingDelta.Action.ADD_VALUE)).isEqualTo(BindingDelta.Action.ADD);
        assertThat(AuditConfigDelta.Action.forNumber(AuditConfigDelta.Action.REMOVE_VALUE))
                .isEqualTo(AuditConfigDelta.Action.REMOVE);
        assertThat(AuditLogConfig.LogType.forNumber(AuditLogConfig.LogType.DATA_WRITE_VALUE))
                .isEqualTo(AuditLogConfig.LogType.DATA_WRITE);
        assertThat(auditData.hasPolicyDelta()).isTrue();
        assertThat(auditData.getPolicyDelta().getBindingDeltasList()).containsExactly(bindingAdded, bindingRemoved);
        assertThat(auditData.getPolicyDelta().getAuditConfigDeltasList()).containsExactly(auditAdded);
        assertThat(auditData.getPolicyDelta().getBindingDeltas(0).getCondition()).isEqualTo(condition);

        assertThat(PolicyDelta.parseFrom(delta.toByteArray())).isEqualTo(delta);
        assertThat(AuditData.parseFrom(auditData.toByteArray())).isEqualTo(auditData);
        assertThat(AuditData.parser().parseFrom(ByteString.copyFrom(auditData.toByteArray()))).isEqualTo(auditData);
    }

    @Test
    void auditDataPacksIntoAnyServiceDataAndUnpacksByTypeUrl() throws Exception {
        PolicyDelta policyDelta = PolicyDelta.newBuilder()
                .addBindingDeltas(BindingDelta.newBuilder()
                        .setAction(BindingDelta.Action.ADD)
                        .setRole("roles/iam.securityReviewer")
                        .setMember("group:security@example.com"))
                .build();
        AuditData auditData = AuditData.newBuilder()
                .setPolicyDelta(policyDelta)
                .build();

        Any serviceData = Any.pack(auditData);

        assertThat(serviceData.getTypeUrl()).isEqualTo("type.googleapis.com/google.iam.v1.logging.AuditData");
        assertThat(serviceData.is(AuditData.class)).isTrue();
        assertThat(serviceData.unpack(AuditData.class)).isEqualTo(auditData);
        assertThat(serviceData.unpack(AuditData.class).getPolicyDelta().getBindingDeltas(0).getMember())
                .isEqualTo("group:security@example.com");
    }

    @Test
    void generatedDescriptorsExposeServiceHttpRulesAndIamFieldAnnotations() {
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        IamPolicyProto.registerAllExtensions(registry);
        PolicyProto.registerAllExtensions(registry);
        AuditDataProto.registerAllExtensions(registry);

        Descriptors.FileDescriptor iamDescriptor = IamPolicyProto.getDescriptor();
        Descriptors.ServiceDescriptor iamService = iamDescriptor.findServiceByName("IAMPolicy");
        assertThat(iamService).isNotNull();
        assertThat(iamService.getFullName()).isEqualTo("google.iam.v1.IAMPolicy");
        assertThat(iamService.getOptions().getExtension(ClientProto.defaultHost))
                .isEqualTo("iam-meta-api.googleapis.com");

        Descriptors.MethodDescriptor setIamPolicy = iamService.findMethodByName("SetIamPolicy");
        assertThat(setIamPolicy.getInputType().getFullName()).isEqualTo("google.iam.v1.SetIamPolicyRequest");
        assertThat(setIamPolicy.getOutputType().getFullName()).isEqualTo("google.iam.v1.Policy");
        HttpRule setRule = setIamPolicy.getOptions().getExtension(AnnotationsProto.http);
        assertThat(setRule.getPost()).isEqualTo("/v1/{resource=**}:setIamPolicy");
        assertThat(setRule.getBody()).isEqualTo("*");

        Descriptors.MethodDescriptor testPermissions = iamService.findMethodByName("TestIamPermissions");
        HttpRule testRule = testPermissions.getOptions().getExtension(AnnotationsProto.http);
        assertThat(testRule.getPost()).isEqualTo("/v1/{resource=**}:testIamPermissions");
        assertThat(testPermissions.getOutputType().getFullName())
                .isEqualTo("google.iam.v1.TestIamPermissionsResponse");

        Descriptors.FieldDescriptor resourceField = SetIamPolicyRequest.getDescriptor().findFieldByName("resource");
        assertThat(resourceField.getOptions().getExtension(FieldBehaviorProto.fieldBehavior))
                .containsExactly(FieldBehavior.REQUIRED);
        ResourceReference resourceReference = resourceField.getOptions().getExtension(ResourceProto.resourceReference);
        assertThat(resourceReference.getType()).isEqualTo("*");

        assertThat(Policy.getDescriptor().findFieldByName("bindings").getNumber())
                .isEqualTo(Policy.BINDINGS_FIELD_NUMBER);
        assertThat(AuditData.getDescriptor().findFieldByName("policy_delta").getMessageType().getFullName())
                .isEqualTo("google.iam.v1.PolicyDelta");
    }
}
