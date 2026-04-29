/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.Address;
import org.jgroups.BytesMessage;
import org.jgroups.Message;
import org.jgroups.MessageFactory;
import org.jgroups.PhysicalAddress;
import org.jgroups.View;
import org.jgroups.protocols.Bundler;
import org.jgroups.protocols.LocalTransport;
import org.jgroups.protocols.TP;
import org.jgroups.stack.MessageProcessingPolicy;
import org.jgroups.util.MessageBatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class TPTest {
    @BeforeEach
    void resetRecorders() {
        RecordingBundler.reset();
        RecordingLocalTransport.reset();
        RecordingMessageFactory.reset();
        RecordingMessageProcessingPolicy.resetRecorder();
    }

    @Test
    void setMessageProcessingPolicyInstantiatesNamedPolicy() {
        TestTransport transport = new TestTransport();

        transport.setMessageProcessingPolicy(RecordingMessageProcessingPolicy.class.getName());

        assertThat(RecordingMessageProcessingPolicy.instances()).isEqualTo(1);
        assertThat(RecordingMessageProcessingPolicy.initCalls()).isEqualTo(1);
        assertThat(RecordingMessageProcessingPolicy.lastTransport()).isSameAs(transport);
    }

    @Test
    void setLocalTransportInstantiatesNamedTransport() throws Exception {
        TestTransport transport = new TestTransport();

        transport.setLocalTransport(RecordingLocalTransport.class.getName());

        assertThat(RecordingLocalTransport.instances()).isEqualTo(1);
        assertThat(RecordingLocalTransport.initCalls()).isEqualTo(1);
        assertThat(RecordingLocalTransport.startCalls()).isEqualTo(1);
        assertThat(RecordingLocalTransport.lastTransport()).isSameAs(transport);

        transport.setLocalTransport((LocalTransport)null);
    }

    @Test
    void initInstantiatesConfiguredTransportPolicyFactoryAndBundler() throws Exception {
        TestTransport transport = new TestTransport();
        transport.setLogicalAddrCacheReaperInterval(0);
        transport.setTimeServiceInterval(0);
        transport.setSuppressTimeDifferentClusterWarnings(0);
        transport.setSuppressTimeDifferentVersionWarnings(0);
        transport.setMsgFactoryClass(RecordingMessageFactory.class.getName());
        transport.setBundlerType(RecordingBundler.class.getName());
        transport.setValue("local_transport_class", RecordingLocalTransport.class.getName());
        transport.setValue("message_processing_policy", RecordingMessageProcessingPolicy.class.getName());

        try {
            transport.init();

            assertThat(RecordingLocalTransport.instances()).isEqualTo(1);
            assertThat(RecordingLocalTransport.initCalls()).isEqualTo(1);
            assertThat(RecordingMessageProcessingPolicy.instances()).isEqualTo(1);
            assertThat(RecordingMessageProcessingPolicy.initCalls()).isEqualTo(1);
            assertThat(RecordingMessageFactory.instances()).isEqualTo(1);
            assertThat(RecordingBundler.instances()).isEqualTo(1);
            assertThat(RecordingBundler.initCalls()).isEqualTo(1);
            assertThat(transport.getMessageFactory()).isInstanceOf(RecordingMessageFactory.class);
            assertThat(transport.getBundler()).isInstanceOf(RecordingBundler.class);
        } finally {
            transport.destroy();
        }
    }

    public static class TestTransport extends TP {
        @Override
        public boolean supportsMulticasting() {
            return false;
        }

        @Override
        public void sendUnicast(PhysicalAddress dest, byte[] data, int offset, int length) {
        }

        @Override
        public String getInfo() {
            return "test transport";
        }

        @Override
        protected PhysicalAddress getPhysicalAddress() {
            return null;
        }
    }

    public static class RecordingMessageProcessingPolicy implements MessageProcessingPolicy {
        private static final AtomicInteger INSTANCES = new AtomicInteger();
        private static final AtomicInteger INIT_CALLS = new AtomicInteger();
        private static TP lastTransport;

        public RecordingMessageProcessingPolicy() {
            INSTANCES.incrementAndGet();
        }

        public static void resetRecorder() {
            INSTANCES.set(0);
            INIT_CALLS.set(0);
            lastTransport = null;
        }

        public static int instances() {
            return INSTANCES.get();
        }

        public static int initCalls() {
            return INIT_CALLS.get();
        }

        public static TP lastTransport() {
            return lastTransport;
        }

        @Override
        public void init(TP transport) {
            INIT_CALLS.incrementAndGet();
            lastTransport = transport;
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
        private static final AtomicInteger INSTANCES = new AtomicInteger();
        private static final AtomicInteger INIT_CALLS = new AtomicInteger();
        private static final AtomicInteger START_CALLS = new AtomicInteger();
        private static TP lastTransport;

        public RecordingLocalTransport() {
            INSTANCES.incrementAndGet();
        }

        public static void reset() {
            INSTANCES.set(0);
            INIT_CALLS.set(0);
            START_CALLS.set(0);
            lastTransport = null;
        }

        public static int instances() {
            return INSTANCES.get();
        }

        public static int initCalls() {
            return INIT_CALLS.get();
        }

        public static int startCalls() {
            return START_CALLS.get();
        }

        public static TP lastTransport() {
            return lastTransport;
        }

        @Override
        public LocalTransport init(TP transport) {
            INIT_CALLS.incrementAndGet();
            lastTransport = transport;
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
        public LocalTransport viewChange(View v) {
            return this;
        }

        @Override
        public boolean isLocalMember(Address addr) {
            return false;
        }

        @Override
        public void sendTo(Address dest, byte[] buf, int offset, int length) {
        }

        @Override
        public void sendToAll(byte[] buf, int offset, int length) {
        }
    }

    public static class RecordingMessageFactory implements MessageFactory {
        private static final AtomicInteger INSTANCES = new AtomicInteger();

        public RecordingMessageFactory() {
            INSTANCES.incrementAndGet();
        }

        public static void reset() {
            INSTANCES.set(0);
        }

        public static int instances() {
            return INSTANCES.get();
        }

        @Override
        public <T extends Message> T create(short id) {
            return (T)new BytesMessage();
        }

        @Override
        public <M extends MessageFactory> M register(short type, Supplier<? extends Message> generator) {
            return (M)this;
        }
    }

    public static class RecordingBundler implements Bundler {
        private static final AtomicInteger INSTANCES = new AtomicInteger();
        private static final AtomicInteger INIT_CALLS = new AtomicInteger();

        public RecordingBundler() {
            INSTANCES.incrementAndGet();
        }

        public static void reset() {
            INSTANCES.set(0);
            INIT_CALLS.set(0);
        }

        public static int instances() {
            return INSTANCES.get();
        }

        public static int initCalls() {
            return INIT_CALLS.get();
        }

        @Override
        public void init(TP transport) {
            INIT_CALLS.incrementAndGet();
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void send(Message msg) {
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public int getQueueSize() {
            return -1;
        }

        @Override
        public int getMaxSize() {
            return -1;
        }
    }
}
