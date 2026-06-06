/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_coordination;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ListMeta;
import io.fabric8.kubernetes.api.model.MicroTime;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseList;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseListBuilder;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseSpec;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseSpecBuilder;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class Kubernetes_model_coordinationTest {
    private static final String COORDINATION_API_GROUP = "coordination.k8s.io";

    @Test
    void leaseBuilderCreatesStableLeaseWithSpecTimestampsAndExtensions() {
        ZonedDateTime acquiredAt = ZonedDateTime.of(2026, 1, 1, 10, 15, 30, 0, ZoneOffset.UTC);
        ZonedDateTime renewedAt = ZonedDateTime.of(2026, 1, 1, 10, 15, 45, 0, ZoneOffset.UTC);

        Lease lease = new LeaseBuilder()
                .withNewMetadata()
                    .withName("controller-manager")
                    .withNamespace("kube-system")
                    .addToLabels("control-plane", "controller-manager")
                    .addToAnnotations("coordination.fabric8.io/source", "integration-test")
                .endMetadata()
                .withNewSpec()
                    .withHolderIdentity("controller-manager-0")
                    .withLeaseDurationSeconds(30)
                    .withAcquireTime(acquiredAt)
                    .withRenewTime(renewedAt)
                    .withLeaseTransitions(2)
                    .withStrategy("Coordinated")
                    .withPreferredHolder("controller-manager-1")
                    .addToAdditionalProperties("election", "primary")
                .endSpec()
                .addToAdditionalProperties("trace-id", "lease-1")
                .build();

        assertThat(lease).isInstanceOf(HasMetadata.class);
        assertThat(lease.getApiVersion()).isEqualTo(COORDINATION_API_GROUP + "/v1");
        assertThat(lease.getKind()).isEqualTo("Lease");
        assertThat(lease.getMetadata().getName()).isEqualTo("controller-manager");
        assertThat(lease.getMetadata().getNamespace()).isEqualTo("kube-system");
        assertThat(lease.getMetadata().getLabels()).containsEntry("control-plane", "controller-manager");
        assertThat(lease.getSpec().getHolderIdentity()).isEqualTo("controller-manager-0");
        assertThat(lease.getSpec().getLeaseDurationSeconds()).isEqualTo(30);
        assertThat(lease.getSpec().getAcquireTime()).isEqualTo(acquiredAt);
        assertThat(lease.getSpec().getRenewTime()).isEqualTo(renewedAt);
        assertThat(lease.getSpec().getLeaseTransitions()).isEqualTo(2);
        assertThat(lease.getSpec().getStrategy()).isEqualTo("Coordinated");
        assertThat(lease.getSpec().getPreferredHolder()).isEqualTo("controller-manager-1");
        assertThat(lease.getSpec().getAdditionalProperties()).containsEntry("election", "primary");
        assertThat(lease.getAdditionalProperties()).containsEntry("trace-id", "lease-1");

        Lease edited = lease.edit()
                .editMetadata()
                    .addToAnnotations("coordination.fabric8.io/verified", "true")
                .endMetadata()
                .editSpec()
                    .withHolderIdentity("controller-manager-1")
                    .withRenewTime(renewedAt.plusSeconds(15))
                    .withLeaseTransitions(3)
                    .addToAdditionalProperties("failover", true)
                .endSpec()
                .removeFromAdditionalProperties("trace-id")
                .build();

        assertThat(edited).isNotEqualTo(lease);
        assertThat(edited.toBuilder().build()).isEqualTo(edited);
        assertThat(edited.toBuilder().build().hashCode()).isEqualTo(edited.hashCode());
        assertThat(edited.getMetadata().getAnnotations())
                .containsEntry("coordination.fabric8.io/verified", "true");
        assertThat(edited.getSpec().getHolderIdentity()).isEqualTo("controller-manager-1");
        assertThat(edited.getSpec().getRenewTime()).isEqualTo(renewedAt.plusSeconds(15));
        assertThat(edited.getSpec().getLeaseTransitions()).isEqualTo(3);
        assertThat(edited.getSpec().getAdditionalProperties())
                .containsEntry("election", "primary")
                .containsEntry("failover", true);
        assertThat(edited.getAdditionalProperties()).doesNotContainKey("trace-id");
        assertThat(lease.getSpec().getHolderIdentity()).isEqualTo("controller-manager-0");
    }

    @Test
    void leaseSpecBuildersPreserveAdditionalPropertiesAndTemplateEditing() {
        LeaseSpec template = new LeaseSpecBuilder()
                .withHolderIdentity("scheduler-0")
                .withLeaseDurationSeconds(20)
                .withLeaseTransitions(1)
                .withStrategy("OldestEmulationVersion")
                .addToAdditionalProperties("source", "template")
                .build();
        ObjectMeta metadataTemplate = new ObjectMetaBuilder()
                .withName("scheduler")
                .withNamespace("kube-system")
                .addToLabels("component", "scheduler")
                .build();

        Lease lease = new LeaseBuilder()
                .editOrNewMetadataLike(metadataTemplate)
                    .addToAnnotations("coordination.fabric8.io/template", "metadata")
                .endMetadata()
                .editOrNewSpecLike(template)
                    .withHolderIdentity("scheduler-1")
                    .withPreferredHolder("scheduler-2")
                    .removeFromAdditionalProperties("source")
                    .addToAdditionalProperties("source", "edited")
                .endSpec()
                .build();

        assertThat(lease.getMetadata().getName()).isEqualTo("scheduler");
        assertThat(lease.getMetadata().getLabels()).containsEntry("component", "scheduler");
        assertThat(lease.getMetadata().getAnnotations())
                .containsEntry("coordination.fabric8.io/template", "metadata");
        assertThat(lease.getSpec().getHolderIdentity()).isEqualTo("scheduler-1");
        assertThat(lease.getSpec().getLeaseDurationSeconds()).isEqualTo(20);
        assertThat(lease.getSpec().getLeaseTransitions()).isEqualTo(1);
        assertThat(lease.getSpec().getPreferredHolder()).isEqualTo("scheduler-2");
        assertThat(lease.getSpec().getStrategy()).isEqualTo("OldestEmulationVersion");
        assertThat(lease.getSpec().getAdditionalProperties()).containsEntry("source", "edited");
        assertThat(template.getHolderIdentity()).isEqualTo("scheduler-0");
        assertThat(template.getAdditionalProperties()).containsEntry("source", "template");
        assertThat(metadataTemplate).extracting(ObjectMeta::getAnnotations)
                .satisfies(annotations -> assertThat(annotations).isEmpty());
    }

    @Test
    void leaseListsSupportNestedItemsPredicatesAndBulkMutation() {
        Lease scheduler = leaseNamed("scheduler", "scheduler-0");
        Lease controller = leaseNamed("controller-manager", "controller-manager-0");
        Lease cloud = leaseNamed("cloud-controller-manager", "cloud-controller-0");
        Lease replaced = leaseNamed("replaced", "replacement-0");

        LeaseListBuilder builder = new LeaseListBuilder()
                .withMetadata(new ListMeta("continue-token", 3L, "77", "self-link", null))
                .addToItems(scheduler, controller)
                .addToItems(1, cloud)
                .addToAdditionalProperties("source", "watch-cache");

        assertThat(builder.hasItems()).isTrue();
        assertThat(builder.hasMatchingItem(item -> "scheduler-0".equals(item.buildSpec().getHolderIdentity())))
                .isTrue();
        assertThat(builder.buildFirstItem().getMetadata().getName()).isEqualTo("scheduler");
        assertThat(builder.buildItem(1).getMetadata().getName()).isEqualTo("cloud-controller-manager");
        assertThat(builder.buildLastItem().getMetadata().getName()).isEqualTo("controller-manager");
        Lease matchingController = builder.buildMatchingItem(
                item -> "controller-manager-0".equals(item.buildSpec().getHolderIdentity()));
        assertThat(matchingController.getMetadata().getName()).isEqualTo("controller-manager");

        LeaseList list = builder
                .editMatchingItem(item -> "scheduler-0".equals(item.buildSpec().getHolderIdentity()))
                    .editSpec()
                        .withHolderIdentity("scheduler-1")
                        .withLeaseTransitions(2)
                    .endSpec()
                .endItem()
                .removeMatchingFromItems(item -> "cloud-controller-0".equals(item.buildSpec().getHolderIdentity()))
                .setToItems(1, replaced)
                .addNewItemLike(controller)
                    .editMetadata()
                        .withName("controller-manager-copy")
                    .endMetadata()
                    .editSpec()
                        .withHolderIdentity("controller-manager-1")
                    .endSpec()
                .endItem()
                .addToAdditionalProperties(Map.<String, Object>of("phase", "updated", "ordered", true))
                .removeFromAdditionalProperties(Map.<String, Object>of("phase", "updated"))
                .build();

        assertThat(list.getApiVersion()).isEqualTo(COORDINATION_API_GROUP + "/v1");
        assertThat(list.getKind()).isEqualTo("LeaseList");
        assertThat(list.getMetadata().getContinue()).isEqualTo("continue-token");
        assertThat(list.getMetadata().getResourceVersion()).isEqualTo("77");
        assertThat(list.getItems())
                .extracting(item -> item.getMetadata().getName())
                .containsExactly("scheduler", "replaced", "controller-manager-copy");
        assertThat(list.getItems())
                .extracting(item -> item.getSpec().getHolderIdentity())
                .containsExactly("scheduler-1", "replacement-0", "controller-manager-1");
        assertThat(list.getItems().get(0).getSpec().getLeaseTransitions()).isEqualTo(2);
        assertThat(list.getAdditionalProperties())
                .containsEntry("source", "watch-cache")
                .containsEntry("ordered", true)
                .doesNotContainKey("phase");
        assertThat(scheduler.getSpec().getHolderIdentity()).isEqualTo("scheduler-0");
        assertThat(controller.getMetadata().getName()).isEqualTo("controller-manager");
    }

    @Test
    void v1beta1LeaseCandidateBuilderCreatesAndEditsCandidateSpec() {
        MicroTime pingTime = new MicroTime("2026-02-01T00:00:00.000001Z");
        MicroTime renewTime = new MicroTime("2026-02-01T00:00:05.000001Z");

        io.fabric8.kubernetes.api.model.coordination.v1beta1.LeaseCandidate candidate =
                new io.fabric8.kubernetes.api.model.coordination.v1beta1.LeaseCandidateBuilder()
                .withNewMetadata()
                    .withName("kube-apiserver-candidate")
                    .withNamespace("kube-system")
                    .addToLabels("component", "apiserver")
                .endMetadata()
                .withNewSpec()
                    .withLeaseName("kube-apiserver")
                    .withPingTime(pingTime)
                    .withRenewTime(renewTime)
                    .withBinaryVersion("1.31.0")
                    .withEmulationVersion("1.30.0")
                    .withStrategy("OldestEmulationVersion")
                    .addToAdditionalProperties("priority", 100)
                .endSpec()
                .addToAdditionalProperties("candidate", "beta")
                .build();

        assertThat(candidate).isInstanceOf(HasMetadata.class);
        assertThat(candidate.getApiVersion()).isEqualTo(COORDINATION_API_GROUP + "/v1beta1");
        assertThat(candidate.getKind()).isEqualTo("LeaseCandidate");
        assertThat(candidate.getMetadata().getNamespace()).isEqualTo("kube-system");
        assertThat(candidate.getSpec().getLeaseName()).isEqualTo("kube-apiserver");
        assertThat(candidate.getSpec().getPingTime().getTime()).isEqualTo("2026-02-01T00:00:00.000001Z");
        assertThat(candidate.getSpec().getRenewTime().getTime()).isEqualTo("2026-02-01T00:00:05.000001Z");
        assertThat(candidate.getSpec().getBinaryVersion()).isEqualTo("1.31.0");
        assertThat(candidate.getSpec().getEmulationVersion()).isEqualTo("1.30.0");
        assertThat(candidate.getSpec().getStrategy()).isEqualTo("OldestEmulationVersion");
        assertThat(candidate.getSpec().getAdditionalProperties()).containsEntry("priority", 100);
        assertThat(candidate.getAdditionalProperties()).containsEntry("candidate", "beta");

        io.fabric8.kubernetes.api.model.coordination.v1beta1.LeaseCandidate edited = candidate.toBuilder()
                .editSpec()
                    .withNewRenewTime("2026-02-01T00:00:10.000001Z")
                    .withBinaryVersion("1.31.1")
                    .addToAdditionalProperties("healthy", true)
                .endSpec()
                .removeFromAdditionalProperties("candidate")
                .build();

        assertThat(edited.toBuilder().build()).isEqualTo(edited);
        assertThat(edited.getSpec().getRenewTime().getTime()).isEqualTo("2026-02-01T00:00:10.000001Z");
        assertThat(edited.getSpec().getBinaryVersion()).isEqualTo("1.31.1");
        assertThat(edited.getSpec().getAdditionalProperties())
                .containsEntry("priority", 100)
                .containsEntry("healthy", true);
        assertThat(edited.getAdditionalProperties()).doesNotContainKey("candidate");
        assertThat(candidate.getSpec().getBinaryVersion()).isEqualTo("1.31.0");
    }

    @Test
    void v1beta1LeaseCandidateListsSupportMatchingAndPositionalMutation() {
        io.fabric8.kubernetes.api.model.coordination.v1beta1.LeaseCandidate apiserver = betaCandidateNamed(
                "apiserver", "kube-apiserver", "1.31.0");
        io.fabric8.kubernetes.api.model.coordination.v1beta1.LeaseCandidate scheduler = betaCandidateNamed(
                "scheduler", "kube-scheduler", "1.31.0");
        io.fabric8.kubernetes.api.model.coordination.v1beta1.LeaseCandidate controller = betaCandidateNamed(
                "controller", "kube-controller-manager", "1.31.0");
        io.fabric8.kubernetes.api.model.coordination.v1beta1.LeaseCandidate replacement = betaCandidateNamed(
                "replacement", "replacement-lease", "1.31.1");

        io.fabric8.kubernetes.api.model.coordination.v1beta1.LeaseCandidateListBuilder builder =
                new io.fabric8.kubernetes.api.model.coordination.v1beta1.LeaseCandidateListBuilder()
                .withMetadata(new ListMeta(null, 3L, "88", null, null))
                .withItems(apiserver)
                .addAllToItems(List.of(scheduler, controller));

        assertThat(builder.hasMatchingItem(item -> "kube-scheduler".equals(item.buildSpec().getLeaseName())))
                .isTrue();
        assertThat(builder.buildMatchingItem(item -> "kube-apiserver".equals(item.buildSpec().getLeaseName()))
                .getSpec().getBinaryVersion()).isEqualTo("1.31.0");

        io.fabric8.kubernetes.api.model.coordination.v1beta1.LeaseCandidateList list = builder
                .editMatchingItem(item -> "kube-scheduler".equals(item.buildSpec().getLeaseName()))
                    .editSpec()
                        .withBinaryVersion("1.31.1")
                        .withNewRenewTime("2026-03-01T00:00:10.000001Z")
                    .endSpec()
                .endItem()
                .removeFromItems(controller)
                .setToItems(0, replacement)
                .addNewItemLike(apiserver)
                    .editMetadata()
                        .withName("apiserver-copy")
                    .endMetadata()
                    .editSpec()
                        .withLeaseName("kube-apiserver-copy")
                    .endSpec()
                .endItem()
                .addToAdditionalProperties("list", "beta")
                .build();

        assertThat(list.getApiVersion()).isEqualTo(COORDINATION_API_GROUP + "/v1beta1");
        assertThat(list.getKind()).isEqualTo("LeaseCandidateList");
        assertThat(list.getMetadata().getResourceVersion()).isEqualTo("88");
        assertThat(list.getItems())
                .extracting(item -> item.getMetadata().getName())
                .containsExactly("replacement", "scheduler", "apiserver-copy");
        assertThat(list.getItems())
                .extracting(item -> item.getSpec().getLeaseName())
                .containsExactly("replacement-lease", "kube-scheduler", "kube-apiserver-copy");
        assertThat(list.getItems().get(1).getSpec().getBinaryVersion()).isEqualTo("1.31.1");
        assertThat(list.getItems().get(1).getSpec().getRenewTime().getTime())
                .isEqualTo("2026-03-01T00:00:10.000001Z");
        assertThat(list.getAdditionalProperties()).containsEntry("list", "beta");
        assertThat(scheduler.getSpec().getBinaryVersion()).isEqualTo("1.31.0");
    }

    @Test
    void v1alpha2LeaseCandidatesPreserveTemplateSpecAndListState() {
        io.fabric8.kubernetes.api.model.coordination.v1alpha2.LeaseCandidateSpec specTemplate =
                new io.fabric8.kubernetes.api.model.coordination.v1alpha2.LeaseCandidateSpecBuilder()
                .withLeaseName("legacy-controller")
                .withNewPingTime("2026-04-01T00:00:00.000001Z")
                .withNewRenewTime("2026-04-01T00:00:05.000001Z")
                .withBinaryVersion("1.30.0")
                .withEmulationVersion("1.29.0")
                .withStrategy("OldestEmulationVersion")
                .addToAdditionalProperties("alpha", true)
                .build();

        io.fabric8.kubernetes.api.model.coordination.v1alpha2.LeaseCandidate candidate =
                new io.fabric8.kubernetes.api.model.coordination.v1alpha2.LeaseCandidateBuilder()
                .withNewMetadata()
                    .withName("legacy-controller")
                    .withNamespace("kube-system")
                .endMetadata()
                .editOrNewSpecLike(specTemplate)
                    .withBinaryVersion("1.30.1")
                    .addToAdditionalProperties("promoted", true)
                .endSpec()
                .build();
        io.fabric8.kubernetes.api.model.coordination.v1alpha2.LeaseCandidate standby = alphaCandidateNamed(
                "legacy-standby", "legacy-controller", "1.30.0");

        io.fabric8.kubernetes.api.model.coordination.v1alpha2.LeaseCandidateList list =
                new io.fabric8.kubernetes.api.model.coordination.v1alpha2.LeaseCandidateListBuilder()
                .withMetadata(new ListMeta("next", 2L, "99", null, null))
                .addToItems(candidate, standby)
                .editLastItem()
                    .editSpec()
                        .withStrategy("Coordinated")
                    .endSpec()
                .endItem()
                .addToAdditionalProperties("list", "alpha")
                .build();

        assertThat(candidate.getApiVersion()).isEqualTo(COORDINATION_API_GROUP + "/v1alpha2");
        assertThat(candidate.getKind()).isEqualTo("LeaseCandidate");
        assertThat(candidate.getSpec().getLeaseName()).isEqualTo("legacy-controller");
        assertThat(candidate.getSpec().getPingTime().getTime()).isEqualTo("2026-04-01T00:00:00.000001Z");
        assertThat(candidate.getSpec().getRenewTime().getTime()).isEqualTo("2026-04-01T00:00:05.000001Z");
        assertThat(candidate.getSpec().getBinaryVersion()).isEqualTo("1.30.1");
        assertThat(candidate.getSpec().getEmulationVersion()).isEqualTo("1.29.0");
        assertThat(candidate.getSpec().getAdditionalProperties())
                .containsEntry("alpha", true)
                .containsEntry("promoted", true);
        assertThat(specTemplate.getBinaryVersion()).isEqualTo("1.30.0");
        assertThat(specTemplate.getAdditionalProperties()).containsEntry("alpha", true);

        assertThat(list.getApiVersion()).isEqualTo(COORDINATION_API_GROUP + "/v1alpha2");
        assertThat(list.getKind()).isEqualTo("LeaseCandidateList");
        assertThat(list.getMetadata().getContinue()).isEqualTo("next");
        assertThat(list.getMetadata().getResourceVersion()).isEqualTo("99");
        assertThat(list.getItems())
                .extracting(item -> item.getMetadata().getName())
                .containsExactly("legacy-controller", "legacy-standby");
        assertThat(list.getItems().get(1).getSpec().getStrategy()).isEqualTo("Coordinated");
        assertThat(list.getAdditionalProperties()).containsEntry("list", "alpha");
    }

    @Test
    void directPojoConstructorsAndSettersPreserveCoordinationResourceState() {
        ZonedDateTime acquiredAt = ZonedDateTime.of(2026, 5, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime renewedAt = ZonedDateTime.of(2026, 5, 1, 12, 0, 5, 0, ZoneOffset.UTC);
        ObjectMeta metadata = new ObjectMetaBuilder()
                .withName("lease-from-pojo")
                .withNamespace("kube-system")
                .addToLabels("created-by", "constructor")
                .build();
        LeaseSpec spec = new LeaseSpec(
                acquiredAt,
                "pojo-holder-0",
                15,
                1,
                "pojo-holder-1",
                renewedAt,
                "Coordinated");

        spec.setHolderIdentity("pojo-holder-2");
        spec.setLeaseDurationSeconds(20);
        spec.setLeaseTransitions(2);
        spec.setPreferredHolder("pojo-holder-3");
        spec.setRenewTime(renewedAt.plusSeconds(5));
        spec.setStrategy("OldestEmulationVersion");
        spec.setAdditionalProperty("path", "setters");

        Lease lease = new Lease(COORDINATION_API_GROUP + "/v1", "Lease", metadata, spec);
        lease.setAdditionalProperties(Map.of("constructed", true));
        lease.setMetadata(new ObjectMetaBuilder(lease.getMetadata())
                .addToAnnotations("coordination.fabric8.io/mutation", "setter")
                .build());

        assertThat(lease).isInstanceOf(Namespaced.class);
        assertThat(lease.getApiVersion()).isEqualTo(COORDINATION_API_GROUP + "/v1");
        assertThat(lease.getKind()).isEqualTo("Lease");
        assertThat(lease.getMetadata().getName()).isEqualTo("lease-from-pojo");
        assertThat(lease.getMetadata().getAnnotations())
                .containsEntry("coordination.fabric8.io/mutation", "setter");
        assertThat(lease.getSpec().getAcquireTime()).isEqualTo(acquiredAt);
        assertThat(lease.getSpec().getHolderIdentity()).isEqualTo("pojo-holder-2");
        assertThat(lease.getSpec().getLeaseDurationSeconds()).isEqualTo(20);
        assertThat(lease.getSpec().getLeaseTransitions()).isEqualTo(2);
        assertThat(lease.getSpec().getPreferredHolder()).isEqualTo("pojo-holder-3");
        assertThat(lease.getSpec().getRenewTime()).isEqualTo(renewedAt.plusSeconds(5));
        assertThat(lease.getSpec().getStrategy()).isEqualTo("OldestEmulationVersion");
        assertThat(lease.getSpec().getAdditionalProperties()).containsEntry("path", "setters");
        assertThat(lease.getAdditionalProperties()).containsEntry("constructed", true);

        ListMeta listMetadata = new ListMeta(null, 1L, "resource-version", null, null);
        LeaseList list = new LeaseList(COORDINATION_API_GROUP + "/v1", List.of(lease), "LeaseList", listMetadata);
        Lease replacement = leaseNamed("lease-from-list-setter", "list-holder-0");
        list.setItems(List.of(replacement));
        list.setAdditionalProperty("source", "pojo-list");

        assertThat(list).isInstanceOf(KubernetesResourceList.class);
        assertThat(list.getApiVersion()).isEqualTo(COORDINATION_API_GROUP + "/v1");
        assertThat(list.getKind()).isEqualTo("LeaseList");
        assertThat(list.getMetadata().getResourceVersion()).isEqualTo("resource-version");
        assertThat(list.getItems()).containsExactly(replacement);
        assertThat(list.getAdditionalProperties()).containsEntry("source", "pojo-list");
    }

    @Test
    void serviceLoaderDiscoversCoordinationResources() {
        Set<Class<?>> resourceTypes = new LinkedHashSet<>();
        for (KubernetesResource resource : ServiceLoader.load(KubernetesResource.class)) {
            resourceTypes.add(resource.getClass());
        }

        assertThat(resourceTypes).contains(
                Lease.class,
                LeaseList.class,
                io.fabric8.kubernetes.api.model.coordination.v1beta1.LeaseCandidate.class,
                io.fabric8.kubernetes.api.model.coordination.v1beta1.LeaseCandidateList.class,
                io.fabric8.kubernetes.api.model.coordination.v1alpha2.LeaseCandidate.class,
                io.fabric8.kubernetes.api.model.coordination.v1alpha2.LeaseCandidateList.class);
    }

    @Test
    void coordinationResourcesExposeMetadataHelpersForNamesFinalizersAndOwners() {
        Lease leader = new LeaseBuilder()
                .withNewMetadata()
                    .withName("controller-leader")
                    .withNamespace("kube-system")
                    .withUid("leader-uid")
                .endMetadata()
                .build();
        io.fabric8.kubernetes.api.model.coordination.v1beta1.LeaseCandidate candidate =
                new io.fabric8.kubernetes.api.model.coordination.v1beta1.LeaseCandidateBuilder()
                .withNewMetadata()
                    .withName("controller-candidate")
                    .withNamespace("kube-system")
                    .withUid("candidate-uid")
                    .addToFinalizers("coordination.fabric8.io/bootstrap")
                .endMetadata()
                .build();

        assertThat(leader.getPlural()).isEqualTo("leases");
        assertThat(leader.getSingular()).isEqualTo("lease");
        assertThat(leader.getFullResourceName()).isEqualTo("leases." + COORDINATION_API_GROUP);
        assertThat(candidate.getPlural()).isEqualTo("leasecandidates");
        assertThat(candidate.getFullResourceName()).isEqualTo("leasecandidates." + COORDINATION_API_GROUP);

        assertThat(candidate.getFinalizers()).containsExactly("coordination.fabric8.io/bootstrap");
        assertThat(candidate.addFinalizer("coordination.fabric8.io/cleanup")).isTrue();
        assertThat(candidate.addFinalizer("coordination.fabric8.io/cleanup")).isFalse();
        assertThat(candidate.hasFinalizer("coordination.fabric8.io/cleanup")).isTrue();
        assertThat(candidate.removeFinalizer("coordination.fabric8.io/bootstrap")).isTrue();
        assertThat(candidate.getFinalizers()).containsExactly("coordination.fabric8.io/cleanup");

        OwnerReference leaderReference = candidate.addOwnerReference(leader);

        assertThat(leaderReference.getApiVersion()).isEqualTo(COORDINATION_API_GROUP + "/v1");
        assertThat(leaderReference.getKind()).isEqualTo("Lease");
        assertThat(leaderReference.getName()).isEqualTo("controller-leader");
        assertThat(leaderReference.getUid()).isEqualTo("leader-uid");
        assertThat(candidate.hasOwnerReferenceFor(leader)).isTrue();
        assertThat(candidate.getOwnerReferenceFor("leader-uid")).contains(leaderReference);

        candidate.removeOwnerReference(leader);

        assertThat(candidate.hasOwnerReferenceFor(leader)).isFalse();
        assertThat(candidate.getMetadata().getOwnerReferences()).isEmpty();
    }

    private static Lease leaseNamed(String name, String holderIdentity) {
        return new LeaseBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace("kube-system")
                .endMetadata()
                .withNewSpec()
                    .withHolderIdentity(holderIdentity)
                    .withLeaseDurationSeconds(30)
                    .withAcquireTime(ZonedDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC))
                    .withRenewTime(ZonedDateTime.of(2026, 1, 1, 10, 0, 5, 0, ZoneOffset.UTC))
                    .withLeaseTransitions(1)
                .endSpec()
                .build();
    }

    private static io.fabric8.kubernetes.api.model.coordination.v1beta1.LeaseCandidate betaCandidateNamed(
            String name, String leaseName, String binaryVersion) {
        return new io.fabric8.kubernetes.api.model.coordination.v1beta1.LeaseCandidateBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace("kube-system")
                .endMetadata()
                .withNewSpec()
                    .withLeaseName(leaseName)
                    .withNewPingTime("2026-03-01T00:00:00.000001Z")
                    .withNewRenewTime("2026-03-01T00:00:05.000001Z")
                    .withBinaryVersion(binaryVersion)
                    .withEmulationVersion("1.30.0")
                    .withStrategy("OldestEmulationVersion")
                .endSpec()
                .build();
    }

    private static io.fabric8.kubernetes.api.model.coordination.v1alpha2.LeaseCandidate alphaCandidateNamed(
            String name, String leaseName, String binaryVersion) {
        return new io.fabric8.kubernetes.api.model.coordination.v1alpha2.LeaseCandidateBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace("kube-system")
                .endMetadata()
                .withNewSpec()
                    .withLeaseName(leaseName)
                    .withNewPingTime("2026-04-01T00:00:00.000001Z")
                    .withNewRenewTime("2026-04-01T00:00:05.000001Z")
                    .withBinaryVersion(binaryVersion)
                    .withEmulationVersion("1.29.0")
                    .withStrategy("OldestEmulationVersion")
                .endSpec()
                .build();
    }
}
