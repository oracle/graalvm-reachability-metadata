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

import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtobufHttpMessageConverterTest {

    @Test
    void readsProtobufMessageWithPublicConverterApi() throws Exception {
        ProtobufHttpMessageConverter converter = new ProtobufHttpMessageConverter();
        StringValue value = StringValue.of("spring-web converter");
        HttpInputMessage inputMessage = new ByteArrayHttpInputMessage(value.toByteArray());

        Message read = converter.read(StringValue.class, inputMessage);

        assertThat(read).isEqualTo(value);
    }

    private static final class ByteArrayHttpInputMessage implements HttpInputMessage {

        private final HttpHeaders headers = new HttpHeaders();

        private final byte[] body;

        private ByteArrayHttpInputMessage(byte[] body) {
            this.headers.setContentType(ProtobufHttpMessageConverter.PROTOBUF);
            this.body = body;
        }

        @Override
        public InputStream getBody() throws IOException {
            return new ByteArrayInputStream(this.body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.headers;
        }
    }
}
