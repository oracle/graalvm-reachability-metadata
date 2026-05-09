/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro.avro_ipc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.avro.Protocol;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.avro.specific.SpecificData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SpecificRequestorTest {
    @Test
    void getClientCreatesProxyThatReportsProtocolAndRemoteName() throws Exception {
        NamedTransceiver transceiver = new NamedTransceiver("specific-requestor-remote");

        AvroIpcGreetingService client = SpecificRequestor.getClient(AvroIpcGreetingService.class, transceiver);

        assertThat(client.toString())
                .contains("AvroIpcGreetingService")
                .contains("specific-requestor-remote");
    }

    @Test
    void getClientWithExistingRequestorCreatesProxyUsingRequestorClassLoader() throws Exception {
        NamedTransceiver transceiver = new NamedTransceiver("requestor-instance-remote");
        SpecificData data = new SpecificData(AvroIpcGreetingService.class.getClassLoader());
        SpecificRequestor requestor = new SpecificRequestor(AvroIpcGreetingService.PROTOCOL, transceiver, data);

        AvroIpcGreetingService client = SpecificRequestor.getClient(AvroIpcGreetingService.class, requestor);

        assertThat(client.toString())
                .contains("AvroIpcGreetingService")
                .contains("requestor-instance-remote");
    }

    public interface AvroIpcGreetingService {
        Protocol PROTOCOL = Protocol.parse("""
                {
                  "protocol": "AvroIpcGreetingService",
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

        CharSequence greet(CharSequence name) throws IOException;
    }

    private static final class NamedTransceiver extends Transceiver {
        private final String remoteName;

        private NamedTransceiver(String remoteName) {
            this.remoteName = remoteName;
        }

        @Override
        public String getRemoteName() {
            return remoteName;
        }

        @Override
        public List<ByteBuffer> readBuffers() throws IOException {
            throw new UnsupportedOperationException("This test does not perform RPC I/O");
        }

        @Override
        public void writeBuffers(List<ByteBuffer> buffers) throws IOException {
            throw new UnsupportedOperationException("This test does not perform RPC I/O");
        }
    }
}
