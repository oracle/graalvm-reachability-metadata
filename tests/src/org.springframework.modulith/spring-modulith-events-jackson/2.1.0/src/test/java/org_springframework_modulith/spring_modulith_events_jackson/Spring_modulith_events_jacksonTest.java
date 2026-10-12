/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_events_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.core.EventSerializer;

import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

public class Spring_modulith_events_jacksonTest {
    @Test
    void autoConfiguresSerializerWithDefaultMapper() {
        try (ConfigurableApplicationContext context = start(DefaultMapperApplication.class)) {
            EventSerializer serializer = context.getBean(EventSerializer.class);
            OrderPlaced event = new OrderPlaced("order-17", List.of("book", "pen"));

            Object serialized = serializer.serialize(event);
            OrderPlaced restored = serializer.deserialize(serialized, OrderPlaced.class);

            assertThat(serialized)
                    .isInstanceOf(String.class)
                    .asString()
                    .contains("\"orderId\":\"order-17\"")
                    .contains("\"items\":[\"book\",\"pen\"]");
            assertThat(restored.getOrderId()).isEqualTo("order-17");
            assertThat(restored.getItems()).containsExactly("book", "pen");
            assertThat(serializer.serialize(new MarkerEvent())).isEqualTo("{}");
        }
    }

    @Test
    void registersJacksonModulesWithDefaultMapper() {
        try (ConfigurableApplicationContext context = start(ModuleApplication.class)) {
            EventSerializer serializer = context.getBean(EventSerializer.class);

            ShippingInstruction restored = serializer.deserialize(
                    "{\"destination\":\"Berlin\",\"packageCount\":3}", ShippingInstruction.class);
            Object serialized = serializer.serialize(restored);

            assertThat(restored).isInstanceOf(StandardShippingInstruction.class);
            assertThat(restored.getDestination()).isEqualTo("Berlin");
            assertThat(restored.getPackageCount()).isEqualTo(3);
            assertThat(serialized.toString())
                    .contains("\"destination\":\"Berlin\"")
                    .contains("\"packageCount\":3");
        }
    }

    @Test
    void usesApplicationProvidedJsonMapper() {
        try (ConfigurableApplicationContext context = start(CustomMapperApplication.class)) {
            EventSerializer serializer = context.getBean(EventSerializer.class);

            Object serialized = serializer.serialize(new CustomerRenamed("Ada Lovelace"));
            CustomerRenamed restored = serializer.deserialize(
                    "{\"first_name\":\"Grace Hopper\"}", CustomerRenamed.class);

            assertThat(serialized).isEqualTo("{\"first_name\":\"Ada Lovelace\"}");
            assertThat(restored.getFirstName()).isEqualTo("Grace Hopper");
        }
    }

    @Test
    void backsOffForApplicationProvidedEventSerializer() {
        try (ConfigurableApplicationContext context = start(CustomSerializerApplication.class)) {
            EventSerializer serializer = context.getBean(EventSerializer.class);

            Object serialized = serializer.serialize("customer-41");
            String restored = serializer.deserialize(serialized, String.class);

            assertThat(serializer).isInstanceOf(ApplicationEventSerializer.class);
            assertThat(context.getBeansOfType(EventSerializer.class)).hasSize(1);
            assertThat(serialized).isEqualTo("customer-41");
            assertThat(restored).isEqualTo("customer-41");
        }
    }

    private ConfigurableApplicationContext start(Class<?> applicationClass) {
        return ApplicationContexts.start(applicationClass);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableJacksonEventSerialization
    public static class DefaultMapperApplication {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableJacksonEventSerialization
    public static class ModuleApplication {
        @Bean
        public JacksonModule shippingModule() {
            return new SimpleModule("shipping-module")
                    .addAbstractTypeMapping(ShippingInstruction.class, StandardShippingInstruction.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableJacksonEventSerialization
    public static class CustomMapperApplication {
        @Bean
        public JsonMapper jsonMapper() {
            return JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableJacksonEventSerialization
    public static class CustomSerializerApplication {
        @Bean
        public EventSerializer applicationEventSerializer() {
            return new ApplicationEventSerializer();
        }
    }

    public static final class ApplicationEventSerializer implements EventSerializer {
        @Override
        public Object serialize(Object event) {
            return event;
        }

        @Override
        public <T> T deserialize(Object serialized, Class<T> type) {
            return type.cast(serialized);
        }
    }

    public static final class MarkerEvent {
    }

    public static final class OrderPlaced {
        private String orderId;
        private List<String> items;

        public OrderPlaced() {
        }

        public OrderPlaced(String orderId, List<String> items) {
            this.orderId = orderId;
            this.items = items;
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public List<String> getItems() {
            return items;
        }

        public void setItems(List<String> items) {
            this.items = items;
        }
    }

    public interface ShippingInstruction {
        String getDestination();

        int getPackageCount();
    }

    public static final class StandardShippingInstruction implements ShippingInstruction {
        private String destination;
        private int packageCount;

        public StandardShippingInstruction() {
        }

        @Override
        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        @Override
        public int getPackageCount() {
            return packageCount;
        }

        public void setPackageCount(int packageCount) {
            this.packageCount = packageCount;
        }
    }

    public static final class CustomerRenamed {
        private String firstName;

        public CustomerRenamed() {
        }

        public CustomerRenamed(String firstName) {
            this.firstName = firstName;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }
    }
}
