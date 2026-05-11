/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.httpclient.methods.GetMethod;
import org.jets3t.service.impl.rest.httpclient.HttpMethodReleaseInputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpMethodReleaseInputStreamTest {
    @Test
    public void releasesHttpMethodWhenResponseStreamIsConsumed() throws IOException {
        TrackingGetMethod method = new TrackingGetMethod("payload");

        HttpMethodReleaseInputStream stream = new HttpMethodReleaseInputStream(method);
        byte[] buffer = new byte[8];

        assertThat(stream.getHttpMethod()).isSameAs(method);
        assertThat(stream.read(buffer, 0, buffer.length)).isEqualTo("payload".length());
        String responseBody = new String(buffer, 0, "payload".length(), StandardCharsets.UTF_8);
        assertThat(responseBody).isEqualTo("payload");
        assertThat(stream.read(buffer, 0, buffer.length)).isEqualTo(-1);

        assertThat(method.wasReleased()).isTrue();
        assertThat(method.wasAborted()).isFalse();
    }

    @Test
    public void abortsHttpMethodWhenStreamIsClosedBeforeConsumption() throws IOException {
        TrackingGetMethod method = new TrackingGetMethod("payload");

        HttpMethodReleaseInputStream stream = new HttpMethodReleaseInputStream(method);
        assertThat(stream.getWrappedInputStream()).isNotNull();
        stream.close();

        assertThat(method.wasReleased()).isTrue();
        assertThat(method.wasAborted()).isTrue();
    }

    private static final class TrackingGetMethod extends GetMethod {
        private final InputStream responseStream;
        private boolean released;
        private boolean aborted;

        private TrackingGetMethod(String responseBody) {
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            this.responseStream = new ByteArrayInputStream(responseBytes);
        }

        @Override
        public InputStream getResponseBodyAsStream() {
            return responseStream;
        }

        @Override
        public void releaseConnection() {
            released = true;
        }

        @Override
        public void abort() {
            aborted = true;
        }

        private boolean wasReleased() {
            return released;
        }

        private boolean wasAborted() {
            return aborted;
        }
    }
}
