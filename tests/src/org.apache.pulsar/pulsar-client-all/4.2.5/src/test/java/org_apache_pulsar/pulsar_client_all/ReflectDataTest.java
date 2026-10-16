/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.avro.Protocol;
import org.apache.pulsar.shade.org.apache.avro.Schema;
import org.apache.pulsar.shade.org.apache.avro.reflect.ReflectData;
import org.junit.jupiter.api.Test;

public class ReflectDataTest {
    @Test
    void derivesRecordArrayAndProtocolSchemasFromJavaTypes() {
        final ReflectData data = ReflectData.AllowNull.get();
        final Schema messageSchema = data.getSchema(Message.class);
        final Schema arraySchema = data.getSchema(Message[].class);
        final Protocol protocol = data.getProtocol(EchoService.class);

        assertThat(messageSchema.getFields()).extracting(Schema.Field::name).contains("id", "payload");
        assertThat(data.getClass(arraySchema)).isEqualTo(Message[].class);
        assertThat(protocol.getMessages()).containsKey("echo");
    }

    public interface EchoService {
        Message echo(Message message);
    }

    public static final class Message {
        private long id;
        private String payload;

        public Message() {
        }
    }
}
