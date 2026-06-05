/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_events;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.MicroTime;
import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.api.model.events.v1.EventBuilder;
import io.fabric8.kubernetes.api.model.events.v1.EventList;
import io.fabric8.kubernetes.api.model.events.v1.EventListBuilder;
import io.fabric8.kubernetes.api.model.events.v1.EventSeries;
import io.fabric8.kubernetes.api.model.events.v1.EventSeriesBuilder;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class Kubernetes_model_eventsTest {
    @Test
    void buildsAndEditsStableEventWithReferencesSeriesAndDeprecatedFields() {
        Event event = new EventBuilder()
                .withNewMetadata()
                    .withName("pod-started.17b4c8")
                    .withNamespace("production")
                    .addToLabels("app", "checkout")
                    .addToAnnotations("events.fabric8.io/source", "test-suite")
                .endMetadata()
                .withAction("Started")
                .withReason("Started")
                .withNote("Started container checkout")
                .withType("Normal")
                .withReportingController("kubelet")
                .withReportingInstance("node-a")
                .withNewEventTime("2026-01-01T00:00:00.123456Z")
                .withNewRegarding()
                    .withApiVersion("v1")
                    .withKind("Pod")
                    .withNamespace("production")
                    .withName("checkout-0")
                    .withUid("pod-uid")
                    .withResourceVersion("17")
                    .withFieldPath("spec.containers{checkout}")
                .endRegarding()
                .withNewRelated()
                    .withApiVersion("v1")
                    .withKind("Node")
                    .withName("node-a")
                    .withUid("node-uid")
                .endRelated()
                .withNewSeries()
                    .withCount(3)
                    .withNewLastObservedTime("2026-01-01T00:01:00.654321Z")
                    .addToAdditionalProperties("aggregation", "kubelet")
                .endSeries()
                .withDeprecatedCount(1)
                .withDeprecatedFirstTimestamp("2026-01-01T00:00:00Z")
                .withDeprecatedLastTimestamp("2026-01-01T00:01:00Z")
                .withNewDeprecatedSource("kubelet", "node-a")
                .addToAdditionalProperties("audit-id", "event-audit-1")
                .build();

        assertThat(event).isInstanceOf(HasMetadata.class);
        assertThat(event.getApiVersion()).isEqualTo("events.k8s.io/v1");
        assertThat(event.getKind()).isEqualTo("Event");
        assertThat(event.getMetadata().getLabels()).containsEntry("app", "checkout");
        assertThat(event.getAction()).isEqualTo("Started");
        assertThat(event.getEventTime().getTime()).isEqualTo("2026-01-01T00:00:00.123456Z");
        assertThat(event.getRegarding().getFieldPath()).isEqualTo("spec.containers{checkout}");
        assertThat(event.getRelated().getKind()).isEqualTo("Node");
        assertThat(event.getSeries().getCount()).isEqualTo(3);
        assertThat(event.getSeries().getLastObservedTime().getTime()).isEqualTo("2026-01-01T00:01:00.654321Z");
        assertThat(event.getSeries().getAdditionalProperties()).containsEntry("aggregation", "kubelet");
        assertThat(event.getDeprecatedSource().getComponent()).isEqualTo("kubelet");
        assertThat(event.getAdditionalProperties()).containsEntry("audit-id", "event-audit-1");

        Event edited = event.toBuilder()
                .editMetadata()
                    .addToAnnotations("events.fabric8.io/processed", "true")
                .endMetadata()
                .withNote("Started and verified container checkout")
                .editSeries()
                    .withCount(4)
                    .withNewLastObservedTime("2026-01-01T00:02:00.000001Z")
                .endSeries()
                .removeFromAdditionalProperties("audit-id")
                .build();

        assertThat(edited).isNotEqualTo(event);
        assertThat(edited.toBuilder().build()).isEqualTo(edited);
        assertThat(edited.toBuilder().build().hashCode()).isEqualTo(edited.hashCode());
        assertThat(edited.getMetadata().getAnnotations()).containsEntry("events.fabric8.io/processed", "true");
        assertThat(edited.getSeries().getCount()).isEqualTo(4);
        assertThat(edited.getSeries().getLastObservedTime().getTime()).isEqualTo("2026-01-01T00:02:00.000001Z");
        assertThat(edited.getAdditionalProperties()).doesNotContainKey("audit-id");
        assertThat(event.getSeries().getCount()).isEqualTo(3);
    }

    @Test
    void buildsAndEditsStableEventListsWithNestedItemPredicates() {
        Event scheduled = eventNamed("checkout-0.17b4c8", "Scheduled", "Pod assigned to node-a");
        Event pulled = eventNamed("checkout-0.17b4d0", "Pulled", "Container image is present");
        Event started = eventNamed("checkout-0.17b4d8", "Started", "Started checkout container");

        EventListBuilder builder = new EventListBuilder()
                .withNewMetadata("continue-token", 3L, "7", "rv-7")
                .addToItems(scheduled, pulled, started)
                .addToAdditionalProperties("source", "watch-cache");

        assertThat(builder.hasItems()).isTrue();
        assertThat(builder.hasMatchingItem(item -> "Pulled".equals(item.getReason()))).isTrue();
        assertThat(builder.buildMatchingItem(item -> "Scheduled".equals(item.getReason())).getNote())
                .isEqualTo("Pod assigned to node-a");

        EventList list = builder
                .editMatchingItem(item -> "Started".equals(item.getReason()))
                    .editSeries()
                        .withCount(2)
                    .endSeries()
                .endItem()
                .removeMatchingFromItems(item -> "Pulled".equals(item.getReason()))
                .addNewItemLike(pulled)
                    .withAction("ImagePull")
                    .withReason("Pulling")
                    .withNote("Pulling image for checkout")
                .endItem()
                .build();

        assertThat(list.getApiVersion()).isEqualTo("events.k8s.io/v1");
        assertThat(list.getKind()).isEqualTo("EventList");
        assertThat(list.getMetadata().getContinue()).isEqualTo("continue-token");
        assertThat(list.getItems())
                .extracting(Event::getReason)
                .containsExactly("Scheduled", "Started", "Pulling");
        assertThat(list.getItems())
                .extracting(item -> item.getMetadata().getName())
                .containsExactly("checkout-0.17b4c8", "checkout-0.17b4d8", "checkout-0.17b4d0");
        assertThat(list.getItems().get(1).getSeries().getCount()).isEqualTo(2);
        assertThat(list.getAdditionalProperties()).containsEntry("source", "watch-cache");
        assertThat(pulled.getReason()).isEqualTo("Pulled");
    }

    @Test
    void stableEventListsSupportPositionalAndBulkItemMutation() {
        Event pending = eventNamed("checkout-0.pending", "Pending", "Pod is waiting for scheduling");
        Event scheduled = eventNamed("checkout-0.scheduled", "Scheduled", "Pod assigned to node-a");
        Event failed = eventNamed("checkout-0.failed", "Failed", "Container start failed");
        Event recovered = eventNamed("checkout-0.recovered", "Recovered", "Container recovered");
        Event running = eventNamed("checkout-0.running", "Running", "Container is running");

        EventListBuilder builder = new EventListBuilder()
                .withItems(pending)
                .addToItems(0, scheduled)
                .addAllToItems(List.of(failed, recovered));

        assertThat(builder.buildFirstItem().getReason()).isEqualTo("Scheduled");
        assertThat(builder.buildItem(1).getReason()).isEqualTo("Pending");
        assertThat(builder.buildLastItem().getReason()).isEqualTo("Recovered");
        assertThat(builder.buildItems())
                .extracting(Event::getReason)
                .containsExactly("Scheduled", "Pending", "Failed", "Recovered");

        EventList list = builder
                .setToItems(1, running)
                .removeFromItems(failed)
                .removeAllFromItems(List.of(recovered))
                .addToAdditionalProperties(Map.<String, Object>of("ordered", true, "phase", "updated"))
                .removeFromAdditionalProperties(Map.<String, Object>of("phase", "updated"))
                .build();

        assertThat(list.getItems())
                .extracting(Event::getReason)
                .containsExactly("Scheduled", "Running");
        assertThat(list.getItems())
                .extracting(item -> item.getMetadata().getName())
                .containsExactly("checkout-0.scheduled", "checkout-0.running");
        assertThat(list.getAdditionalProperties())
                .containsEntry("ordered", true)
                .doesNotContainKey("phase");
        assertThat(pending.getReason()).isEqualTo("Pending");
    }

    @Test
    void buildsAndEditsBetaEventWithReferencesAndSeries() {
        io.fabric8.kubernetes.api.model.events.v1beta1.Event event =
                new io.fabric8.kubernetes.api.model.events.v1beta1.EventBuilder()
                .withNewMetadata()
                    .withName("beta-pod-created.17b4c8")
                    .withNamespace("staging")
                    .addToLabels("app", "payments")
                .endMetadata()
                .withAction("Created")
                .withReason("Created")
                .withNote("Created beta pod")
                .withType("Normal")
                .withReportingController("statefulset-controller")
                .withReportingInstance("controller-manager-0")
                .withNewEventTime("2026-02-01T00:00:00.000001Z")
                .withNewRegarding()
                    .withApiVersion("v1")
                    .withKind("Pod")
                    .withNamespace("staging")
                    .withName("payments-0")
                    .withUid("beta-pod-uid")
                .endRegarding()
                .withNewRelated()
                    .withApiVersion("apps/v1")
                    .withKind("StatefulSet")
                    .withNamespace("staging")
                    .withName("payments")
                .endRelated()
                .withNewSeries()
                    .withCount(5)
                    .withNewLastObservedTime("2026-02-01T00:05:00.000001Z")
                    .addToAdditionalProperties("beta-series", true)
                .endSeries()
                .withDeprecatedCount(5)
                .withNewDeprecatedSource("statefulset-controller", "controller-manager-0")
                .addToAdditionalProperties("beta-extension", Map.of("kept", true))
                .build();

        assertThat(event).isInstanceOf(HasMetadata.class);
        assertThat(event.getApiVersion()).isEqualTo("events.k8s.io/v1beta1");
        assertThat(event.getKind()).isEqualTo("Event");
        assertThat(event.getMetadata().getNamespace()).isEqualTo("staging");
        assertThat(event.getRelated().getKind()).isEqualTo("StatefulSet");
        assertThat(event.getSeries().getCount()).isEqualTo(5);
        assertThat(event.getSeries().getAdditionalProperties()).containsEntry("beta-series", true);
        assertThat(event.getAdditionalProperties()).containsKey("beta-extension");

        io.fabric8.kubernetes.api.model.events.v1beta1.Event edited = event.edit()
                .withType("Warning")
                .withReason("CreateFailed")
                .editRegarding()
                    .withFieldPath("spec.containers{payments}")
                .endRegarding()
                .editSeries()
                    .withCount(6)
                .endSeries()
                .build();

        assertThat(edited.toBuilder().build()).isEqualTo(edited);
        assertThat(edited.getType()).isEqualTo("Warning");
        assertThat(edited.getReason()).isEqualTo("CreateFailed");
        assertThat(edited.getRegarding().getFieldPath()).isEqualTo("spec.containers{payments}");
        assertThat(edited.getSeries().getCount()).isEqualTo(6);
        assertThat(event.getReason()).isEqualTo("Created");
    }

    @Test
    void buildsBetaEventListsWithNestedItemsAndPredicates() {
        io.fabric8.kubernetes.api.model.events.v1beta1.Event ready = betaEventNamed(
                "payments-0.ready", "Ready", "Pod is ready");
        io.fabric8.kubernetes.api.model.events.v1beta1.Event unhealthy = betaEventNamed(
                "payments-0.unhealthy", "Unhealthy", "Readiness probe failed");

        io.fabric8.kubernetes.api.model.events.v1beta1.EventList list =
                new io.fabric8.kubernetes.api.model.events.v1beta1.EventListBuilder()
                .withNewMetadata(null, 2L, "9", "rv-9")
                .withItems(ready, unhealthy)
                .editMatchingItem(item -> "Unhealthy".equals(item.getReason()))
                    .withType("Warning")
                    .editSeries()
                        .withCount(3)
                    .endSeries()
                .endItem()
                .addNewItem()
                    .withNewMetadata()
                        .withName("payments-0.recovered")
                        .withNamespace("staging")
                    .endMetadata()
                    .withAction("HealthCheck")
                    .withReason("Recovered")
                    .withNote("Readiness probe recovered")
                    .withType("Normal")
                    .withReportingController("kubelet")
                    .withReportingInstance("node-b")
                    .withNewEventTime("2026-02-01T00:07:00.000001Z")
                    .withNewRegarding()
                        .withApiVersion("v1")
                        .withKind("Pod")
                        .withNamespace("staging")
                        .withName("payments-0")
                    .endRegarding()
                    .withNewSeries()
                        .withCount(1)
                        .withNewLastObservedTime("2026-02-01T00:07:00.000001Z")
                    .endSeries()
                .endItem()
                .addToAdditionalProperties("watch", "beta")
                .build();

        assertThat(list.getApiVersion()).isEqualTo("events.k8s.io/v1beta1");
        assertThat(list.getKind()).isEqualTo("EventList");
        assertThat(list.getMetadata().getResourceVersion()).isEqualTo("9");
        assertThat(list.getItems())
                .extracting(item -> item.getReason())
                .containsExactly("Ready", "Unhealthy", "Recovered");
        assertThat(list.getItems())
                .extracting(item -> item.getMetadata().getNamespace())
                .containsExactly("staging", "staging", "staging");
        assertThat(list.getItems().get(1).getType()).isEqualTo("Warning");
        assertThat(list.getItems().get(1).getSeries().getCount()).isEqualTo(3);
        assertThat(list.getAdditionalProperties()).containsEntry("watch", "beta");
    }

    @Test
    void eventSeriesBuildersPreserveMicroTimeAndExtensions() {
        EventSeries series = new EventSeriesBuilder()
                .withCount(10)
                .withNewLastObservedTime("2026-03-01T10:15:30.000001Z")
                .addToAdditionalProperties("window", "five-minutes")
                .build();

        EventSeries edited = series.edit()
                .withCount(11)
                .withLastObservedTime(new MicroTime("2026-03-01T10:16:30.000001Z"))
                .addToAdditionalProperties("compacted", true)
                .build();

        assertThat(series.getCount()).isEqualTo(10);
        assertThat(edited.getCount()).isEqualTo(11);
        assertThat(edited.getLastObservedTime().getTime()).isEqualTo("2026-03-01T10:16:30.000001Z");
        assertThat(edited.getAdditionalProperties())
                .containsEntry("window", "five-minutes")
                .containsEntry("compacted", true);
        assertThat(edited.toBuilder().build()).isEqualTo(edited);

        io.fabric8.kubernetes.api.model.events.v1beta1.EventSeries betaSeries =
                new io.fabric8.kubernetes.api.model.events.v1beta1.EventSeriesBuilder()
                .withCount(2)
                .withNewLastObservedTime("2026-03-02T10:15:30.000001Z")
                .addToAdditionalProperties("beta-window", "one-minute")
                .build();

        assertThat(betaSeries.getCount()).isEqualTo(2);
        assertThat(betaSeries.getLastObservedTime().getTime()).isEqualTo("2026-03-02T10:15:30.000001Z");
        assertThat(betaSeries.getAdditionalProperties()).containsEntry("beta-window", "one-minute");
        assertThat(betaSeries.toBuilder().build()).isEqualTo(betaSeries);
    }

    @Test
    void serviceLoaderDiscoversEventResources() {
        Set<Class<?>> resourceTypes = new LinkedHashSet<>();
        for (KubernetesResource resource : ServiceLoader.load(KubernetesResource.class)) {
            resourceTypes.add(resource.getClass());
        }

        assertThat(resourceTypes).contains(
                Event.class,
                EventList.class,
                io.fabric8.kubernetes.api.model.events.v1beta1.Event.class,
                io.fabric8.kubernetes.api.model.events.v1beta1.EventList.class);
    }

    private static Event eventNamed(String name, String reason, String note) {
        return new EventBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace("production")
                .endMetadata()
                .withAction(reason)
                .withReason(reason)
                .withNote(note)
                .withType("Normal")
                .withReportingController("kubelet")
                .withReportingInstance("node-a")
                .withNewEventTime("2026-01-01T00:00:00.000001Z")
                .withNewRegarding()
                    .withApiVersion("v1")
                    .withKind("Pod")
                    .withNamespace("production")
                    .withName("checkout-0")
                .endRegarding()
                .withNewSeries()
                    .withCount(1)
                    .withNewLastObservedTime("2026-01-01T00:00:00.000001Z")
                .endSeries()
                .build();
    }

    private static io.fabric8.kubernetes.api.model.events.v1beta1.Event betaEventNamed(
            String name, String reason, String note) {
        return new io.fabric8.kubernetes.api.model.events.v1beta1.EventBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace("staging")
                .endMetadata()
                .withAction(reason)
                .withReason(reason)
                .withNote(note)
                .withType("Normal")
                .withReportingController("kubelet")
                .withReportingInstance("node-b")
                .withNewEventTime("2026-02-01T00:00:00.000001Z")
                .withNewRegarding()
                    .withApiVersion("v1")
                    .withKind("Pod")
                    .withNamespace("staging")
                    .withName("payments-0")
                .endRegarding()
                .withNewSeries()
                    .withCount(1)
                    .withNewLastObservedTime("2026-02-01T00:00:00.000001Z")
                .endSeries()
                .build();
    }
}
