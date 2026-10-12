/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_events_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.modulith.events.config.EventExternalizationAutoConfiguration;
import org.springframework.modulith.events.config.EventPublicationAutoConfiguration;
import org.springframework.modulith.events.core.EventSerializer;

public class Jackson2EventSerializerTest {
    @Test
    void autoConfiguresJackson2CompatibilitySerializer() {
        try (ConfigurableApplicationContext context = SpringApplication.run(Jackson2Application.class,
                "--spring.main.web-application-type=none",
                "--spring.main.banner-mode=off",
                "--logging.level.root=OFF")) {
            EventSerializer serializer = context.getBean(EventSerializer.class);
            LegacyOrder event = new LegacyOrder("legacy-23", true);

            Object serialized = serializer.serialize(event);
            LegacyOrder restored = serializer.deserialize(serialized, LegacyOrder.class);

            assertThat(serialized)
                    .isInstanceOf(String.class)
                    .asString()
                    .contains("\"orderId\":\"legacy-23\"")
                    .contains("\"expedited\":true");
            assertThat(restored.getOrderId()).isEqualTo("legacy-23");
            assertThat(restored.isExpedited()).isTrue();
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(
            exclude = {
                    EventExternalizationAutoConfiguration.class,
                    EventPublicationAutoConfiguration.class
            },
            excludeName = "org.springframework.modulith.events.jackson.JacksonEventSerializationConfiguration")
    public static class Jackson2Application {
    }

    public static final class LegacyOrder {
        private String orderId;
        private boolean expedited;

        public LegacyOrder() {
        }

        public LegacyOrder(String orderId, boolean expedited) {
            this.orderId = orderId;
            this.expedited = expedited;
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public boolean isExpedited() {
            return expedited;
        }

        public void setExpedited(boolean expedited) {
            this.expedited = expedited;
        }
    }
}
