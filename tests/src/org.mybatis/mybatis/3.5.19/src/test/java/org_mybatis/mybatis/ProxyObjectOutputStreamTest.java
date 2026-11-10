/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;

import org.apache.ibatis.javassist.util.proxy.MethodHandler;
import org.apache.ibatis.javassist.util.proxy.ProxyObject;
import org.apache.ibatis.javassist.util.proxy.ProxyObjectOutputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ProxyObjectOutputStreamTest {
    @Test
    void writesProxySuperclassInterfacesAndFilterAlongsideObjectState() throws Exception {
        MessageProxy original = new MessageProxy();
        original.setText("Hello, Ada");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ProxyObjectOutputStream output = new ProxyObjectOutputStream(bytes)) {
            output.writeObject(original);
            output.writeObject(original);
            output.writeObject("end of messages");
        }

        try (CompiledProxyInputStream input = new CompiledProxyInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            MessageProxy restored = assertInstanceOf(MessageProxy.class, input.readObject());
            assertNotSame(original, restored);
            assertEquals("Hello, Ada", restored.text());
            assertNull(restored.getHandler());
            assertEquals(restored, input.readObject());
            assertEquals("end of messages", input.readObject());
            assertEquals(1, input.proxyDescriptors);
        }
    }

    public interface MessageView {
        String text();
    }

    public static class Message implements Serializable {
        private static final long serialVersionUID = 1L;
        private String text;

        public String text() {
            return text;
        }

        public void setText(String value) {
            text = value;
        }
    }

    public static class MessageProxy extends Message implements MessageView, ProxyObject {
        private static final long serialVersionUID = 1L;
        @SuppressWarnings("checkstyle:StaticVariableName") // Javassist reads this field by name.
        public static byte[] _filter_signature = new byte[]{0, 0};
        private MethodHandler handler;

        @Override
        public void setHandler(MethodHandler value) {
            handler = value;
        }

        @Override
        public MethodHandler getHandler() {
            return handler;
        }
    }

    /// Reads the proxy wire format using an already compiled class instead of generating a new one.
    private static class CompiledProxyInputStream extends ObjectInputStream {
        private int proxyDescriptors;

        CompiledProxyInputStream(InputStream input) throws IOException {
            super(input);
        }

        @Override
        protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
            if (!readBoolean()) {
                return super.readClassDescriptor();
            }
            proxyDescriptors++;
            assertEquals(Message.class.getName(), readObject());
            assertEquals(1, readInt());
            assertEquals(MessageView.class.getName(), readObject());
            int signatureLength = readInt();
            byte[] signature = new byte[signatureLength];
            readFully(signature);
            assertArrayEquals(MessageProxy._filter_signature, signature);
            return ObjectStreamClass.lookup(MessageProxy.class);
        }
    }
}
