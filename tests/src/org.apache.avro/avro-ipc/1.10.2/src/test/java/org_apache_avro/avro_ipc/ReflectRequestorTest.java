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

import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.reflect.ReflectRequestor;
import org.apache.avro.reflect.ReflectData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectRequestorTest {
    @Test
    void getClientWithReflectDataCreatesProxyThatReportsProtocolAndRemoteName() throws Exception {
        NamedTransceiver transceiver = new NamedTransceiver("reflect-requestor-remote");
        ReflectData reflectData = new ReflectData(AvroIpcReflectGreetingService.class.getClassLoader());

        AvroIpcReflectGreetingService client = ReflectRequestor.getClient(
                AvroIpcReflectGreetingService.class,
                transceiver,
                reflectData);

        assertThat(client.toString())
                .contains("AvroIpcReflectGreetingService")
                .contains("reflect-requestor-remote");
    }

    @Test
    void getClientWithExistingReflectRequestorCreatesProxyUsingRequestorClassLoader() throws Exception {
        NamedTransceiver transceiver = new NamedTransceiver("reflect-requestor-instance-remote");
        ReflectData reflectData = new ReflectData(AvroIpcReflectGreetingService.class.getClassLoader());
        ReflectRequestor requestor = new ReflectRequestor(
                AvroIpcReflectGreetingService.class,
                transceiver,
                reflectData);

        AvroIpcReflectGreetingService client = ReflectRequestor.getClient(
                AvroIpcReflectGreetingService.class,
                requestor);

        assertThat(client.toString())
                .contains("AvroIpcReflectGreetingService")
                .contains("reflect-requestor-instance-remote");
    }

    public interface AvroIpcReflectGreetingService {
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
