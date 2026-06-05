/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_admissionregistration;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReviewBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.MatchResources;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.MatchResourcesBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.MutatingWebhookConfiguration;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.MutatingWebhookConfigurationBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.MutatingWebhookConfigurationList;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.MutatingWebhookConfigurationListBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingAdmissionPolicy;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingAdmissionPolicyBinding;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingAdmissionPolicyBindingBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingAdmissionPolicyBindingList;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingAdmissionPolicyBindingListBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingAdmissionPolicyBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingAdmissionPolicyList;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingAdmissionPolicyListBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfiguration;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfigurationBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfigurationList;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ValidatingWebhookConfigurationListBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

public class Kubernetes_model_admissionregistrationTest {
    @Test
    void buildsAndEditsStableWebhookConfigurationsWithSelectorsRulesAndClientConfig() {
        MutatingWebhookConfiguration mutatingConfiguration = new MutatingWebhookConfigurationBuilder()
                .withNewMetadata()
                    .withName("pod-defaulting")
                    .addToLabels("app.kubernetes.io/component", "admission")
                    .addToAnnotations("admissionregistration.fabric8.io/source", "test")
                .endMetadata()
                .addNewWebhook()
                    .withName("pod-defaulting.example.test")
                    .withAdmissionReviewVersions("v1", "v1beta1")
                    .withFailurePolicy("Fail")
                    .withMatchPolicy("Equivalent")
                    .withReinvocationPolicy("IfNeeded")
                    .withSideEffects("None")
                    .withTimeoutSeconds(5)
                    .withNewClientConfig()
                        .withCaBundle("LS0tLS1DQS0tLS0=")
                        .withNewService("pod-defaulting", "admission-system", "/mutate", 443)
                    .endClientConfig()
                    .withNewNamespaceSelector()
                        .addToMatchLabels("admission", "enabled")
                    .endNamespaceSelector()
                    .withNewObjectSelector()
                        .addToMatchLabels("mutate", "true")
                    .endObjectSelector()
                    .addNewMatchCondition()
                        .withName("pods-only")
                        .withExpression("request.resource.resource == 'pods'")
                    .endMatchCondition()
                    .addNewRule()
                        .withOperations("CREATE", "UPDATE")
                        .withApiGroups("")
                        .withApiVersions("v1")
                        .withResources("pods")
                        .withScope("Namespaced")
                    .endRule()
                    .addToAdditionalProperties("webhook-index", 1)
                .endWebhook()
                .addToAdditionalProperties("managed-by", "fabric8-test")
                .build();

        assertThat(mutatingConfiguration).isInstanceOf(HasMetadata.class);
        assertThat(mutatingConfiguration.getApiVersion()).isEqualTo("admissionregistration.k8s.io/v1");
        assertThat(mutatingConfiguration.getKind()).isEqualTo("MutatingWebhookConfiguration");
        assertThat(mutatingConfiguration.getMetadata().getLabels())
                .containsEntry("app.kubernetes.io/component", "admission");
        assertThat(mutatingConfiguration.getWebhooks()).hasSize(1);
        assertThat(mutatingConfiguration.getWebhooks().get(0).getClientConfig().getService().getName())
                .isEqualTo("pod-defaulting");
        assertThat(mutatingConfiguration.getWebhooks().get(0).getClientConfig().getService().getPath())
                .isEqualTo("/mutate");
        assertThat(mutatingConfiguration.getWebhooks().get(0).getRules().get(0).getOperations())
                .containsExactly("CREATE", "UPDATE");
        assertThat(mutatingConfiguration.getWebhooks().get(0).getMatchConditions().get(0).getExpression())
                .contains("request.resource.resource");
        assertThat(mutatingConfiguration.getWebhooks().get(0).getAdditionalProperties())
                .containsEntry("webhook-index", 1);

        MutatingWebhookConfiguration edited = mutatingConfiguration.toBuilder()
                .editMetadata()
                    .addToAnnotations("edited", "true")
                .endMetadata()
                .editFirstWebhook()
                    .withFailurePolicy("Ignore")
                    .editClientConfig()
                        .editService()
                            .withPort(8443)
                        .endService()
                    .endClientConfig()
                    .editFirstRule()
                        .withResources("pods", "pods/status")
                    .endRule()
                .endWebhook()
                .removeFromAdditionalProperties("managed-by")
                .build();

        assertThat(edited).isNotEqualTo(mutatingConfiguration);
        assertThat(edited.toBuilder().build()).isEqualTo(edited);
        assertThat(edited.getMetadata().getAnnotations()).containsEntry("edited", "true");
        assertThat(edited.getWebhooks().get(0).getFailurePolicy()).isEqualTo("Ignore");
        assertThat(edited.getWebhooks().get(0).getClientConfig().getService().getPort()).isEqualTo(8443);
        assertThat(edited.getWebhooks().get(0).getRules().get(0).getResources())
                .containsExactly("pods", "pods/status");
        assertThat(mutatingConfiguration.getWebhooks().get(0).getFailurePolicy()).isEqualTo("Fail");

        ValidatingWebhookConfiguration validatingConfiguration = new ValidatingWebhookConfigurationBuilder()
                .withNewMetadata()
                    .withName("pod-validation")
                .endMetadata()
                .addNewWebhook()
                    .withName("pod-validation.example.test")
                    .withAdmissionReviewVersions("v1")
                    .withFailurePolicy("Fail")
                    .withMatchPolicy("Equivalent")
                    .withSideEffects("None")
                    .withTimeoutSeconds(7)
                    .withNewClientConfig()
                        .withUrl("https://admission.example.test/validate")
                    .endClientConfig()
                    .addNewRule()
                        .withOperations("CREATE")
                        .withApiGroups("apps")
                        .withApiVersions("v1")
                        .withResources("deployments")
                        .withScope("Namespaced")
                    .endRule()
                .endWebhook()
                .build();

        assertThat(validatingConfiguration.getKind()).isEqualTo("ValidatingWebhookConfiguration");
        assertThat(validatingConfiguration.getWebhooks().get(0).getClientConfig().getUrl())
                .isEqualTo("https://admission.example.test/validate");
        assertThat(validatingConfiguration.getWebhooks().get(0).getRules().get(0).getApiGroups())
                .containsExactly("apps");
    }

