/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_rbac;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingList;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingListBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleList;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleListBuilder;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingList;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingListBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleList;
import io.fabric8.kubernetes.api.model.rbac.RoleListBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleRef;
import io.fabric8.kubernetes.api.model.rbac.RoleRefBuilder;
import io.fabric8.kubernetes.api.model.rbac.Subject;
import io.fabric8.kubernetes.api.model.rbac.SubjectBuilder;
import org.junit.jupiter.api.Test;

public class Kubernetes_model_rbacTest {
    private static final String RBAC_API_GROUP = "rbac.authorization.k8s.io";
    private static final String RBAC_API_VERSION = RBAC_API_GROUP + "/v1";

    @Test
    void roleBuilderCreatesNamespacedPolicyRulesAndSupportsEditing() {
        Role role = new RoleBuilder()
                .withNewMetadata()
                .withName("pod-reader")
                .withNamespace("team-a")
                .addToLabels("app.kubernetes.io/name", "rbac-test")
                .endMetadata()
                .addNewRule()
                .addToApiGroups("")
                .addToResources("pods", "pods/log")
                .addToResourceNames("frontend")
                .addToVerbs("get", "list", "watch")
                .endRule()
                .addNewRule()
                .addToApiGroups("batch")
                .addToResources("jobs")
                .addToVerbs("create", "delete")
                .endRule()
                .build();

        Role edited = role.toBuilder()
                .editMetadata()
                .addToAnnotations("owner", "security")
                .endMetadata()
                .editFirstRule()
                .addToResourceNames("backend")
                .endRule()
                .build();

        assertThat(role.getApiVersion()).isEqualTo(RBAC_API_VERSION);
        assertThat(role.getKind()).isEqualTo("Role");
        assertThat(role.getMetadata().getNamespace()).isEqualTo("team-a");
        assertThat(role.getMetadata().getLabels()).containsEntry("app.kubernetes.io/name", "rbac-test");
        assertThat(role.getRules()).hasSize(2);
        assertThat(role.getRules().get(0).getApiGroups()).containsExactly("");
        assertThat(role.getRules().get(0).getResources()).containsExactly("pods", "pods/log");
        assertThat(role.getRules().get(0).getResourceNames()).containsExactly("frontend");
        assertThat(role.getRules().get(0).getVerbs()).containsExactly("get", "list", "watch");
        assertThat(role.getRules().get(1).getApiGroups()).containsExactly("batch");
        assertThat(role.getRules().get(1).getResources()).containsExactly("jobs");
        assertThat(role.getRules().get(1).getVerbs()).containsExactly("create", "delete");

        assertThat(edited.getMetadata().getAnnotations()).containsEntry("owner", "security");
        assertThat(edited.getRules().get(0).getResourceNames()).containsExactly("frontend", "backend");
        assertThat(role.getMetadata().getAnnotations()).isEmpty();
        assertThat(role.getRules().get(0).getResourceNames()).containsExactly("frontend");
    }

    @Test
    void clusterRoleBuilderCreatesAggregationRuleAndNonResourcePolicyRule() {
        PolicyRule healthRule = new PolicyRuleBuilder()
                .addToNonResourceURLs("/healthz", "/readyz")
                .addToVerbs("get")
                .build();

        ClusterRole clusterRole = new ClusterRoleBuilder()
                .withNewMetadata()
                .withName("aggregate-reader")
                .addToLabels("rbac.authorization.k8s.io/aggregate-to-view", "true")
                .endMetadata()
                .withNewAggregationRule()
                .addNewClusterRoleSelector()
                .addToMatchLabels("rbac.example/aggregate-to-reader", "true")
                .endClusterRoleSelector()
                .endAggregationRule()
                .addToRules(healthRule)
                .addNewRule()
                .addToApiGroups("apps")
                .addToResources("deployments", "replicasets")
                .addToVerbs("get", "list")
                .endRule()
                .build();

        assertThat(clusterRole.getApiVersion()).isEqualTo(RBAC_API_VERSION);
        assertThat(clusterRole.getKind()).isEqualTo("ClusterRole");
        assertThat(clusterRole.getMetadata().getName()).isEqualTo("aggregate-reader");
        assertThat(clusterRole.getAggregationRule().getClusterRoleSelectors()).hasSize(1);
        assertThat(clusterRole.getAggregationRule().getClusterRoleSelectors().get(0).getMatchLabels())
                .containsEntry("rbac.example/aggregate-to-reader", "true");
        assertThat(clusterRole.getRules()).hasSize(2);
        assertThat(clusterRole.getRules().get(0).getNonResourceURLs()).containsExactly("/healthz", "/readyz");
        assertThat(clusterRole.getRules().get(0).getVerbs()).containsExactly("get");
        assertThat(clusterRole.getRules().get(1).getApiGroups()).containsExactly("apps");
        assertThat(clusterRole.getRules().get(1).getResources()).containsExactly("deployments", "replicasets");
        assertThat(clusterRole.getRules().get(1).getVerbs()).containsExactly("get", "list");
    }

