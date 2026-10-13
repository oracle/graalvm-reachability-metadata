/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jmolecules.jmolecules_events;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jmolecules.event.annotation.DomainEvent;
import org.jmolecules.event.annotation.DomainEventHandler;
import org.jmolecules.event.annotation.DomainEventPublisher;
import org.jmolecules.event.annotation.DomainEventPublisher.PublisherType;
import org.jmolecules.event.annotation.Externalized;
import org.jmolecules.stereotype.Stereotype;
import org.junit.jupiter.api.Test;

@SuppressWarnings("checkstyle:annotationAccess")
public class Jmolecules_eventsTest {
    @Test
    void markerInterfacesCarryEventsThroughConsumerCode() {
        OrderPlaced event = new OrderPlaced("order-42", 3);
        org.jmolecules.event.types.DomainEvent domainEvent = event;
        org.jmolecules.event.types.Externalized externalized = event;

        EventJournal journal = new EventJournal();
        journal.append(domainEvent);

        assertThat(journal.events()).containsExactly(event);
        assertThat(externalized).isSameAs(event);
        assertThat(event.summary()).isEqualTo("order-42:3");
    }

    @Test
    void eventAnnotationsDescribeExplicitAndConventionBasedEvents() {
        DomainEvent orderDescriptor = OrderPlaced.class.getAnnotation(DomainEvent.class);
        Externalized orderTarget = OrderPlaced.class.getAnnotation(Externalized.class);
        DomainEvent inventoryDescriptor = InventoryAdjusted.class.getAnnotation(DomainEvent.class);
        Externalized inventoryTarget = InventoryAdjusted.class.getAnnotation(Externalized.class);

        assertThat(orderDescriptor.namespace()).isEqualTo("sales");
        assertThat(orderDescriptor.name()).isEqualTo("OrderPlaced");
        assertThat(orderTarget.value()).isEmpty();
        assertThat(orderTarget.target()).isEqualTo("orders");

        assertThat(inventoryDescriptor.namespace()).isEmpty();
        assertThat(inventoryDescriptor.name()).isEmpty();
        assertThat(inventoryTarget.value()).isEqualTo("inventory");
        assertThat(inventoryTarget.target()).isEmpty();
        assertThat(new InventoryAdjusted("SKU-7", -2).applyTo(10)).isEqualTo(8);
    }

    @Test
    void handlerAnnotationsLinkMethodsToSpecificAndConventionalEvents() throws NoSuchMethodException {
        EventProjection projection = new EventProjection();
        OrderPlaced event = new OrderPlaced("order-7", 2);
        Method explicitHandler = EventProjection.class.getDeclaredMethod("on", OrderPlaced.class);
        Method conventionalHandler = EventProjection.class.getDeclaredMethod("on", InventoryAdjusted.class);

        DomainEventHandler explicitDescriptor = explicitHandler.getAnnotation(DomainEventHandler.class);
        DomainEventHandler conventionalDescriptor = conventionalHandler.getAnnotation(DomainEventHandler.class);

        assertThat(explicitDescriptor.namespace()).isEqualTo("sales");
        assertThat(explicitDescriptor.name()).isEqualTo("OrderPlaced");
        assertThat(conventionalDescriptor.namespace()).isEmpty();
        assertThat(conventionalDescriptor.name()).isEmpty();
        assertThat(projection.on(event)).isEqualTo("projected order-7:2");
        assertThat(projection.on(new InventoryAdjusted("SKU-7", 4))).isEqualTo("adjusted SKU-7 by 4");
    }

    @Test
    void publisherAnnotationsDescribeInternalExternalAndUnspecifiedPublishers() throws NoSuchMethodException {
        EventPublisher publisher = new EventPublisher();
        OrderPlaced event = new OrderPlaced("order-9", 1);
        DomainEventPublisher external = publisherAnnotation("publishExternally", OrderPlaced.class);
        DomainEventPublisher internal = publisherAnnotation("publishInternally", InventoryAdjusted.class);
        DomainEventPublisher unspecified = publisherAnnotation("publish", Object.class);

        assertThat(external.publishes()).isEqualTo("sales.OrderPlaced");
        assertThat(external.type()).isEqualTo(PublisherType.EXTERNAL);
        assertThat(internal.publishes()).isEqualTo("InventoryAdjusted");
        assertThat(internal.type()).isEqualTo(PublisherType.INTERNAL);
        assertThat(unspecified.publishes()).isEmpty();
        assertThat(unspecified.type()).isEqualTo(PublisherType.UNDEFINED);

        assertThat(publisher.publishExternally(event)).isEqualTo("external:sales.OrderPlaced:order-9:1");
        assertThat(publisher.publishInternally(new InventoryAdjusted("SKU-2", 5)))
                .isEqualTo("internal:SKU-2:5");
        assertThat(publisher.publish("audit-entry")).isEqualTo("event:audit-entry");
    }

    @Test
    void handlerAndPublisherCanBeUsedAsComposedAnnotations() throws NoSuchMethodException {
        Method handler = ComposedEventEndpoints.class.getDeclaredMethod("handle", OrderPlaced.class);
        Method publisher = ComposedEventEndpoints.class.getDeclaredMethod("publish", OrderPlaced.class);
        DomainEventHandler handlerDescriptor = HandlesSalesEvents.class.getAnnotation(DomainEventHandler.class);
        DomainEventPublisher publisherDescriptor = PublishesSalesEvents.class.getAnnotation(DomainEventPublisher.class);
        ComposedEventEndpoints endpoints = new ComposedEventEndpoints();
        OrderPlaced event = new OrderPlaced("order-11", 6);

        assertThat(handler.isAnnotationPresent(HandlesSalesEvents.class)).isTrue();
        assertThat(handlerDescriptor.namespace()).isEqualTo("sales");
        assertThat(handlerDescriptor.name()).isEqualTo("*");
        assertThat(publisher.isAnnotationPresent(PublishesSalesEvents.class)).isTrue();
        assertThat(publisherDescriptor.publishes()).isEqualTo("sales.*");
        assertThat(publisherDescriptor.type()).isEqualTo(PublisherType.EXTERNAL);
        assertThat(endpoints.handle(event)).isEqualTo("handled:order-11");
        assertThat(endpoints.publish(event)).isEqualTo("published:order-11");
    }

