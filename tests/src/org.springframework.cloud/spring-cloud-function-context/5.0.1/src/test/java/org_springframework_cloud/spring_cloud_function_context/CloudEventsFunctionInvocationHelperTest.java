/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_function_context;

import java.net.URI;
import java.util.List;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.function.cloudevent.CloudEventsFunctionExtensionConfiguration;
import org.springframework.cloud.function.cloudevent.CloudEventsFunctionInvocationHelper;
import org.springframework.cloud.function.core.FunctionInvocationHelper;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class CloudEventsFunctionInvocationHelperTest {
    @Test
    void configurationCreatesCloudEventsInvocationHelper() {
        CloudEventsFunctionExtensionConfiguration configuration = new CloudEventsFunctionExtensionConfiguration();

        FunctionInvocationHelper<Message<?>> helper = configuration.nativeFunctionInvocationHelper(null);

        assertThat(helper).isInstanceOf(CloudEventsFunctionInvocationHelper.class);
        Message<String> partitionedMessage = MessageBuilder.withPayload("payload")
                .setHeader("partitionKey", "orders-0")
                .build();
        assertThat(helper.isRetainOutputAsMessage(partitionedMessage)).isTrue();
    }

    @Test
    void postProcessResultConvertsCloudEventWhenCloudEventsApiIsPresent() {
        CloudEventsFunctionExtensionConfiguration configuration = new CloudEventsFunctionExtensionConfiguration();
        CloudEventsFunctionInvocationHelper helper = (CloudEventsFunctionInvocationHelper) configuration
                .nativeFunctionInvocationHelper(null);
        Message<String> convertedMessage = MessageBuilder.withPayload("converted").build();
        helper.setMessageConverter(new CompositeMessageConverter(
                List.of(new CloudEventToMessageConverter(convertedMessage))));
        CloudEvent cloudEvent = CloudEventBuilder.v1()
                .withId("event-1")
                .withSource(URI.create("https://spring.io/events"))
                .withType("example.event")
                .build();

        Object result = helper.postProcessResult(cloudEvent, MessageBuilder.withPayload("input").build());

        assertThat(result).isSameAs(convertedMessage);
    }

    private static final class CloudEventToMessageConverter implements MessageConverter {
        private final Message<?> convertedMessage;

        private CloudEventToMessageConverter(Message<?> convertedMessage) {
            this.convertedMessage = convertedMessage;
        }

        @Override
        public Object fromMessage(Message<?> message, Class<?> targetClass) {
            return null;
        }

        @Override
        public Message<?> toMessage(Object payload, MessageHeaders headers) {
            return this.convertedMessage;
        }
    }
}