    @Test
    void roleBindingConnectsSubjectsToRoleReference() {
        Subject serviceAccount = new SubjectBuilder()
                .withKind("ServiceAccount")
                .withName("builder")
                .withNamespace("ci")
                .build();
        RoleRef roleRef = new RoleRefBuilder()
                .withApiGroup(RBAC_API_GROUP)
                .withKind("Role")
                .withName("pod-writer")
                .build();

        RoleBinding binding = new RoleBindingBuilder()
                .withNewMetadata()
                .withName("write-pods")
                .withNamespace("ci")
                .endMetadata()
                .withRoleRef(roleRef)
                .addToSubjects(serviceAccount)
                .addNewSubject()
                .withApiGroup(RBAC_API_GROUP)
                .withKind("Group")
                .withName("developers")
                .endSubject()
                .build();

        assertThat(binding.getApiVersion()).isEqualTo(RBAC_API_VERSION);
        assertThat(binding.getKind()).isEqualTo("RoleBinding");
        assertThat(binding.getMetadata().getNamespace()).isEqualTo("ci");
        assertThat(binding.getRoleRef().getApiGroup()).isEqualTo(RBAC_API_GROUP);
        assertThat(binding.getRoleRef().getKind()).isEqualTo("Role");
        assertThat(binding.getRoleRef().getName()).isEqualTo("pod-writer");
        assertThat(binding.getSubjects()).hasSize(2);
        assertThat(binding.getSubjects().get(0).getKind()).isEqualTo("ServiceAccount");
        assertThat(binding.getSubjects().get(0).getName()).isEqualTo("builder");
        assertThat(binding.getSubjects().get(0).getNamespace()).isEqualTo("ci");
        assertThat(binding.getSubjects().get(1).getApiGroup()).isEqualTo(RBAC_API_GROUP);
        assertThat(binding.getSubjects().get(1).getKind()).isEqualTo("Group");
        assertThat(binding.getSubjects().get(1).getName()).isEqualTo("developers");
    }

    @Test
    void clusterRoleBindingConnectsClusterSubjectsToClusterRoleReference() {
        ClusterRoleBinding binding = new ClusterRoleBindingBuilder()
                .withNewMetadata()
                .withName("cluster-read")
                .endMetadata()
                .withNewRoleRef(RBAC_API_GROUP, "ClusterRole", "aggregate-reader")
                .addNewSubject(null, "User", "alice", null)
                .addNewSubject(RBAC_API_GROUP, "Group", "platform-admins", null)
                .build();

        ClusterRoleBinding edited = binding.edit()
                .editMetadata()
                .addToAnnotations("purpose", "integration-test")
                .endMetadata()
                .build();

        assertThat(binding.getApiVersion()).isEqualTo(RBAC_API_VERSION);
        assertThat(binding.getKind()).isEqualTo("ClusterRoleBinding");
        assertThat(binding.getMetadata().getName()).isEqualTo("cluster-read");
        assertThat(binding.getRoleRef().getApiGroup()).isEqualTo(RBAC_API_GROUP);
        assertThat(binding.getRoleRef().getKind()).isEqualTo("ClusterRole");
        assertThat(binding.getRoleRef().getName()).isEqualTo("aggregate-reader");
        assertThat(binding.getSubjects()).hasSize(2);
        assertThat(binding.getSubjects().get(0).getKind()).isEqualTo("User");
        assertThat(binding.getSubjects().get(0).getName()).isEqualTo("alice");
        assertThat(binding.getSubjects().get(0).getNamespace()).isNull();
        assertThat(binding.getSubjects().get(1).getApiGroup()).isEqualTo(RBAC_API_GROUP);
        assertThat(binding.getSubjects().get(1).getKind()).isEqualTo("Group");
        assertThat(binding.getSubjects().get(1).getName()).isEqualTo("platform-admins");
        assertThat(edited.getMetadata().getAnnotations()).containsEntry("purpose", "integration-test");
        assertThat(binding.getMetadata().getAnnotations()).isEmpty();
    }

