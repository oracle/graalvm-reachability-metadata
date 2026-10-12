/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_events_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.EventExternalized;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublication.Status;
import org.springframework.modulith.events.ExternalizationMode;
import org.springframework.modulith.events.Externalized;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class Spring_modulith_events_apiTest {

    @Test
    void defaultConfigurationDiscoversExternalizedEventsAndFillsInLocalTarget() {
        FallbackExternalizedEvent event = new FallbackExternalizedEvent("customer-17");
        String basePackage = Spring_modulith_events_apiTest.class.getPackageName();
        String localTypeName = FallbackExternalizedEvent.class.getName().substring(basePackage.length() + 1);

        EventExternalizationConfiguration configuration =
                EventExternalizationConfiguration.defaults(basePackage).build();

        assertThat(configuration.supports(event)).isTrue();
        assertThat(configuration.supports(new UnannotatedEvent("ignored"))).isFalse();
        assertThat(configuration.map(event)).isSameAs(event);
        assertThat(configuration.getHeadersFor(event)).isEmpty();
        assertThat(configuration.serializeExternalization()).isFalse();

        RoutingTarget target = configuration.determineTarget(event);
        assertThat(target.getTarget()).isEqualTo(localTypeName);
        assertThat(target.getKey()).isEqualTo("#{#this.customerId}");
        assertThat(target.hasKeyExpression()).isTrue();
    }

    @Test
    void customConfigurationMapsRoutesAndAddsHeadersForSelectedEventType() {
        OrderPlaced event = new OrderPlaced("order-41", "tenant-a");

        EventExternalizationConfiguration configuration = EventExternalizationConfiguration.externalizing()
                .selectByType(OrderPlaced.class)
                .mapping(OrderPlaced.class, source -> new OutboundOrder(source.orderId()))
                .headers(OrderPlaced.class, source -> Map.of("tenant", source.tenant()))
                .routeAllByType(type -> RoutingTarget.forTarget("mapped." + type.getSimpleName()).withoutKey())
                .routeMapped()
                .routeKey(OrderPlaced.class, OrderPlaced::orderId)
                .serializeExternalization(true)
                .build();

        assertThat(configuration.supports(event)).isTrue();
        assertThat(configuration.supports("not-an-order")).isFalse();
        assertThat(configuration.map(event)).isEqualTo(new OutboundOrder("order-41"));
        assertThat(configuration.getHeadersFor(event)).containsEntry("tenant", "tenant-a");
        assertThat(configuration.determineTarget(event).toString()).isEqualTo("mapped.OutboundOrder::order-41");
        assertThat(configuration.serializeExternalization()).isTrue();
    }

    @Test
    void annotationBasedSelectorUsesAnnotationValueAsRoute() {
        InvoiceIssued event = new InvoiceIssued("invoice-9");

        EventExternalizationConfiguration configuration = EventExternalizationConfiguration.externalizing()
                .selectAndRoute(Destination.class, Destination::value)
                .build();

        assertThat(configuration.supports(event)).isTrue();
        assertThat(configuration.supports(new UnannotatedEvent("ignored"))).isFalse();
        assertThat(configuration.determineTarget(event))
                .isEqualTo(RoutingTarget.forTarget("invoices").andKey("#{#this.invoiceId}"));
    }

    @Test
    void annotationAndEventAwareRouterBuildsTargetFromBothInputs() {
        ShipmentDispatched event = new ShipmentDispatched("shipment-23");

        EventExternalizationConfiguration configuration = EventExternalizationConfiguration.externalizing()
                .selectAndRoute(Destination.class, (candidate, destination) -> {
                    ShipmentDispatched shipment = (ShipmentDispatched) candidate;
                    return RoutingTarget.forTarget(destination.value()).andKey(shipment.shipmentId());
                })
                .build();

        assertThat(configuration.supports(event)).isTrue();
        assertThat(configuration.determineTarget(event))
                .isEqualTo(RoutingTarget.forTarget("shipments").andKey("shipment-23"));
    }

    @Test
    void optionalTypeRoutingFallsBackToGeneralRoute() {
        PriorityEvent priority = new PriorityEvent("high");
        UnannotatedEvent ordinary = new UnannotatedEvent("ordinary");

        EventExternalizationConfiguration configuration = EventExternalizationConfiguration.externalizing()
                .select(event -> true)
                .routeAll(event -> RoutingTarget.forTarget("events.default").withoutKey())
                .routeOptionalByType(type -> type == PriorityEvent.class
                        ? Optional.of(RoutingTarget.forTarget("events.priority").andKey("urgent"))
                        : Optional.empty());

        assertThat(configuration.determineTarget(priority).toString()).isEqualTo("events.priority::urgent");
        assertThat(configuration.determineTarget(ordinary).toString()).isEqualTo("events.default");
        assertThat(EventExternalizationConfiguration.disabled().supports(priority)).isFalse();
    }

    @Test
    void routingTargetsExposeValueAndExpressionSemantics() {
        RoutingTarget keyed = RoutingTarget.forTarget(" orders ").andKey(" #{#this.orderId} ");
        RoutingTarget same = RoutingTarget.forTarget("orders").andKey("#{#this.orderId}");
        RoutingTarget expressionTarget = RoutingTarget.forTarget("#{#this.destination}").withoutKey();

        assertThat(keyed.getTarget()).isEqualTo("orders");
        assertThat(keyed.getKey()).isEqualTo("#{#this.orderId}");
        assertThat(keyed.hasKeyExpression()).isTrue();
        assertThat(keyed.hasExpression()).isTrue();
        assertThat(keyed.toString()).isEqualTo("orders::#{#this.orderId}");
        assertThat(keyed).isEqualTo(same);
        assertThat(keyed.hashCode()).isEqualTo(same.hashCode());
        assertThat(expressionTarget.hasExpression()).isTrue();

        String basePackage = Spring_modulith_events_apiTest.class.getPackageName();
        Optional<RoutingTarget> local = EventExternalizationConfiguration.byApplicationLocalName(
                List.of(basePackage)).apply(new UnannotatedEvent("event"));
        RoutingTarget qualified = EventExternalizationConfiguration.byFullyQualifiedTypeName()
                .apply(new UnannotatedEvent("event"));

        assertThat(local).isPresent();
        assertThat(local.orElseThrow().getTarget()).isEqualTo(
                UnannotatedEvent.class.getName().substring(basePackage.length() + 1));
        assertThat(qualified.getTarget()).isEqualTo(UnannotatedEvent.class.getName());
        assertThat(qualified.getKey()).isNull();
    }

    @Test
    void externalizedEventRetainsPayloadRoutingAndResolvableGenericTypes() {
        OrderPlaced source = new OrderPlaced("order-12", "tenant-b");
        OutboundOrder mapped = new OutboundOrder("order-12");
        RoutingTarget target = RoutingTarget.forTarget("orders").andKey("order-12");

        EventExternalized<OrderPlaced, String> event =
                new EventExternalized<>(source, mapped, target, "broker-offset-3");
        EventExternalized<OrderPlaced, String> equivalent =
                new EventExternalized<>(source, mapped, target, "broker-offset-3");

        assertThat(event.getEvent()).isSameAs(source);
        assertThat(event.getEventType()).isEqualTo(OrderPlaced.class);
        assertThat(event.getMapped()).isSameAs(mapped);
        assertThat(event.getTarget()).isSameAs(target);
        assertThat(event.getBrokerResult()).isEqualTo("broker-offset-3");
        assertThat(event.getResolvableType().getGeneric(0).resolve()).isEqualTo(OrderPlaced.class);
        assertThat(event.getResolvableType().getGeneric(1).resolve()).isEqualTo(String.class);
        assertThat(event).isEqualTo(equivalent);
        assertThat(event.hashCode()).isEqualTo(equivalent.hashCode());
    }

    @Test
    void publicationDefaultsWrapPayloadsAndExposeCompletionState() {
        Instant publishedAt = Instant.parse("2025-01-02T03:04:05Z");
        TestPublication incomplete = new TestPublication(
                "payload", publishedAt, Optional.empty(), Status.PUBLISHED, null, 0);
        TestPublication completed = new TestPublication(
                "payload", publishedAt.plusSeconds(1), Optional.of(publishedAt.plusSeconds(2)),
                Status.COMPLETED, publishedAt.plusSeconds(1), 1);

        ApplicationEvent applicationEvent = incomplete.getApplicationEvent();
        assertThat(applicationEvent).isInstanceOf(PayloadApplicationEvent.class);
        assertThat(((PayloadApplicationEvent<?>) applicationEvent).getPayload()).isEqualTo("payload");
        assertThat(incomplete.isCompleted()).isFalse();
        assertThat(completed.isCompleted()).isTrue();
        assertThat(incomplete.compareTo(completed)).isNegative();
        assertThat(completed.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(completed.getLastResubmissionDate()).isEqualTo(publishedAt.plusSeconds(1));
        assertThat(completed.getCompletionAttempts()).isEqualTo(1);

        PayloadApplicationEvent<String> existing = new PayloadApplicationEvent<>(this, "existing");
        TestPublication alreadyWrapped = new TestPublication(
                existing, publishedAt, Optional.empty(), Status.PUBLISHED, null, 0);
        assertThat(alreadyWrapped.getApplicationEvent()).isSameAs(existing);
    }

    @Test
    void resubmissionOptionsApplyImmutableLimitsAgeAndFilter() {
        TestPublication failed = new TestPublication(
                "failed", Instant.EPOCH, Optional.empty(), Status.FAILED, Instant.EPOCH, 2);
        TestPublication completed = new TestPublication(
                "completed", Instant.EPOCH, Optional.of(Instant.EPOCH), Status.COMPLETED, null, 1);

        ResubmissionOptions defaults = ResubmissionOptions.defaults();
        ResubmissionOptions configured = defaults
                .withMaxInFlight(4)
                .withBatchSize(25)
                .withMinAge(Duration.ofMinutes(10))
                .withFilter(publication -> publication.getStatus() == Status.FAILED);

        assertThat(defaults.getMaxInFlight()).isEqualTo(Integer.MAX_VALUE);
        assertThat(defaults.getBatchSize()).isEqualTo(100);
        assertThat(defaults.getMinAge()).isZero();
        assertThat(defaults.getFilter().test(completed)).isTrue();
        assertThat(configured.getMaxInFlight()).isEqualTo(4);
        assertThat(configured.getBatchSize()).isEqualTo(25);
        assertThat(configured.getMinAge()).isEqualTo(Duration.ofMinutes(10));
        assertThat(configured.getFilter().test(failed)).isTrue();
        assertThat(configured.getFilter().test(completed)).isFalse();
    }

    @Test
    void externalizationModeUsesEnvironmentPropertyAndDocumentedDefault() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);

        assertThat(ExternalizationMode.from(environment)).isEqualTo(ExternalizationMode.MODULE_LISTENER);

        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.<String, Object>of(
                ExternalizationMode.PROPERTY, "OUTBOX")));

        assertThat(ExternalizationMode.from(environment)).isEqualTo(ExternalizationMode.OUTBOX);
    }

    @Test
    void applicationModuleListenerComposesTransactionalAsyncAndEventListenerSemantics() {
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                ReadOnlyModuleListener.class, Transactional.class);
        EventListener eventListener = AnnotatedElementUtils.findMergedAnnotation(
                ReadOnlyModuleListener.class, EventListener.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.propagation()).isEqualTo(Propagation.SUPPORTS);
        assertThat(eventListener).isNotNull();
        assertThat(eventListener.id()).isEqualTo("read-only-listener");
        assertThat(eventListener.condition()).isEqualTo("#root.event.enabled");
        assertThat(AnnotatedElementUtils.hasAnnotation(ReadOnlyModuleListener.class, Async.class)).isTrue();
    }

    @Externalized("::#{#this.customerId}")
    public record FallbackExternalizedEvent(String customerId) {}

    public record UnannotatedEvent(String value) {}

    public record OrderPlaced(String orderId, String tenant) {}

    public record OutboundOrder(String orderId) {}

    public record PriorityEvent(String priority) {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Destination {
        String value();
    }

    @Destination("invoices::#{#this.invoiceId}")
    public record InvoiceIssued(String invoiceId) {}

    @Destination("shipments")
    public record ShipmentDispatched(String shipmentId) {}

    @ApplicationModuleListener(
            readOnlyTransaction = true,
            id = "read-only-listener",
            condition = "#root.event.enabled",
            propagation = Propagation.SUPPORTS)
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface ReadOnlyModuleListener {}

    private static final class TestPublication implements EventPublication {

        private static final UUID IDENTIFIER = UUID.fromString("ec9ea106-f73e-4d01-b752-15580fc8c73e");

        private final Object event;
        private final Instant publicationDate;
        private final Optional<Instant> completionDate;
        private final Status status;
        private final Instant lastResubmissionDate;
        private final int completionAttempts;

        private TestPublication(Object event, Instant publicationDate, Optional<Instant> completionDate,
                Status status, Instant lastResubmissionDate, int completionAttempts) {
            this.event = event;
            this.publicationDate = publicationDate;
            this.completionDate = completionDate;
            this.status = status;
            this.lastResubmissionDate = lastResubmissionDate;
            this.completionAttempts = completionAttempts;
        }

        @Override
        public UUID getIdentifier() {
            return IDENTIFIER;
        }

        @Override
        public Object getEvent() {
            return event;
        }

        @Override
        public Instant getPublicationDate() {
            return publicationDate;
        }

        @Override
        public Optional<Instant> getCompletionDate() {
            return completionDate;
        }

        @Override
        public Status getStatus() {
            return status;
        }

        @Override
        public Instant getLastResubmissionDate() {
            return lastResubmissionDate;
        }

        @Override
        public int getCompletionAttempts() {
            return completionAttempts;
        }
    }
}
