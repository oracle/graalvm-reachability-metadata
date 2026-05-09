/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro.avro_ipc;

import org.apache.avro.Protocol;
import org.apache.avro.Protocol.Message;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.ipc.specific.SpecificResponder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SpecificResponderTest {
    @Test
    void respondInvokesMatchingImplementationMethod() throws Exception {
        GreetingEndpoint endpoint = new GreetingEndpoint();
        SpecificResponder responder = new SpecificResponder(GreetingService.PROTOCOL, endpoint);
        Message message = GreetingService.PROTOCOL.getMessages().get("greet");
        GenericRecord request = new GenericData.Record(message.getRequest());
        request.put("name", "native-image");

        Object response = responder.respond(message, request);

        assertThat(response).isEqualTo("Hello, native-image");
    }

    public interface GreetingService {
        Protocol PROTOCOL = Protocol.parse("""
                {
                  "protocol": "GreetingService",
                  "namespace": "org_apache_avro.avro_ipc",
                  "messages": {
                    "greet": {
                      "request": [
                        {"name": "name", "type": "string"}
                      ],
                      "response": "string"
                    }
                  }
                }
                """);
    }

    public static final class GreetingEndpoint {
        public CharSequence greet(CharSequence name) {
            return "Hello, " + name;
        }
    }
}