    @Test
    void matchingPredicatesSelectEditAndRemoveRulesAndSubjects() {
        RoleBuilder roleBuilder = new RoleBuilder()
                .withNewMetadata()
                .withName("curated-access")
                .withNamespace("team-b")
                .endMetadata()
                .addNewRule()
                .addToApiGroups("")
                .addToResources("pods")
                .addToVerbs("get")
                .endRule()
                .addNewRule()
                .addToApiGroups("")
                .addToResources("secrets")
                .addToVerbs("get")
                .endRule()
                .addNewRule()
                .addToApiGroups("apps")
                .addToResources("deployments")
                .addToVerbs("watch")
                .endRule();

        assertThat(roleBuilder.hasMatchingRule(rule -> rule.hasMatchingResource("pods"::equals))).isTrue();
        assertThat(roleBuilder.buildMatchingRule(rule -> rule.hasMatchingResource("pods"::equals)).getVerbs())
                .containsExactly("get");

        Role role = roleBuilder
                .editMatchingRule(rule -> rule.hasMatchingResource("pods"::equals))
                .addToVerbs("list")
                .endRule()
                .removeMatchingFromRules(rule -> rule.hasMatchingResource("secrets"::equals))
                .build();

        RoleBindingBuilder bindingBuilder = new RoleBindingBuilder()
                .withNewMetadata()
                .withName("curated-access-binding")
                .withNamespace("team-b")
                .endMetadata()
                .editOrNewRoleRef()
                .withApiGroup(RBAC_API_GROUP)
                .withKind("Role")
                .withName("curated-access")
                .endRoleRef()
                .addNewSubject(null, "ServiceAccount", "builder", "team-b")
                .addNewSubject(RBAC_API_GROUP, "Group", "temporary-reviewers", null)
                .addNewSubject(null, "User", "carol", null);

        assertThat(bindingBuilder.hasMatchingSubject(subject -> "Group".equals(subject.getKind()))).isTrue();
        assertThat(bindingBuilder.buildMatchingSubject(subject -> "Group".equals(subject.getKind())).getName())
                .isEqualTo("temporary-reviewers");

        RoleBinding binding = bindingBuilder
                .editMatchingSubject(subject -> "ServiceAccount".equals(subject.getKind()))
                .withName("deployer")
                .endSubject()
                .removeMatchingFromSubjects(subject -> "temporary-reviewers".equals(subject.getName()))
                .build();

        assertThat(role.getRules()).hasSize(2);
        assertThat(role.getRules()).extracting(rule -> rule.getResources().get(0))
                .containsExactly("pods", "deployments");
        assertThat(role.getRules().get(0).getVerbs()).containsExactly("get", "list");
        assertThat(role.getRules().get(1).getVerbs()).containsExactly("watch");

        assertThat(binding.getRoleRef().getName()).isEqualTo("curated-access");
        assertThat(binding.getSubjects()).hasSize(2);
        assertThat(binding.getSubjects()).extracting(Subject::getKind)
                .containsExactly("ServiceAccount", "User");
        assertThat(binding.getSubjects().get(0).getName()).isEqualTo("deployer");
        assertThat(binding.getSubjects().get(0).getNamespace()).isEqualTo("team-b");
        assertThat(binding.getSubjects().get(1).getName()).isEqualTo("carol");
    }