    @Test
    void webhookConfigurationListsSupportPredicateEditsAndItemCopies() {
        MutatingWebhookConfiguration defaulting = mutatingConfiguration("pod-defaulting", "pods", "mutate");
        MutatingWebhookConfiguration imagePolicy = mutatingConfiguration("image-policy", "pods", "images");

        MutatingWebhookConfigurationList mutatingList = new MutatingWebhookConfigurationListBuilder()
                .withNewMetadata("continue-token", 2L, "17", "self-link")
                .withItems(defaulting, imagePolicy)
                .editMatchingItem(item -> "image-policy".equals(item.buildMetadata().getName()))
                    .editFirstWebhook()
                        .withTimeoutSeconds(10)
                    .endWebhook()
                .endItem()
                .addNewItemLike(defaulting)
                    .editMetadata()
                        .withName("pod-defaulting-canary")
                    .endMetadata()
                    .editFirstWebhook()
                        .withName("pod-defaulting-canary.example.test")
                    .endWebhook()
                .endItem()
                .addToAdditionalProperties("list-source", "watch-cache")
                .build();

        assertThat(mutatingList.getApiVersion()).isEqualTo("admissionregistration.k8s.io/v1");
        assertThat(mutatingList.getKind()).isEqualTo("MutatingWebhookConfigurationList");
        assertThat(mutatingList.getMetadata().getContinue()).isEqualTo("continue-token");
        assertThat(mutatingList.getItems())
                .extracting(item -> item.getMetadata().getName())
                .containsExactly("pod-defaulting", "image-policy", "pod-defaulting-canary");
        assertThat(mutatingList.getItems().get(1).getWebhooks().get(0).getTimeoutSeconds()).isEqualTo(10);
        assertThat(mutatingList.getAdditionalProperties()).containsEntry("list-source", "watch-cache");

        MutatingWebhookConfigurationList reduced = mutatingList.toBuilder()
                .removeMatchingFromItems(item -> item.buildMetadata().getName().endsWith("canary"))
                .build();
        assertThat(reduced.getItems())
                .extracting(item -> item.getMetadata().getName())
                .containsExactly("pod-defaulting", "image-policy");
        assertThat(mutatingList.getItems()).hasSize(3);

        ValidatingWebhookConfiguration validating = new ValidatingWebhookConfigurationBuilder()
                .withNewMetadata()
                    .withName("validation")
                .endMetadata()
                .addNewWebhook()
                    .withName("validation.example.test")
                    .withAdmissionReviewVersions("v1")
                    .withFailurePolicy("Fail")
                    .withSideEffects("None")
                    .withNewClientConfig()
                        .withNewService("validation", "admission-system", "/validate", 443)
                    .endClientConfig()
                    .addNewRule()
                        .withOperations("CREATE")
                        .withApiGroups("apps")
                        .withApiVersions("v1")
                        .withResources("deployments")
                    .endRule()
                .endWebhook()
                .build();
        ValidatingWebhookConfigurationList validatingList = new ValidatingWebhookConfigurationListBuilder()
                .withNewMetadata(null, 1L, "18", null)
                .addToItems(validating)
                .build();

        assertThat(validatingList.getKind()).isEqualTo("ValidatingWebhookConfigurationList");
        assertThat(validatingList.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("validation");
    }

    @Test
    void validatingAdmissionPolicyModelsCelExpressionsBindingsStatusAndLists() {
        MatchResources podMatchResources = podMatchResources("validate", "production");
        ValidatingAdmissionPolicy policy = new ValidatingAdmissionPolicyBuilder()
                .withNewMetadata()
                    .withName("require-team-label")
                    .addToLabels("policy", "labels")
                .endMetadata()
                .withNewSpec()
                    .withFailurePolicy("Fail")
                    .withNewParamKind("rules.example.test/v1", "LabelPolicy")
                    .withMatchConstraints(podMatchResources)
                    .addNewVariable()
                        .withName("labels")
                        .withExpression("object.metadata.labels")
                    .endVariable()
                    .addNewMatchCondition()
                        .withName("has-team-label")
                        .withExpression("has(object.metadata.labels['team'])")
                    .endMatchCondition()
                    .addNewValidation()
                        .withExpression("object.metadata.labels['team'] == params.team")
                        .withMessage("team label must match the policy parameter")
                        .withMessageExpression("'invalid team label: ' + object.metadata.name")
                        .withReason("Invalid")
                    .endValidation()
                    .addNewAuditAnnotation()
                        .withKey("team-policy")
                        .withValueExpression("params.team")
                    .endAuditAnnotation()
                .endSpec()
                .withNewStatus()
                    .withObservedGeneration(3L)
                    .withConditions(new io.fabric8.kubernetes.api.model.ConditionBuilder()
                            .withType("Ready")
                            .withStatus("True")
                            .withReason("TypeChecked")
                            .withMessage("Policy expressions are type checked")
                            .build())
                    .withNewTypeChecking()
                        .addNewExpressionWarning()
                            .withFieldRef("spec.validations[0].expression")
                            .withWarning("expression relies on params")
                        .endExpressionWarning()
                    .endTypeChecking()
                .endStatus()
                .addToAdditionalProperties("policy-source", "unit-test")
                .build();

        assertThat(policy).isInstanceOf(HasMetadata.class);
        assertThat(policy.getApiVersion()).isEqualTo("admissionregistration.k8s.io/v1");
        assertThat(policy.getKind()).isEqualTo("ValidatingAdmissionPolicy");
        assertThat(policy.getSpec().getParamKind().getKind()).isEqualTo("LabelPolicy");
        assertThat(policy.getSpec().getMatchConstraints().getResourceRules().get(0).getResourceNames())
                .containsExactly("validate");
        assertThat(policy.getSpec().getVariables().get(0).getName()).isEqualTo("labels");
        assertThat(policy.getSpec().getValidations().get(0).getMessage()).contains("team label");
        assertThat(policy.getSpec().getAuditAnnotations().get(0).getValueExpression()).isEqualTo("params.team");
        assertThat(policy.getStatus().getObservedGeneration()).isEqualTo(3L);
        assertThat(policy.getStatus().getConditions().get(0).getReason()).isEqualTo("TypeChecked");
        assertThat(policy.getStatus().getTypeChecking().getExpressionWarnings().get(0).getFieldRef())
                .isEqualTo("spec.validations[0].expression");
        assertThat(policy.getAdditionalProperties()).containsEntry("policy-source", "unit-test");

        ValidatingAdmissionPolicy editedPolicy = policy.toBuilder()
                .editSpec()
                    .withFailurePolicy("Ignore")
                    .editFirstValidation()
                        .withReason("Forbidden")
                    .endValidation()
                .endSpec()
                .editStatus()
                    .withObservedGeneration(4L)
                .endStatus()
                .build();
        assertThat(editedPolicy.getSpec().getFailurePolicy()).isEqualTo("Ignore");
        assertThat(editedPolicy.getSpec().getValidations().get(0).getReason()).isEqualTo("Forbidden");
        assertThat(editedPolicy.getStatus().getObservedGeneration()).isEqualTo(4L);
        assertThat(policy.getSpec().getFailurePolicy()).isEqualTo("Fail");

        ValidatingAdmissionPolicyBinding binding = new ValidatingAdmissionPolicyBindingBuilder()
                .withNewMetadata()
                    .withName("require-team-label-production")
                .endMetadata()
                .withNewSpec()
                    .withPolicyName("require-team-label")
                    .withValidationActions("Deny", "Audit")
                    .withMatchResources(podMatchResources)
                    .withNewParamRef()
                        .withName("team-platform")
                        .withNamespace("production")
                        .withParameterNotFoundAction("Deny")
                        .withNewSelector()
                            .addToMatchLabels("team", "platform")
                        .endSelector()
                    .endParamRef()
                .endSpec()
                .build();

        assertThat(binding.getApiVersion()).isEqualTo("admissionregistration.k8s.io/v1");
        assertThat(binding.getKind()).isEqualTo("ValidatingAdmissionPolicyBinding");
        assertThat(binding.getSpec().getPolicyName()).isEqualTo("require-team-label");
        assertThat(binding.getSpec().getValidationActions()).containsExactly("Deny", "Audit");
        assertThat(binding.getSpec().getParamRef().getSelector().getMatchLabels()).containsEntry("team", "platform");

        ValidatingAdmissionPolicyList policyList = new ValidatingAdmissionPolicyListBuilder()
                .withNewMetadata(null, 1L, "27", null)
                .withItems(policy, editedPolicy)
                .build();
        ValidatingAdmissionPolicyBindingList bindingList = new ValidatingAdmissionPolicyBindingListBuilder()
                .withNewMetadata("next", 1L, "28", null)
                .addToItems(binding)
                .build();

        assertThat(policyList.getKind()).isEqualTo("ValidatingAdmissionPolicyList");
        assertThat(policyList.getItems()).extracting(item -> item.getSpec().getFailurePolicy())
                .containsExactly("Fail", "Ignore");
        assertThat(bindingList.getKind()).isEqualTo("ValidatingAdmissionPolicyBindingList");
        assertThat(bindingList.getItems()).extracting(item -> item.getSpec().getPolicyName())
                .containsExactly("require-team-label");
    }

    @Test
    void betaWebhookAndMutatingPolicyResourcesSupportCurrentAndPreviewShapes() {
        io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.MutatingWebhookConfiguration betaWebhook =
                new io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.MutatingWebhookConfigurationBuilder()
                .withNewMetadata()
                    .withName("beta-defaulting")
                .endMetadata()
                .addNewWebhook()
                    .withName("beta-defaulting.example.test")
                    .withAdmissionReviewVersions("v1beta1", "v1")
                    .withFailurePolicy("Ignore")
                    .withMatchPolicy("Exact")
                    .withReinvocationPolicy("Never")
                    .withSideEffects("None")
                    .withNewClientConfig()
                        .withNewService("beta-defaulting", "admission-system", "/mutate", 443)
                    .endClientConfig()
                    .addNewRule()
                        .withOperations("CREATE")
                        .withApiGroups("")
                        .withApiVersions("v1")
                        .withResources("pods")
                    .endRule()
                .endWebhook()
                .build();

        assertThat(betaWebhook.getApiVersion()).isEqualTo("admissionregistration.k8s.io/v1beta1");
        assertThat(betaWebhook.getKind()).isEqualTo("MutatingWebhookConfiguration");
        assertThat(betaWebhook.getWebhooks().get(0).getClientConfig().getService().getNamespace())
                .isEqualTo("admission-system");
        assertThat(betaWebhook.toBuilder()
                .editFirstWebhook()
                    .withTimeoutSeconds(12)
                .endWebhook()
                .build()
                .getWebhooks().get(0).getTimeoutSeconds()).isEqualTo(12);

        io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.ValidatingWebhookConfiguration betaValidation =
                new io.fabric8.kubernetes.api.model.admissionregistration.v1beta1
                        .ValidatingWebhookConfigurationBuilder()
                .withNewMetadata()
                    .withName("beta-validation")
                .endMetadata()
                .addNewWebhook()
                    .withName("beta-validation.example.test")
                    .withAdmissionReviewVersions("v1beta1")
                    .withFailurePolicy("Fail")
                    .withSideEffects("None")
                    .withNewClientConfig()
                        .withUrl("https://beta-validation.example.test/validate")
                    .endClientConfig()
                    .addNewRule()
                        .withOperations("UPDATE")
                        .withApiGroups("apps")
                        .withApiVersions("v1")
                        .withResources("deployments")
                    .endRule()
                .endWebhook()
                .build();
        assertThat(betaValidation.getKind()).isEqualTo("ValidatingWebhookConfiguration");
        assertThat(betaValidation.getWebhooks().get(0).getClientConfig().getUrl()).contains("beta-validation");

        io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.MutatingAdmissionPolicy policy =
                betaMutatingPolicy("beta-mutate-labels", "ApplyConfiguration");
        io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.MutatingAdmissionPolicyBinding binding =
                new io.fabric8.kubernetes.api.model.admissionregistration.v1beta1
                        .MutatingAdmissionPolicyBindingBuilder()
                .withNewMetadata()
                    .withName("beta-mutate-labels-binding")
                .endMetadata()
                .withNewSpec()
                    .withPolicyName("beta-mutate-labels")
                    .withMatchResources(betaPodMatchResources("mutate", "staging"))
                    .withNewParamRef()
                        .withName("default-labels")
                        .withNamespace("staging")
                        .withParameterNotFoundAction("Allow")
                    .endParamRef()
                .endSpec()
                .build();
        io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.MutatingAdmissionPolicyList policyList =
                new io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.MutatingAdmissionPolicyListBuilder()
                .withNewMetadata(null, 2L, "31", null)
                .withItems(policy)
                .build();
        io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.MutatingAdmissionPolicyBindingList bindingList =
                new io.fabric8.kubernetes.api.model.admissionregistration.v1beta1
                        .MutatingAdmissionPolicyBindingListBuilder()
                .withNewMetadata(null, 1L, "32", null)
                .withItems(binding)
                .build();

        assertThat(policy.getApiVersion()).isEqualTo("admissionregistration.k8s.io/v1beta1");
        assertThat(policy.getKind()).isEqualTo("MutatingAdmissionPolicy");
        assertThat(policy.getSpec().getParamKind().getKind()).isEqualTo("DefaultLabels");
        assertThat(policy.getSpec().getMutations().get(0).getPatchType()).isEqualTo("ApplyConfiguration");
        assertThat(policy.getSpec().getMutations().get(0).getApplyConfiguration().getExpression())
                .contains("metadata");
        assertThat(binding.getKind()).isEqualTo("MutatingAdmissionPolicyBinding");
        assertThat(binding.getSpec().getParamRef().getParameterNotFoundAction()).isEqualTo("Allow");
        assertThat(policyList.getItems()).containsExactly(policy);
        assertThat(bindingList.getItems()).containsExactly(binding);
    }

    @Test
    void alphaMutatingAdmissionPolicyModelsJsonPatchAndBindingEdits() {
        io.fabric8.kubernetes.api.model.admissionregistration.v1alpha1.MutatingAdmissionPolicy alphaPolicy =
                new io.fabric8.kubernetes.api.model.admissionregistration.v1alpha1.MutatingAdmissionPolicyBuilder()
                .withNewMetadata()
                    .withName("alpha-image-policy")
                .endMetadata()
                .withNewSpec()
                    .withFailurePolicy("Fail")
                    .withReinvocationPolicy("IfNeeded")
                    .withNewParamKind("rules.example.test/v1alpha1", "ImagePolicy")
                    .withNewMatchConstraints()
                        .withMatchPolicy("Equivalent")
                        .addNewResourceRule()
                            .withOperations("CREATE")
                            .withApiGroups("")
                            .withApiVersions("v1")
                            .withResources("pods")
                            .withResourceNames("alpha-pod")
                            .withScope("Namespaced")
                        .endResourceRule()
                    .endMatchConstraints()
                    .addNewVariable()
                        .withName("image")
                        .withExpression("object.spec.containers[0].image")
                    .endVariable()
                    .addNewMutation()
                        .withPatchType("JSONPatch")
                        .withNewJsonPatch("JSONPatch{op: 'add', path: '/metadata/labels/checked', value: 'true'}")
                    .endMutation()
                .endSpec()
                .addToAdditionalProperties("stage", "alpha")
                .build();

        assertThat(alphaPolicy.getApiVersion()).isEqualTo("admissionregistration.k8s.io/v1alpha1");
        assertThat(alphaPolicy.getKind()).isEqualTo("MutatingAdmissionPolicy");
        assertThat(alphaPolicy.getSpec().getReinvocationPolicy()).isEqualTo("IfNeeded");
        assertThat(alphaPolicy.getSpec().getMutations().get(0).getJsonPatch().getExpression())
                .contains("/metadata/labels/checked");
        assertThat(alphaPolicy.getAdditionalProperties()).containsEntry("stage", "alpha");

        io.fabric8.kubernetes.api.model.admissionregistration.v1alpha1.MutatingAdmissionPolicy edited =
                alphaPolicy.toBuilder()
                .editSpec()
                    .editFirstMutation()
                        .withPatchType("ApplyConfiguration")
                        .withNewApplyConfiguration(
                                "Object{metadata: Object.metadata{annotations: {'checked': 'true'}}}")
                    .endMutation()
                .endSpec()
                .build();
        assertThat(edited.getSpec().getMutations().get(0).getPatchType()).isEqualTo("ApplyConfiguration");
        assertThat(edited.getSpec().getMutations().get(0).getApplyConfiguration().getExpression())
                .contains("annotations");
        assertThat(alphaPolicy.getSpec().getMutations().get(0).getPatchType()).isEqualTo("JSONPatch");

        io.fabric8.kubernetes.api.model.admissionregistration.v1alpha1.MutatingAdmissionPolicyBinding binding =
                new io.fabric8.kubernetes.api.model.admissionregistration.v1alpha1
                        .MutatingAdmissionPolicyBindingBuilder()
                .withNewMetadata()
                    .withName("alpha-image-policy-binding")
                .endMetadata()
                .withNewSpec()
                    .withPolicyName("alpha-image-policy")
                    .withMatchResources(alphaPodMatchResources("alpha-pod", "alpha"))
                    .withNewParamRef()
                        .withName("image-params")
                        .withNamespace("alpha")
                        .withParameterNotFoundAction("Deny")
                    .endParamRef()
                .endSpec()
                .build();
        io.fabric8.kubernetes.api.model.admissionregistration.v1alpha1.MutatingAdmissionPolicyBindingList bindingList =
                new io.fabric8.kubernetes.api.model.admissionregistration.v1alpha1
                        .MutatingAdmissionPolicyBindingListBuilder()
                .withNewMetadata("continue", 1L, "41", null)
                .addToItems(binding)
                .build();
        io.fabric8.kubernetes.api.model.admissionregistration.v1alpha1.MutatingAdmissionPolicyList policyList =
                new io.fabric8.kubernetes.api.model.admissionregistration.v1alpha1.MutatingAdmissionPolicyListBuilder()
                .withNewMetadata(null, 2L, "42", null)
                .withItems(alphaPolicy, edited)
                .build();

        assertThat(binding.getKind()).isEqualTo("MutatingAdmissionPolicyBinding");
        assertThat(binding.getSpec().getParamRef().getNamespace()).isEqualTo("alpha");
        assertThat(bindingList.getItems()).extracting(item -> item.getSpec().getPolicyName())
                .containsExactly("alpha-image-policy");
        assertThat(policyList.getItems()).extracting(item -> item.getSpec().getMutations().get(0).getPatchType())
                .containsExactly("JSONPatch", "ApplyConfiguration");
    }

    @Test
    void admissionReviewModelsRequestsResponsesRawObjectsAndWarnings() {
        AdmissionReview review = new AdmissionReviewBuilder()
                .withNewRequest()
                    .withUid("admission-request-1")
                    .withName("checkout")
                    .withNamespace("production")
                    .withOperation("CREATE")
                    .withDryRun(false)
                    .withNewKind("apps", "Deployment", "v1")
                    .withNewResource("apps", "deployments", "v1")
                    .withNewRequestKind("apps", "Deployment", "v1")
                    .withNewRequestResource("apps", "deployments", "v1")
                    .withObject(Map.of("metadata", Map.of("name", "checkout")))
                    .withOldObject(Map.of("metadata", Map.of("name", "checkout-old")))
                    .withOptions(Map.of("fieldManager", "fabric8-test"))
                    .withNewUserInfo()
                        .withUsername("system:serviceaccount:production:builder")
                        .withUid("user-1")
                        .withGroups("system:serviceaccounts", "system:authenticated")
                        .addToExtra("scopes", List.of("build", "deploy"))
                    .endUserInfo()
                .endRequest()
                .withNewResponse()
                    .withUid("admission-request-1")
                    .withAllowed(true)
                    .withPatch("W3sib3AiOiJhZGQiLCJwYXRoIjoiL21ldGFkYXRhL2xhYmVscy9jaGVja2VkIiwidmFsdWUiOiJ0cnVlIn1d")
                    .withPatchType("JSONPatch")
                    .withStatus(new StatusBuilder()
                            .withStatus("Success")
                            .withReason("Allowed")
                            .withMessage("request accepted")
                            .build())
                    .withWarnings("defaulted team label")
                .endResponse()
                .addToAdditionalProperties("audit-id", "admission-audit-1")
                .build();

        assertThat(review).isInstanceOf(KubernetesResource.class);
        assertThat(review.getApiVersion()).isEqualTo("admission.k8s.io/v1");
        assertThat(review.getKind()).isEqualTo("AdmissionReview");
        assertThat(review.getRequest().getResource().getResource()).isEqualTo("deployments");
        assertThat(review.getRequest().getKind().getKind()).isEqualTo("Deployment");
        assertThat(review.getRequest().getUserInfo().getGroups())
                .containsExactly("system:serviceaccounts", "system:authenticated");
        assertThat(review.getRequest().getUserInfo().getExtra()).containsEntry("scopes", List.of("build", "deploy"));
        assertThat(review.getResponse().getAllowed()).isTrue();
        assertThat(review.getResponse().getPatchType()).isEqualTo("JSONPatch");
        assertThat(review.getResponse().getStatus().getReason()).isEqualTo("Allowed");
        assertThat(review.getResponse().getWarnings()).containsExactly("defaulted team label");
        assertThat(review.getAdditionalProperties()).containsEntry("audit-id", "admission-audit-1");

        io.fabric8.kubernetes.api.model.admission.v1beta1.AdmissionReview betaReview =
                new io.fabric8.kubernetes.api.model.admission.v1beta1.AdmissionReviewBuilder()
                .withNewRequest()
                    .withUid("beta-request-1")
                    .withOperation("UPDATE")
                    .withName("checkout")
                    .withNamespace("staging")
                    .withNewKind("", "Pod", "v1")
                    .withNewResource("", "pods", "v1")
                    .withNewUserInfo()
                        .withUsername("developer")
                    .endUserInfo()
                .endRequest()
                .withNewResponse()
                    .withUid("beta-request-1")
                    .withAllowed(false)
                    .withStatus(new StatusBuilder()
                            .withStatus("Failure")
                            .withReason("Forbidden")
                            .withMessage("missing required label")
                            .build())
                    .withWarnings("beta admission path")
                .endResponse()
                .build();

        assertThat(betaReview.getApiVersion()).isEqualTo("admission.k8s.io/v1beta1");
        assertThat(betaReview.getKind()).isEqualTo("AdmissionReview");
        assertThat(betaReview.getRequest().getResource().getResource()).isEqualTo("pods");
        assertThat(betaReview.getResponse().getAllowed()).isFalse();
        assertThat(betaReview.toBuilder()
                .editResponse()
                    .withAllowed(true)
                .endResponse()
                .build()
                .getResponse().getAllowed()).isTrue();
    }

    @Test
    void serviceLoaderDiscoversAdmissionRegistrationAdmissionAndAccessReviewResources() {
        List<Class<?>> resourceTypes = new ArrayList<>();
        for (KubernetesResource resource : ServiceLoader.load(KubernetesResource.class)) {
            resourceTypes.add(resource.getClass());
        }

        assertThat(resourceTypes).contains(
                AdmissionReview.class,
                io.fabric8.kubernetes.api.model.admission.v1beta1.AdmissionReview.class,
                MutatingWebhookConfiguration.class,
                MutatingWebhookConfigurationList.class,
                ValidatingWebhookConfiguration.class,
                ValidatingWebhookConfigurationList.class,
                ValidatingAdmissionPolicy.class,
                ValidatingAdmissionPolicyList.class,
                ValidatingAdmissionPolicyBinding.class,
                ValidatingAdmissionPolicyBindingList.class,
                io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.MutatingAdmissionPolicy.class,
                io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.MutatingAdmissionPolicyList.class,
                io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.MutatingAdmissionPolicyBinding.class,
                io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.MutatingAdmissionPolicyBindingList.class,
                io.fabric8.kubernetes.api.model.admissionregistration.v1alpha1.MutatingAdmissionPolicy.class,
                io.fabric8.kubernetes.api.model.admissionregistration.v1alpha1.MutatingAdmissionPolicyList.class,
                io.fabric8.kubernetes.api.model.admissionregistration.v1alpha1.MutatingAdmissionPolicyBinding.class,
                io.fabric8.kubernetes.api.model.admissionregistration.v1alpha1.MutatingAdmissionPolicyBindingList.class,
                io.fabric8.kubernetes.api.model.authentication.TokenRequest.class,
                io.fabric8.kubernetes.api.model.authentication.TokenReview.class,
                io.fabric8.kubernetes.api.model.authentication.SelfSubjectReview.class,
                io.fabric8.kubernetes.api.model.authorization.v1.SubjectAccessReview.class,
                io.fabric8.kubernetes.api.model.authorization.v1.LocalSubjectAccessReview.class,
                io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReview.class,
                io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectRulesReview.class);
    }

    private static MutatingWebhookConfiguration mutatingConfiguration(String name, String resource, String pathSuffix) {
        return new MutatingWebhookConfigurationBuilder()
                .withNewMetadata()
                    .withName(name)
                .endMetadata()
                .addNewWebhook()
                    .withName(name + ".example.test")
                    .withAdmissionReviewVersions("v1")
                    .withFailurePolicy("Fail")
                    .withSideEffects("None")
                    .withTimeoutSeconds(3)
                    .withNewClientConfig()
                        .withNewService(name, "admission-system", "/" + pathSuffix, 443)
                    .endClientConfig()
                    .addNewRule()
                        .withOperations("CREATE")
                        .withApiGroups("")
                        .withApiVersions("v1")
                        .withResources(resource)
                    .endRule()
                .endWebhook()
                .build();
    }

    private static MatchResources podMatchResources(String podName, String namespace) {
        return new MatchResourcesBuilder()
                .withMatchPolicy("Equivalent")
                .withNewNamespaceSelector()
                    .addToMatchLabels("kubernetes.io/metadata.name", namespace)
                .endNamespaceSelector()
                .withNewObjectSelector()
                    .addToMatchLabels("validated", "true")
                .endObjectSelector()
                .addNewResourceRule()
                    .withOperations("CREATE", "UPDATE")
                    .withApiGroups("")
                    .withApiVersions("v1")
                    .withResources("pods")
                    .withResourceNames(podName)
                    .withScope("Namespaced")
                .endResourceRule()
                .addNewExcludeResourceRule()
                    .withOperations("DELETE")
                    .withApiGroups("")
                    .withApiVersions("v1")
                    .withResources("pods")
                    .withResourceNames(podName + "-skip")
                    .withScope("Namespaced")
                .endExcludeResourceRule()
                .build();
    }

    private static io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.MutatingAdmissionPolicy
            betaMutatingPolicy(String name, String patchType) {
        return new io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.MutatingAdmissionPolicyBuilder()
                .withNewMetadata()
                    .withName(name)
                .endMetadata()
                .withNewSpec()
                    .withFailurePolicy("Fail")
                    .withReinvocationPolicy("IfNeeded")
                    .withNewParamKind("rules.example.test/v1beta1", "DefaultLabels")
                    .withMatchConstraints(betaPodMatchResources("mutate", "staging"))
                    .addNewVariable()
                        .withName("labels")
                        .withExpression("object.metadata.labels")
                    .endVariable()
                    .addNewMatchCondition()
                        .withName("mutate-enabled")
                        .withExpression("has(object.metadata.labels['mutate'])")
                    .endMatchCondition()
                    .addNewMutation()
                        .withPatchType(patchType)
                        .withNewApplyConfiguration(
                                "Object{metadata: Object.metadata{labels: {'mutated-by': 'fabric8'}}}")
                    .endMutation()
                .endSpec()
                .build();
    }

    private static io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.MatchResources
            betaPodMatchResources(String podName, String namespace) {
        return new io.fabric8.kubernetes.api.model.admissionregistration.v1beta1.MatchResourcesBuilder()
                .withMatchPolicy("Equivalent")
                .withNewNamespaceSelector()
                    .addToMatchLabels("kubernetes.io/metadata.name", namespace)
                .endNamespaceSelector()
                .addNewResourceRule()
                    .withOperations("CREATE", "UPDATE")
                    .withApiGroups("")
                    .withApiVersions("v1")
                    .withResources("pods")
                    .withResourceNames(podName)
                    .withScope("Namespaced")
                .endResourceRule()
                .build();
    }

    private static io.fabric8.kubernetes.api.model.admissionregistration.v1alpha1.MatchResources
            alphaPodMatchResources(String podName, String namespace) {
        return new io.fabric8.kubernetes.api.model.admissionregistration.v1alpha1.MatchResourcesBuilder()
                .withMatchPolicy("Equivalent")
                .withNewNamespaceSelector()
                    .addToMatchLabels("kubernetes.io/metadata.name", namespace)
                .endNamespaceSelector()
                .addNewResourceRule()
                    .withOperations("CREATE")
                    .withApiGroups("")
                    .withApiVersions("v1")
                    .withResources("pods")
                    .withResourceNames(podName)
                    .withScope("Namespaced")
                .endResourceRule()
                .build();
    }
}
