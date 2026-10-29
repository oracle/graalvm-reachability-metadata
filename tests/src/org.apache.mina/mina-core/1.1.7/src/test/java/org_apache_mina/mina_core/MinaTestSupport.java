/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_mina.mina_core;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.ThreadModel;
import org.apache.mina.common.WriteFuture;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.apache.mina.filter.codec.demux.MessageDecoderAdapter;
import org.apache.mina.filter.codec.demux.MessageDecoderResult;
import org.apache.mina.filter.codec.demux.MessageEncoder;
import org.apache.mina.transport.vmpipe.VmPipeAcceptor;
import org.apache.mina.transport.vmpipe.VmPipeConnector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class MinaTestSupport {
    private static final long IO_TIMEOUT_MILLIS = 30000;

    private MinaTestSupport() {
    }

    static void assertTransfer(ProtocolCodecFilter receiverFilter, ProtocolCodecFilter senderFilter,
            Object message, Object expected) throws Exception {
        CompletableFuture<Object> received = new CompletableFuture<>();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        IoHandlerAdapter handler = new IoHandlerAdapter() {
            @Override
            public void messageReceived(IoSession session, Object value) {
                received.complete(value);
            }

            @Override
            public void exceptionCaught(IoSession session, Throwable cause) {
                failures.add(cause);
                received.completeExceptionally(cause);
            }
        };
        VmPipeAcceptor acceptor = new VmPipeAcceptor();
        VmPipeConnector connector = new VmPipeConnector();
        acceptor.getDefaultConfig().setThreadModel(ThreadModel.MANUAL);
        connector.getDefaultConfig().setThreadModel(ThreadModel.MANUAL);
        acceptor.getFilterChain().addLast("codec", receiverFilter);
        if (senderFilter != null) {
            connector.getFilterChain().addLast("codec", senderFilter);
        }

        IoSession session = null;
        try {
            acceptor.bind(null, handler);
            ConnectFuture connection = connector.connect(
                    acceptor.getManagedServiceAddresses().iterator().next(), handler);
            assertTrue(connection.join(IO_TIMEOUT_MILLIS), "Connection did not complete");
            session = connection.getSession();
            WriteFuture write = session.write(message);
            assertTrue(write.join(IO_TIMEOUT_MILLIS), "Write did not complete");
            assertTrue(write.isWritten(), "Message was not written");
            assertEquals(expected, received.get(30, TimeUnit.SECONDS));
        } finally {
            try {
                if (session != null) {
                    assertTrue(session.close().join(IO_TIMEOUT_MILLIS), "Session did not close");
                }
            } finally {
                acceptor.unbindAll();
            }
        }
        assertTrue(failures.isEmpty(), () -> "Unexpected codec failure: " + failures);
    }

    public static class IntegerEncoder implements MessageEncoder {
        public IntegerEncoder() {
        }

        @Override
        public Set<Class<?>> getMessageTypes() {
            return Set.of(Integer.class);
        }

        @Override
        public void encode(IoSession session, Object message, ProtocolEncoderOutput out) {
            ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
            buffer.putInt((Integer) message);
            buffer.flip();
            out.write(buffer);
        }
    }

    public static class IntegerDecoder extends MessageDecoderAdapter {
        public IntegerDecoder() {
        }

        @Override
        public MessageDecoderResult decodable(IoSession session, ByteBuffer in) {
            return in.remaining() >= Integer.BYTES ? OK : NEED_DATA;
        }

        @Override
        public MessageDecoderResult decode(IoSession session, ByteBuffer in, ProtocolDecoderOutput out) {
            if (in.remaining() < Integer.BYTES) {
                return NEED_DATA;
            }
            out.write(in.getInt());
            return OK;
        }
    }
}