    @Test
    void listResourcesPreserveMetadataItemsAndAdditionalProperties() {
        Role readOnlyRole = new RoleBuilder()
                .withNewMetadata()
                .withName("read-only")
                .withNamespace("team-a")
                .endMetadata()
                .addNewRule()
                .addToApiGroups("")
                .addToResources("configmaps")
                .addToVerbs("get", "list")
                .endRule()
                .build();
        Role writeRole = new RoleBuilder(readOnlyRole)
                .editMetadata()
                .withName("write")
                .endMetadata()
                .editFirstRule()
                .withVerbs("create", "update", "patch")
                .endRule()
                .build();
        ClusterRole clusterRole = new ClusterRoleBuilder()
                .withNewMetadata()
                .withName("cluster-view")
                .endMetadata()
                .addNewRule()
                .addToApiGroups("")
                .addToResources("nodes")
                .addToVerbs("get", "list")
                .endRule()
                .build();
        RoleBinding roleBinding = new RoleBindingBuilder()
                .withNewMetadata()
                .withName("read-only-binding")
                .withNamespace("team-a")
                .endMetadata()
                .withNewRoleRef(RBAC_API_GROUP, "Role", "read-only")
                .addNewSubject(null, "ServiceAccount", "reader", "team-a")
                .build();
        ClusterRoleBinding clusterRoleBinding = new ClusterRoleBindingBuilder()
                .withNewMetadata()
                .withName("cluster-view-binding")
                .endMetadata()
                .withNewRoleRef(RBAC_API_GROUP, "ClusterRole", "cluster-view")
                .addNewSubject(null, "User", "bob", null)
                .build();

        readOnlyRole.setAdditionalProperty("x-test", "role-value");
        RoleList roleList = new RoleListBuilder()
                .withNewMetadata(null, null, "42", null)
                .addToItems(readOnlyRole, writeRole)
                .build();
        ClusterRoleList clusterRoleList = new ClusterRoleListBuilder()
                .withNewMetadata("next", null, null, null)
                .addToItems(clusterRole)
                .build();
        RoleBindingList roleBindingList = new RoleBindingListBuilder()
                .withNewMetadata(null, null, "43", null)
                .addToItems(roleBinding)
                .build();
        ClusterRoleBindingList clusterRoleBindingList = new ClusterRoleBindingListBuilder()
                .withNewMetadata(null, null, "44", null)
                .addToItems(clusterRoleBinding)
                .build();

        assertThat(roleList.getApiVersion()).isEqualTo(RBAC_API_VERSION);
        assertThat(roleList.getKind()).isEqualTo("RoleList");
        assertThat(roleList.getMetadata().getResourceVersion()).isEqualTo("42");
        assertThat(roleList.getItems()).extracting(role -> role.getMetadata().getName())
                .containsExactly("read-only", "write");
        assertThat(roleList.getItems().get(0).getAdditionalProperties()).containsEntry("x-test", "role-value");
        assertThat(roleList.getItems().get(1).getRules().get(0).getVerbs())
                .containsExactly("create", "update", "patch");

        assertThat(clusterRoleList.getApiVersion()).isEqualTo(RBAC_API_VERSION);
        assertThat(clusterRoleList.getKind()).isEqualTo("ClusterRoleList");
        assertThat(clusterRoleList.getMetadata().getContinue()).isEqualTo("next");
        assertThat(clusterRoleList.getItems()).extracting(item -> item.getMetadata().getName())
                .containsExactly("cluster-view");

        assertThat(roleBindingList.getApiVersion()).isEqualTo(RBAC_API_VERSION);
        assertThat(roleBindingList.getKind()).isEqualTo("RoleBindingList");
        assertThat(roleBindingList.getItems()).extracting(item -> item.getRoleRef().getName())
                .containsExactly("read-only");

        assertThat(clusterRoleBindingList.getApiVersion()).isEqualTo(RBAC_API_VERSION);
        assertThat(clusterRoleBindingList.getKind()).isEqualTo("ClusterRoleBindingList");
        assertThat(clusterRoleBindingList.getItems()).extracting(item -> item.getRoleRef().getName())
                .containsExactly("cluster-view");
    }
}
