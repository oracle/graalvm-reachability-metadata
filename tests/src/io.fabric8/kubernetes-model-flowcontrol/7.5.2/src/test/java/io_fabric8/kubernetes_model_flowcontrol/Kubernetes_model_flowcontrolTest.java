/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_flowcontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ListMetaBuilder;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.FlowSchema;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.FlowSchemaBuilder;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.FlowSchemaList;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.FlowSchemaListBuilder;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.PriorityLevelConfiguration;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.PriorityLevelConfigurationBuilder;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.PriorityLevelConfigurationList;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.PriorityLevelConfigurationListBuilder;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.ResourcePolicyRule;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.ResourcePolicyRuleBuilder;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.Subject;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Kubernetes_model_flowcontrolTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void buildsEditsAndRoundTripsStableFlowSchema() throws Exception {
        FlowSchema schema = new FlowSchemaBuilder()
                .withNewMetadata()
                    .withName("workload-high")
                    .addToLabels("tier", "critical")
                .endMetadata()
                .withNewSpec()
                    .withMatchingPrecedence(100)
                    .withNewDistinguisherMethod("ByUser")
                    .withNewPriorityLevelConfiguration("workload-high")
                    .addNewRule()
                        .addNewSubject()
                            .withKind("User")
                            .withNewUser("alice")
                        .endSubject()
                        .addNewSubject()
                            .withKind("Group")
                            .withNewGroup("system:authenticated")
                        .endSubject()
                        .addNewSubject()
                            .withKind("ServiceAccount")
                            .withNewServiceAccount("ci", "build-bot")
                        .endSubject()
                        .addNewResourceRule()
                            .withVerbs("get", "list", "watch")
                            .withApiGroups("")
                            .withResources("pods", "pods/status")
                            .withNamespaces("team-a")
                        .endResourceRule()
                        .addNewNonResourceRule()
                            .withVerbs("get")
                            .withNonResourceURLs("/healthz", "/readyz")
                        .endNonResourceRule()
                    .endRule()
                    .addToAdditionalProperties("x-fabric8", Map.of("owner", "platform"))
                .endSpec()
                .withNewStatus()
                    .addNewCondition("2024-01-01T00:00:00Z", "schema accepted", "Fresh", "True", "Dangling")
                .endStatus()
                .build();

        assertThat(schema.getApiVersion()).isEqualTo("flowcontrol.apiserver.k8s.io/v1");
        assertThat(schema.getKind()).isEqualTo("FlowSchema");
        assertThat(schema.getMetadata().getLabels()).containsEntry("tier", "critical");
        assertThat(schema.getSpec().getDistinguisherMethod().getType()).isEqualTo("ByUser");
        assertThat(schema.getSpec().getPriorityLevelConfiguration().getName()).isEqualTo("workload-high");
        assertThat(schema.getSpec().getRules()).hasSize(1);
        assertThat(schema.getSpec().getRules().get(0).getSubjects()).extracting(Subject::getKind)
                .containsExactly("User", "Group", "ServiceAccount");
        assertThat(schema.getSpec().getRules().get(0).getResourceRules().get(0).getResources())
                .containsExactly("pods", "pods/status");
        assertThat(schema.getStatus().getConditions().get(0).getType()).isEqualTo("Dangling");

        FlowSchema edited = schema.edit()
                .editMetadata()
                    .addToAnnotations("flowcontrol.fabric8.io/managed", "true")
                .endMetadata()
                .editSpec()
                    .withMatchingPrecedence(80)
                    .editFirstRule()
                        .editMatchingResourceRule(rule -> rule.hasMatchingResource("pods/status"::equals))
                            .addToResources("deployments")
                            .removeFromVerbs("watch")
                        .endResourceRule()
                        .addNewNonResourceRule()
                            .withVerbs("post")
                            .withNonResourceURLs("/metrics")
                        .endNonResourceRule()
                    .endRule()
                .endSpec()
                .build();

        assertThat(schema.getMetadata().getAnnotations()).isEmpty();
        assertThat(edited.getMetadata().getAnnotations()).containsEntry("flowcontrol.fabric8.io/managed", "true");
        assertThat(edited.getSpec().getMatchingPrecedence()).isEqualTo(80);
        assertThat(edited.getSpec().getRules().get(0).getResourceRules().get(0).getVerbs())
                .containsExactly("get", "list");
        assertThat(edited.getSpec().getRules().get(0).getNonResourceRules()).hasSize(2);
        assertThat(edited.toBuilder().build()).isEqualTo(edited);
        assertThat(edited.toBuilder().build().hashCode()).isEqualTo(edited.hashCode());

        String json = MAPPER.writeValueAsString(edited);
        FlowSchema roundTripped = MAPPER.readValue(json, FlowSchema.class);

        assertThat(roundTripped).isEqualTo(edited);
        assertThat(roundTripped.getSpec().getAdditionalProperties()).containsKey("x-fabric8");
        assertThat(roundTripped.getSpec().getRules().get(0).getResourceRules().get(0).getResources())
                .contains("deployments");
    }

    @Test
    void buildsEditsAndRoundTripsStablePriorityLevelConfiguration() throws Exception {
        PriorityLevelConfiguration configuration = new PriorityLevelConfigurationBuilder()
                .withNewMetadata()
                    .withName("workload-high")
                    .addToLabels("mode", "limited")
                .endMetadata()
                .withNewSpec()
                    .withType("Limited")
                    .withNewLimited()
                        .withNominalConcurrencyShares(30)
                        .withLendablePercent(25)
                        .withBorrowingLimitPercent(50)
                        .withNewLimitResponse()
                            .withType("Queue")
                            .withNewQueuing(8, 128, 32)
                        .endLimitResponse()
                    .endLimited()
                    .addToAdditionalProperties("policy", "bounded")
                .endSpec()
                .withNewStatus()
                    .addNewCondition("2024-01-01T00:00:00Z", "configured", "AsExpected", "True", "ConcurrencyLimited")
                .endStatus()
                .build();

        assertThat(configuration.getApiVersion()).isEqualTo("flowcontrol.apiserver.k8s.io/v1");
        assertThat(configuration.getKind()).isEqualTo("PriorityLevelConfiguration");
        assertThat(configuration.getSpec().getType()).isEqualTo("Limited");
        assertThat(configuration.getSpec().getLimited().getNominalConcurrencyShares()).isEqualTo(30);
        assertThat(configuration.getSpec().getLimited().getLimitResponse().getType()).isEqualTo("Queue");
        assertThat(configuration.getSpec().getLimited().getLimitResponse().getQueuing().getHandSize()).isEqualTo(8);
        assertThat(configuration.getSpec().getLimited().getLimitResponse().getQueuing().getQueues()).isEqualTo(32);
        assertThat(configuration.getStatus().getConditions().get(0).getType()).isEqualTo("ConcurrencyLimited");

        PriorityLevelConfiguration edited = configuration.edit()
                .editSpec()
                    .editLimited()
                        .withNominalConcurrencyShares(45)
                        .editLimitResponse()
                            .editQueuing()
                                .withQueueLengthLimit(256)
                            .endQueuing()
                        .endLimitResponse()
                    .endLimited()
                .endSpec()
                .build();

        assertThat(configuration.getSpec().getLimited().getNominalConcurrencyShares()).isEqualTo(30);
        assertThat(edited.getSpec().getLimited().getNominalConcurrencyShares()).isEqualTo(45);
        assertThat(edited.getSpec().getLimited().getLimitResponse().getQueuing().getQueueLengthLimit())
                .isEqualTo(256);

        String json = MAPPER.writeValueAsString(edited);
        PriorityLevelConfiguration roundTripped = MAPPER.readValue(json, PriorityLevelConfiguration.class);

        assertThat(roundTripped).isEqualTo(edited);
        assertThat(roundTripped.getSpec().getAdditionalProperties()).containsEntry("policy", "bounded");
    }

    @Test
    void buildsListsAndUsesPredicateBasedEdits() throws Exception {
        ResourcePolicyRule podRule = new ResourcePolicyRuleBuilder()
                .withVerbs("get", "list")
                .withApiGroups("")
                .withResources("pods")
                .withNamespaces("team-a")
                .build();
        ResourcePolicyRule deploymentRule = new ResourcePolicyRuleBuilder()
                .withVerbs("create")
                .withApiGroups("apps")
                .withResources("deployments")
                .withNamespaces("team-b")
                .build();
        FlowSchema low = new FlowSchemaBuilder()
                .withNewMetadata()
                    .withName("workload-low")
                .endMetadata()
                .withNewSpec()
                    .withMatchingPrecedence(900)
                    .withNewPriorityLevelConfiguration("workload-low")
                    .addNewRule()
                        .addNewSubject()
                            .withKind("Group")
                            .withNewGroup("system:serviceaccounts")
                        .endSubject()
                        .withResourceRules(podRule)
                    .endRule()
                .endSpec()
                .build();
        FlowSchema high = new FlowSchemaBuilder(low)
                .editMetadata()
                    .withName("workload-high")
                .endMetadata()
                .editSpec()
                    .withMatchingPrecedence(100)
                    .editFirstRule()
                        .withResourceRules(deploymentRule)
                    .endRule()
                .endSpec()
                .build();

        FlowSchemaList list = new FlowSchemaListBuilder()
                .withMetadata(new ListMetaBuilder().withResourceVersion("rv-1").build())
                .withItems(low, high)
                .build();

        assertThat(list.getApiVersion()).isEqualTo("flowcontrol.apiserver.k8s.io/v1");
        assertThat(list.getKind()).isEqualTo("FlowSchemaList");
        assertThat(list.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("workload-low", "workload-high");

        FlowSchemaList edited = new FlowSchemaListBuilder(list)
                .editMatchingItem(item -> "workload-high".equals(item.buildMetadata().getName()))
                    .editSpec()
                        .withMatchingPrecedence(50)
                    .endSpec()
                .endItem()
                .removeMatchingFromItems(item -> "workload-low".equals(item.buildMetadata().getName()))
                .addNewItemLike(low)
                    .editMetadata()
                        .withName("workload-background")
                    .endMetadata()
                    .editSpec()
                        .withMatchingPrecedence(1000)
                    .endSpec()
                .endItem()
                .build();

        assertThat(edited.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("workload-high", "workload-background");
        assertThat(edited.getItems()).extracting(item -> item.getSpec().getMatchingPrecedence())
                .containsExactly(50, 1000);

        PriorityLevelConfigurationList configurations = new PriorityLevelConfigurationListBuilder()
                .withMetadata(new ListMetaBuilder().withContinue("next").build())
                .addNewItem()
                    .withNewMetadata()
                        .withName("workload-high")
                    .endMetadata()
                    .withNewSpec()
                        .withType("Exempt")
                        .withNewExempt(100, 100)
                    .endSpec()
                .endItem()
                .addNewItem()
                    .withNewMetadata()
                        .withName("workload-low")
                    .endMetadata()
                    .withNewSpec()
                        .withType("Limited")
                        .withNewLimited()
                            .withNominalConcurrencyShares(5)
                            .withNewLimitResponse()
                                .withType("Reject")
                            .endLimitResponse()
                        .endLimited()
                    .endSpec()
                .endItem()
                .build();

        assertThat(configurations.getKind()).isEqualTo("PriorityLevelConfigurationList");
        assertThat(configurations.getMetadata().getContinue()).isEqualTo("next");
        assertThat(configurations.getItems()).extracting(item -> item.getSpec().getType())
                .containsExactly("Exempt", "Limited");

        String json = MAPPER.writeValueAsString(configurations);
        PriorityLevelConfigurationList roundTripped = MAPPER.readValue(json, PriorityLevelConfigurationList.class);

        assertThat(roundTripped).isEqualTo(configurations);
        assertThat(roundTripped.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("workload-high", "workload-low");
    }

    @Test
    void buildsAndRoundTripsBetaFlowSchemasAndPriorityLevels() throws Exception {
        io.fabric8.kubernetes.api.model.flowcontrol.v1beta1.FlowSchema beta1Schema =
                new io.fabric8.kubernetes.api.model.flowcontrol.v1beta1.FlowSchemaBuilder()
                        .withNewMetadata()
                            .withName("beta1-schema")
                        .endMetadata()
                        .withNewSpec()
                            .withMatchingPrecedence(300)
                            .withNewDistinguisherMethod("ByNamespace")
                            .withNewPriorityLevelConfiguration("beta1-limited")
                            .addNewRule()
                                .addNewSubject()
                                    .withKind("User")
                                    .withNewUser("bob")
                                .endSubject()
                                .addNewResourceRule()
                                    .withVerbs("update")
                                    .withApiGroups("apps")
                                    .withResources("deployments")
                                    .withClusterScope(false)
                                    .withNamespaces("team-beta")
                                .endResourceRule()
                            .endRule()
                        .endSpec()
                        .build();

        assertThat(beta1Schema.getApiVersion()).isEqualTo("flowcontrol.apiserver.k8s.io/v1beta1");
        assertThat(beta1Schema.getSpec().getDistinguisherMethod().getType()).isEqualTo("ByNamespace");
        assertThat(beta1Schema.getSpec().getRules().get(0).getSubjects().get(0).getUser().getName())
                .isEqualTo("bob");

        String beta1Json = MAPPER.writeValueAsString(beta1Schema);
        io.fabric8.kubernetes.api.model.flowcontrol.v1beta1.FlowSchema beta1RoundTripped = MAPPER.readValue(
                beta1Json, io.fabric8.kubernetes.api.model.flowcontrol.v1beta1.FlowSchema.class);
        assertThat(beta1RoundTripped).isEqualTo(beta1Schema);

        io.fabric8.kubernetes.api.model.flowcontrol.v1beta2.PriorityLevelConfiguration beta2Configuration =
                new io.fabric8.kubernetes.api.model.flowcontrol.v1beta2.PriorityLevelConfigurationBuilder()
                        .withNewMetadata()
                            .withName("beta2-priority")
                        .endMetadata()
                        .withNewSpec()
                            .withType("Limited")
                            .withNewLimited()
                                .withAssuredConcurrencyShares(12)
                                .withNewLimitResponse()
                                    .withType("Queue")
                                    .withNewQueuing(4, 64, 16)
                                .endLimitResponse()
                            .endLimited()
                        .endSpec()
                        .build();

        assertThat(beta2Configuration.getApiVersion()).isEqualTo("flowcontrol.apiserver.k8s.io/v1beta2");
        assertThat(beta2Configuration.getSpec().getLimited().getAssuredConcurrencyShares()).isEqualTo(12);
        assertThat(beta2Configuration.getSpec().getLimited().getLimitResponse().getQueuing().getQueues())
                .isEqualTo(16);

        String beta2Json = MAPPER.writeValueAsString(beta2Configuration);
        io.fabric8.kubernetes.api.model.flowcontrol.v1beta2.PriorityLevelConfiguration beta2RoundTripped =
                MAPPER.readValue(
                        beta2Json,
                        io.fabric8.kubernetes.api.model.flowcontrol.v1beta2.PriorityLevelConfiguration.class);
        assertThat(beta2RoundTripped).isEqualTo(beta2Configuration);
    }

    @Test
    void buildsAndEditsBeta3Resources() throws Exception {
        io.fabric8.kubernetes.api.model.flowcontrol.v1beta3.FlowSchema schema =
                new io.fabric8.kubernetes.api.model.flowcontrol.v1beta3.FlowSchemaBuilder()
                        .withNewMetadata()
                            .withName("beta3-schema")
                        .endMetadata()
                        .withNewSpec()
                            .withMatchingPrecedence(200)
                            .withNewDistinguisherMethod("ByUser")
                            .withNewPriorityLevelConfiguration("beta3-priority")
                            .addNewRule()
                                .addNewSubject()
                                    .withKind("ServiceAccount")
                                    .withNewServiceAccount("reconciler", "payments")
                                .endSubject()
                                .addNewNonResourceRule()
                                    .withVerbs("get")
                                    .withNonResourceURLs("/version")
                                .endNonResourceRule()
                            .endRule()
                        .endSpec()
                        .build();

        io.fabric8.kubernetes.api.model.flowcontrol.v1beta3.FlowSchema editedSchema = schema.edit()
                .editSpec()
                    .editFirstRule()
                        .addNewResourceRule()
                            .withVerbs("patch")
                            .withApiGroups("batch")
                            .withResources("jobs")
                            .withNamespaces("payments")
                        .endResourceRule()
                    .endRule()
                .endSpec()
                .build();

        assertThat(editedSchema.getApiVersion()).isEqualTo("flowcontrol.apiserver.k8s.io/v1beta3");
        assertThat(editedSchema.getSpec().getRules().get(0).getSubjects().get(0).getServiceAccount().getName())
                .isEqualTo("reconciler");
        assertThat(editedSchema.getSpec().getRules().get(0).getResourceRules().get(0).getResources())
                .containsExactly("jobs");

        io.fabric8.kubernetes.api.model.flowcontrol.v1beta3.PriorityLevelConfiguration configuration =
                new io.fabric8.kubernetes.api.model.flowcontrol.v1beta3.PriorityLevelConfigurationBuilder()
                        .withNewMetadata()
                            .withName("beta3-priority")
                        .endMetadata()
                        .withNewSpec()
                            .withType("Limited")
                            .withNewLimited()
                                .withNominalConcurrencyShares(20)
                                .withLendablePercent(10)
                                .withBorrowingLimitPercent(30)
                                .withNewLimitResponse()
                                    .withType("Reject")
                                .endLimitResponse()
                            .endLimited()
                        .endSpec()
                        .build();

        assertThat(configuration.getSpec().getLimited().getNominalConcurrencyShares()).isEqualTo(20);
        assertThat(configuration.getSpec().getLimited().getLimitResponse().getType()).isEqualTo("Reject");

        String schemaJson = MAPPER.writeValueAsString(editedSchema);
        io.fabric8.kubernetes.api.model.flowcontrol.v1beta3.FlowSchema roundTrippedSchema = MAPPER.readValue(
                schemaJson, io.fabric8.kubernetes.api.model.flowcontrol.v1beta3.FlowSchema.class);
        assertThat(roundTrippedSchema).isEqualTo(editedSchema);

        String configurationJson = MAPPER.writeValueAsString(configuration);
        io.fabric8.kubernetes.api.model.flowcontrol.v1beta3.PriorityLevelConfiguration roundTrippedConfiguration =
                MAPPER.readValue(
                        configurationJson,
                        io.fabric8.kubernetes.api.model.flowcontrol.v1beta3.PriorityLevelConfiguration.class);
        assertThat(roundTrippedConfiguration).isEqualTo(configuration);
    }
}
