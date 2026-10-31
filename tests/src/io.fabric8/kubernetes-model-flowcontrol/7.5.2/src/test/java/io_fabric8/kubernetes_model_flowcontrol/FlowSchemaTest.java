/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_flowcontrol;

import io.fabric8.kubernetes.api.model.flowcontrol.v1.FlowSchema;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.FlowSchemaBuilder;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.FlowSchemaList;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.FlowSchemaListBuilder;
import io.fabric8.kubernetes.api.model.flowcontrol.v1.PolicyRulesWithSubjects;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FlowSchemaTest {
    @Test
    void buildsRequestMatchingRulesForUsersGroupsAndServiceAccounts() {
        FlowSchema schema = new FlowSchemaBuilder()
                .withNewMetadata().withName("workload-reads").endMetadata()
                .withNewSpec()
                    .withMatchingPrecedence(500)
                    .withNewDistinguisherMethod("ByUser")
                    .withNewPriorityLevelConfiguration("workloads")
                    .addNewRule()
                        .addNewSubject()
                            .withKind("User").withNewUser("alice")
                        .endSubject()
                        .addNewSubject()
                            .withKind("Group").withNewGroup("developers")
                        .endSubject()
                        .addNewSubject()
                            .withKind("ServiceAccount")
                            .withNewServiceAccount().withName("reader").withNamespace("apps").endServiceAccount()
                        .endSubject()
                        .addNewResourceRule()
                            .withApiGroups("")
                            .withResources("pods", "services")
                            .withVerbs("get", "list", "watch")
                            .withNamespaces("apps")
                            .withClusterScope(false)
                        .endResourceRule()
                        .addNewNonResourceRule()
                            .withVerbs("get").withNonResourceURLs("/healthz", "/readyz")
                        .endNonResourceRule()
                    .endRule()
                .endSpec()
                .withNewStatus()
                    .addNewCondition()
                        .withType("Dangling").withStatus("False")
                        .withReason("FoundPriorityLevel").withMessage("Priority level exists")
                    .endCondition()
                .endStatus()
                .addToAdditionalProperties("managed-by", "platform")
                .build();

        PolicyRulesWithSubjects rule = schema.getSpec().getRules().get(0);
        assertThat(schema.getApiVersion()).isEqualTo("flowcontrol.apiserver.k8s.io/v1");
        assertThat(schema.getKind()).isEqualTo("FlowSchema");
        assertThat(schema.getFullResourceName()).isEqualTo("flowschemas.flowcontrol.apiserver.k8s.io");
        assertThat(schema.getSpec().getMatchingPrecedence()).isEqualTo(500);
        assertThat(schema.getSpec().getDistinguisherMethod().getType()).isEqualTo("ByUser");
        assertThat(schema.getSpec().getPriorityLevelConfiguration().getName()).isEqualTo("workloads");
        assertThat(rule.getSubjects()).hasSize(3);
        assertThat(rule.getSubjects().get(0).getUser().getName()).isEqualTo("alice");
        assertThat(rule.getSubjects().get(1).getGroup().getName()).isEqualTo("developers");
        assertThat(rule.getSubjects().get(2).getServiceAccount().getName()).isEqualTo("reader");
        assertThat(rule.getSubjects().get(2).getServiceAccount().getNamespace()).isEqualTo("apps");
        assertThat(rule.getResourceRules().get(0).getResources()).containsExactly("pods", "services");
        assertThat(rule.getResourceRules().get(0).getVerbs()).containsExactly("get", "list", "watch");
        assertThat(rule.getResourceRules().get(0).getNamespaces()).containsExactly("apps");
        assertThat(rule.getResourceRules().get(0).getClusterScope()).isFalse();
        assertThat(rule.getNonResourceRules().get(0).getNonResourceURLs()).containsExactly("/healthz", "/readyz");
        assertThat(schema.getStatus().getConditions().get(0).getStatus()).isEqualTo("False");
        assertThat(schema.getAdditionalProperties()).containsEntry("managed-by", "platform");
    }

    @Test
    void editsNestedSchemaWithoutChangingOriginalAndBuildsPaginatedList() {
        FlowSchema original = new FlowSchemaBuilder()
                .withNewMetadata().withName("batch").addToLabels("team", "data").endMetadata()
                .withNewSpec()
                    .withMatchingPrecedence(900)
                    .withNewPriorityLevelConfiguration("batch")
                .endSpec()
                .build();
        FlowSchema edited = original.edit()
                .editMetadata().addToLabels("environment", "production").endMetadata()
                .editSpec().withMatchingPrecedence(700).endSpec()
                .build();
        FlowSchemaList list = new FlowSchemaListBuilder()
                .withNewMetadata("next-page", null, "42", null)
                .addToItems(original, edited)
                .build();
        FlowSchemaList filtered = list.toBuilder()
                .removeMatchingFromItems(item -> item.buildSpec().getMatchingPrecedence() == 900)
                .build();

        assertThat(original.getSpec().getMatchingPrecedence()).isEqualTo(900);
        assertThat(original.getMetadata().getLabels()).doesNotContainKey("environment");
        assertThat(edited.getSpec().getMatchingPrecedence()).isEqualTo(700);
        assertThat(edited.getMetadata().getLabels()).containsEntry("team", "data")
                .containsEntry("environment", "production");
        assertThat(list.getItems()).containsExactly(original, edited);
        assertThat(filtered.getItems()).containsExactly(edited);
        assertThat(filtered.getMetadata().getResourceVersion()).isEqualTo("42");
        assertThat(filtered.getMetadata().getContinue()).isEqualTo("next-page");
        assertThat(filtered.getKind()).isEqualTo("FlowSchemaList");
    }
}
