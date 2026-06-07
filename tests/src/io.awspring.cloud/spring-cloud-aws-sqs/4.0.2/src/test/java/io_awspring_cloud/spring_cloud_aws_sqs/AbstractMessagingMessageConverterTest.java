/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_awspring_cloud.spring_cloud_aws_sqs;

import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.support.converter.AbstractMessagingMessageConverter;
import io.awspring.cloud.sqs.support.converter.HeaderMapper;
import io.awspring.cloud.sqs.support.converter.SimpleClassMatchingMessageConverter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractMessagingMessageConverterTest {

    @Test
    void convertsPayloadUsingDefaultTypeHeader() {
        TestMessagingMessageConverter converter = new TestMessagingMessageConverter();

        Message<?> message = converter.toMessagingMessage("native-image-friendly payload");

        assertThat(message.getPayload()).isEqualTo("native-image-friendly payload");
        assertThat(message.getHeaders().get(SqsHeaders.SQS_DEFAULT_TYPE_HEADER)).isEqualTo(String.class.getName());
    }
}

final class TestMessagingMessageConverter extends AbstractMessagingMessageConverter<String> {

    TestMessagingMessageConverter() {
        super(new SimpleClassMatchingMessageConverter());
    }

    @Override
    protected HeaderMapper<String> createDefaultHeaderMapper() {
        return new StaticTypeHeaderMapper();
    }

    @Override
    protected Object getPayloadToDeserialize(String message) {
        return message;
    }

    @Override
    protected String doConvertMessage(String messageWithHeaders, Object payload) {
        return (String) payload;
    }
}

final class StaticTypeHeaderMapper implements HeaderMapper<String> {

    @Override
    public String fromHeaders(MessageHeaders headers) {
        return "";
    }

    @Override
    public MessageHeaders toHeaders(String source) {
        return new MessageHeaders(Map.of(SqsHeaders.SQS_DEFAULT_TYPE_HEADER, String.class.getName()));
    }
}
