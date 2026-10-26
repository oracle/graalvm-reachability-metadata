/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.kylin.jdbc.shaded.com.google.protobuf.CodedOutputStream;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.GeneratedMessageV3;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.Message;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.Message.Builder;
import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.proto.Requests.OpenConnectionRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneratedMessageLiteInnerSerializedFormTest {
    @Test
    void restoresMessageUsingCurrentDefaultInstanceField() throws Exception {
        OpenConnectionRequest request = request("current-connection");

        Object restored = serializeAndRestore(request);

        assertThat(restored).isEqualTo(request);
    }

    @Test
    void restoresMessageUsingLegacyDefaultInstanceField() throws Exception {
        OpenConnectionRequest request = request("legacy-connection");

        Object restored = serializeAndRestore(new LegacyMessage(request));

        assertThat(restored).isEqualTo(request);
    }

    private static OpenConnectionRequest request(String connectionId) {
        return OpenConnectionRequest.newBuilder()
                .setConnectionId(connectionId)
                .putInfo("role", "analyst")
                .build();
    }

    private static Object serializeAndRestore(Object message) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(message);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return input.readObject();
        }
    }

    public static final class LegacyMessage extends GeneratedMessageV3 {
        private static final long serialVersionUID = 1L;
        private static final Message defaultInstance = OpenConnectionRequest.getDefaultInstance();

        private final OpenConnectionRequest delegate;

        private LegacyMessage(OpenConnectionRequest delegate) {
            this.delegate = delegate;
        }

        @Override
        public void writeTo(CodedOutputStream output) throws IOException {
            delegate.writeTo(output);
        }

        @Override
        public int getSerializedSize() {
            return delegate.getSerializedSize();
        }

        @Override
        public Builder newBuilderForType() {
            return delegate.newBuilderForType();
        }

        @Override
        public Builder toBuilder() {
            return delegate.toBuilder();
        }

        @Override
        public Message getDefaultInstanceForType() {
            return delegate.getDefaultInstanceForType();
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Builder newBuilderForType(BuilderParent parent) {
            return delegate.newBuilderForType();
        }
    }
}
