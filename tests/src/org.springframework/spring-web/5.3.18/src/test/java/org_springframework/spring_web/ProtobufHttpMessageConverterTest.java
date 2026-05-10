/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.google.protobuf.StringValue;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtobufHttpMessageConverterTest {
    @Test
    void readsBinaryMessageUsingProtobufBuilderLookup() throws IOException {
        StringValue expected = StringValue.of("spring-web converter");
        ProtobufHttpMessageConverter converter = new ProtobufHttpMessageConverter();
        SimpleHttpInputMessage inputMessage = new SimpleHttpInputMessage(expected.toByteArray());
        inputMessage.getHeaders().setContentType(ProtobufHttpMessageConverter.PROTOBUF);

        StringValue actual = (StringValue) converter.read(StringValue.class, inputMessage);

        assertThat(actual).isEqualTo(expected);
    }

    private static final class SimpleHttpInputMessage implements HttpInputMessage {
        private final HttpHeaders headers = new HttpHeaders();
        private final byte[] body;

        private SimpleHttpInputMessage(byte[] body) {
            this.body = body;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(this.body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.headers;
        }
    }
}