    @Test
    void stereotypeCatalogDescribesEventConceptsForExternalTools() throws IOException {
        try (InputStream input = DomainEvent.class.getResourceAsStream("/META-INF/jmolecules-stereotypes.json")) {
            assertThat(input).isNotNull();

            String catalog = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(catalog)
                    .contains(
                            "\"events.DomainEvent\"",
                            "\"@org.jmolecules.event.annotation.DomainEvent\"",
                            "\"org.jmolecules.event.types.DomainEvent\"")
                    .contains(
                            "\"events.DomainEventHandler\"",
                            "\"@org.jmolecules.event.annotation.DomainEventHandler\"")
                    .contains(
                            "\"events.Externalized\"",
                            "\"@org.jmolecules.event.annotation.Externalized\"",
                            "\"org.jmolecules.event.types.Externalized\"")
                    .contains("\"events\"", "\"displayName\" : \"Events\"", "\"type\" : \"design\"");
        }
    }

    @Test
    void eventConceptsExposeRuntimeStereotypeContracts() {
        List<Class<?>> eventConcepts = List.of(
                DomainEvent.class,
                DomainEventHandler.class,
                DomainEventPublisher.class,
                Externalized.class,
                org.jmolecules.event.types.DomainEvent.class,
                org.jmolecules.event.types.Externalized.class);

        assertThat(eventConcepts).allSatisfy(type -> assertThat(type.isAnnotationPresent(Stereotype.class)).isTrue());
        assertRuntimeAnnotation(DomainEvent.class, ElementType.TYPE);
        assertRuntimeAnnotation(Externalized.class, ElementType.TYPE);
        assertRuntimeAnnotation(DomainEventHandler.class, ElementType.METHOD, ElementType.ANNOTATION_TYPE);
        assertRuntimeAnnotation(DomainEventPublisher.class, ElementType.METHOD, ElementType.ANNOTATION_TYPE);
    }

    private static DomainEventPublisher publisherAnnotation(String methodName, Class<?> parameterType)
            throws NoSuchMethodException {
        return EventPublisher.class
                .getDeclaredMethod(methodName, parameterType)
                .getAnnotation(DomainEventPublisher.class);
    }

    private static void assertRuntimeAnnotation(
            Class<? extends Annotation> annotationType, ElementType... supportedTargets) {
        Retention retention = annotationType.getAnnotation(Retention.class);
        Target target = annotationType.getAnnotation(Target.class);

        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(target.value()).containsExactlyInAnyOrder(supportedTargets);
        assertThat(annotationType.isAnnotationPresent(Documented.class)).isTrue();
    }

    @DomainEvent(namespace = "sales", name = "OrderPlaced")
    @Externalized(target = "orders")
    private record OrderPlaced(String orderId, int lineCount)
            implements org.jmolecules.event.types.DomainEvent, org.jmolecules.event.types.Externalized {
        private String summary() {
            return orderId + ":" + lineCount;
        }
    }

    @DomainEvent
    @Externalized("inventory")
    private record InventoryAdjusted(String sku, int delta) {
        private int applyTo(int currentQuantity) {
            return currentQuantity + delta;
        }
    }

    private static final class EventJournal {
        private final List<org.jmolecules.event.types.DomainEvent> events = new ArrayList<>();

        private void append(org.jmolecules.event.types.DomainEvent event) {
            events.add(event);
        }

        private List<org.jmolecules.event.types.DomainEvent> events() {
            return List.copyOf(events);
        }
    }

    private static final class EventProjection {
        @DomainEventHandler(namespace = "sales", name = "OrderPlaced")
        private String on(OrderPlaced event) {
            return "projected " + event.summary();
        }

        @DomainEventHandler
        private String on(InventoryAdjusted event) {
            return "adjusted " + event.sku() + " by " + event.delta();
        }
    }

    private static final class EventPublisher {
        @DomainEventPublisher(publishes = "sales.OrderPlaced", type = PublisherType.EXTERNAL)
        private String publishExternally(OrderPlaced event) {
            return "external:sales.OrderPlaced:" + event.summary();
        }

        @DomainEventPublisher(publishes = "InventoryAdjusted", type = PublisherType.INTERNAL)
        private String publishInternally(InventoryAdjusted event) {
            return "internal:" + event.sku() + ":" + event.delta();
        }

        @DomainEventPublisher
        private String publish(Object event) {
            return "event:" + event;
        }
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @DomainEventHandler(namespace = "sales", name = "*")
    private @interface HandlesSalesEvents {}

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @DomainEventPublisher(publishes = "sales.*", type = PublisherType.EXTERNAL)
    private @interface PublishesSalesEvents {}

    private static final class ComposedEventEndpoints {
        @HandlesSalesEvents
        private String handle(OrderPlaced event) {
            return "handled:" + event.orderId();
        }

        @PublishesSalesEvents
        private String publish(OrderPlaced event) {
            return "published:" + event.orderId();
        }
    }
}
