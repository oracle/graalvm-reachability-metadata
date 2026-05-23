/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jgroups.Address;
import org.jgroups.DefaultMessageFactory;
import org.jgroups.Message;
import org.jgroups.View;
import org.jgroups.protocols.LocalTransport;
import org.jgroups.protocols.NoBundler;
import org.jgroups.protocols.SHARED_LOOPBACK;
import org.jgroups.protocols.TP;
import org.jgroups.stack.MessageProcessingPolicy;
import org.jgroups.util.MessageBatch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TPTest {
    @Test
    void instantiatesCustomPolicyAndLocalTransportFromManagedSetters() throws Exception {
        RecordingMessageProcessingPolicy.resetRecorder();
        RecordingLocalTransport.reset();
        SHARED_LOOPBACK transport = new SHARED_LOOPBACK();

        try {
            transport.setMessageProcessingPolicy(RecordingMessageProcessingPolicy.class.getName());
            transport.setLocalTransport(RecordingLocalTransport.class.getName());

            assertThat(RecordingMessageProcessingPolicy.initCalls()).hasValue(1);
            assertThat(RecordingMessageProcessingPolicy.transport()).hasValue(transport);
            assertThat(RecordingLocalTransport.constructorCalls()).hasValue(1);
            assertThat(RecordingLocalTransport.initCalls()).hasValue(1);
            assertThat(RecordingLocalTransport.startCalls()).hasValue(1);
            assertThat(RecordingLocalTransport.transport()).hasValue(transport);
            assertThat(transport.getLocalTransport()).isInstanceOf(RecordingLocalTransport.class);
        } finally {
            transport.setLocalTransport((LocalTransport) null);
            transport.destroy();
        }
    }

    @Test
    void initializesConfiguredTransportFactoryPolicyAndBundlerClasses() throws Exception {
        RecordingMessageProcessingPolicy.resetRecorder();
        RecordingLocalTransport.reset();
        RecordingMessageFactory.reset();
        RecordingBundler.reset();
        SHARED_LOOPBACK transport = new SHARED_LOOPBACK();
        transport.setLogicalAddrCacheReaperInterval(0);
        transport.setTimeServiceInterval(0);
        transport.setValue("local_transport_class", RecordingLocalTransport.class.getName());
        transport.setValue("message_processing_policy", RecordingMessageProcessingPolicy.class.getName());
        transport.setMsgFactoryClass(RecordingMessageFactory.class.getName());
        transport.setBundlerType(RecordingBundler.class.getName());

        try {
            transport.init();

            assertThat(RecordingLocalTransport.constructorCalls()).hasValue(1);
            assertThat(RecordingLocalTransport.initCalls()).hasValue(1);
            assertThat(RecordingLocalTransport.startCalls()).hasValue(0);
            assertThat(RecordingLocalTransport.transport()).hasValue(transport);
            assertThat(RecordingMessageProcessingPolicy.initCalls()).hasValue(1);
            assertThat(RecordingMessageProcessingPolicy.transport()).hasValue(transport);
            assertThat(RecordingMessageFactory.constructorCalls()).hasValue(1);
            assertThat(transport.getMessageFactory()).isInstanceOf(RecordingMessageFactory.class);
            assertThat(RecordingBundler.constructorCalls()).hasValue(1);
            assertThat(RecordingBundler.initCalls()).hasValue(1);
            assertThat(RecordingBundler.transport()).hasValue(transport);
            assertThat(transport.getBundler()).isInstanceOf(RecordingBundler.class);
        } finally {
            transport.destroy();
        }
    }

    public static class RecordingMessageProcessingPolicy implements MessageProcessingPolicy {
        private static final AtomicInteger INIT_CALLS = new AtomicInteger();
        private static final AtomicReference<TP> TRANSPORT = new AtomicReference<>();

        static void resetRecorder() {
            INIT_CALLS.set(0);
            TRANSPORT.set(null);
        }

        static AtomicInteger initCalls() {
            return INIT_CALLS;
        }

        static AtomicReference<TP> transport() {
            return TRANSPORT;
        }

        @Override
        public void init(TP transport) {
            INIT_CALLS.incrementAndGet();
            TRANSPORT.set(transport);
        }

        @Override
        public boolean loopback(Message msg, boolean oob) {
            return true;
        }

        @Override
        public boolean process(Message msg, boolean oob) {
            return true;
        }

        @Override
        public boolean process(MessageBatch batch, boolean oob) {
            return true;
        }
    }

    public static class RecordingLocalTransport implements LocalTransport {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();
        private static final AtomicInteger INIT_CALLS = new AtomicInteger();
        private static final AtomicInteger START_CALLS = new AtomicInteger();
        private static final AtomicReference<TP> TRANSPORT = new AtomicReference<>();

        public RecordingLocalTransport() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        static void reset() {
            CONSTRUCTOR_CALLS.set(0);
            INIT_CALLS.set(0);
            START_CALLS.set(0);
            TRANSPORT.set(null);
        }

        static AtomicInteger constructorCalls() {
            return CONSTRUCTOR_CALLS;
        }

        static AtomicInteger initCalls() {
            return INIT_CALLS;
        }

        static AtomicInteger startCalls() {
            return START_CALLS;
        }

        static AtomicReference<TP> transport() {
            return TRANSPORT;
        }

        @Override
        public LocalTransport init(TP transport) {
            INIT_CALLS.incrementAndGet();
            TRANSPORT.set(transport);
            return this;
        }

        @Override
        public LocalTransport start() {
            START_CALLS.incrementAndGet();
            return this;
        }

        @Override
        public LocalTransport stop() {
            return this;
        }

        @Override
        public LocalTransport destroy() {
            return this;
        }

        @Override
        public LocalTransport resetStats() {
            return this;
        }

        @Override
        public LocalTransport viewChange(View view) {
            return this;
        }

        @Override
        public boolean isLocalMember(Address addr) {
            return false;
        }

        @Override
        public void sendTo(Address dest, byte[] buf, int offset, int length) {
            // No-op test transport does not deliver payloads.
        }

        @Override
        public void sendToAll(byte[] buf, int offset, int length) {
            // No-op test transport does not deliver payloads.
        }
    }

    public static class RecordingMessageFactory extends DefaultMessageFactory {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public RecordingMessageFactory() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        static void reset() {
            CONSTRUCTOR_CALLS.set(0);
        }

        static AtomicInteger constructorCalls() {
            return CONSTRUCTOR_CALLS;
        }
    }

    public static class RecordingBundler extends NoBundler {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();
        private static final AtomicInteger INIT_CALLS = new AtomicInteger();
        private static final AtomicReference<TP> TRANSPORT = new AtomicReference<>();

        public RecordingBundler() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        static void reset() {
            CONSTRUCTOR_CALLS.set(0);
            INIT_CALLS.set(0);
            TRANSPORT.set(null);
        }

        static AtomicInteger constructorCalls() {
            return CONSTRUCTOR_CALLS;
        }

        static AtomicInteger initCalls() {
            return INIT_CALLS;
        }

        static AtomicReference<TP> transport() {
            return TRANSPORT;
        }

        @Override
        public void init(TP transport) {
            INIT_CALLS.incrementAndGet();
            TRANSPORT.set(transport);
            super.init(transport);
        }

        @Override
        public void send(Message msg) {
            // No-op test bundler does not send payloads.
        }
    }
}
