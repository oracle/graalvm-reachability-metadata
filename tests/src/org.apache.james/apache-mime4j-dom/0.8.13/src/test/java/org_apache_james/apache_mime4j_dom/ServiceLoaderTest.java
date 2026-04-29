/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_dom;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.MessageBuilder;
import org.apache.james.mime4j.dom.MessageServiceFactory;
import org.apache.james.mime4j.dom.MessageWriter;
import org.junit.jupiter.api.Test;

public class ServiceLoaderTest {
    @Test
    void messageServiceFactoryNewInstanceLoadsBundledProvider() throws Exception {
        MessageServiceFactory factory = MessageServiceFactory.newInstance();

        assertThat(factory).isNotNull();
        assertThat(factory.getClass().getName())
                .isEqualTo("org.apache.james.mime4j.message.MessageServiceFactoryImpl");

        MessageBuilder builder = factory.newMessageBuilder();
        MessageWriter writer = factory.newMessageWriter();
        String source = "Subject: service provider smoke test\r\n"
                + "From: sender@example.com\r\n"
                + "To: receiver@example.com\r\n"
                + "\r\n"
                + "Body text";
        Message message = builder.parseMessage(
                new ByteArrayInputStream(source.getBytes(StandardCharsets.US_ASCII)));

        assertThat(message.getSubject()).isEqualTo("service provider smoke test");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.writeMessage(message, output);

        assertThat(new String(output.toByteArray(), StandardCharsets.US_ASCII))
                .contains("Subject: service provider smoke test");
    }
}
