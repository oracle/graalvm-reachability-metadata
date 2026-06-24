/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_awspring_cloud.spring_cloud_aws_sqs;

import io.awspring.cloud.sqs.support.converter.SqsHeaderMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageHeaders;
import software.amazon.awssdk.services.sqs.model.Message;

import static org.assertj.core.api.Assertions.assertThat;

public class SqsHeaderMapperInnerNumberParserTest {

    @Test
    void mapsTypedNumberAttributesBackToOriginalHeaderType() {
        SqsHeaderMapper mapper = new SqsHeaderMapper();
        String headerName = "deliveryAttempt";

        Message sqsMessage = mapper.fromHeaders(new MessageHeaders(Map.of(headerName, 42)));
        MessageHeaders mappedHeaders = mapper.toHeaders(sqsMessage);

        assertThat(sqsMessage.messageAttributes().get(headerName).dataType()).isEqualTo("Number.java.lang.Integer");
        assertThat(mappedHeaders.get(headerName)).isEqualTo(42);
    }
}
