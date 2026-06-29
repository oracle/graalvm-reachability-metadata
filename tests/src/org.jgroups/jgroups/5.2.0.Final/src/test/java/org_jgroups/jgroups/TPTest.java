/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.Address;
import org.jgroups.DefaultMessageFactory;
import org.jgroups.Message;
import org.jgroups.View;
import org.jgroups.protocols.Bundler;
import org.jgroups.protocols.LocalTransport;
import org.jgroups.protocols.SHARED_LOOPBACK;
import org.jgroups.protocols.TP;
import org.jgroups.stack.MessageProcessingPolicy;
import org.jgroups.util.MessageBatch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class TPTest {
    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @Test
    void initializesConfiguredTransportCollaboratorsFromClassNames() throws Exception {
        CustomLocalTransport.created.set(0);
        CustomMessageProcessingPolicy.initialized.set(0);
        CustomBundler.initialized.set(false);
        SHARED_LOOPBACK transport = new SHARED_LOOPBACK();
        transport.setBundlerType(CustomBundler.class.getName());
        transport.setMsgFactoryClass(CustomMessageFactory.class.getName());
        transport.setValue("local_transport_class", CustomLocalTransport.class.getName());
        transport.setValue("message_processing_policy", CustomMessageProcessingPolicy.class.getName());
        boolean initialized = false;

        try {
            transport.init();
            initialized = true;

            assertThat(transport.getBundler()).isInstanceOf(CustomBundler.class);
            assertThat(transport.getMessageFactory()).isInstanceOf(CustomMessageFactory.class);
            assertThat(transport.getLocalTransport()).isInstanceOf(CustomLocalTransport.class);
            assertThat(CustomBundler.initialized).isTrue();
            assertThat(CustomLocalTransport.created).hasValue(1);
            assertThat(CustomMessageProcessingPolicy.initialized).hasValue(1);
        }
        finally {
            if (initialized) {
                transport.stop();
            }
            transport.destroy();
        }
    }

    @Test
    void replacesLocalTransportFromConfiguredClassName() throws Exception {
        CustomLocalTransport.started.set(false);
        SHARED_LOOPBACK transport = new SHARED_LOOPBACK();
        try {
            transport.setLocalTransport(CustomLocalTransport.class.getName());

            assertThat(transport.getLocalTransport()).isInstanceOf(CustomLocalTransport.class);
            assertThat(CustomLocalTransport.started).isTrue();
        }
        finally {
            transport.setLocalTransport((LocalTransport) null);
            transport.destroy();
        }
    }

    @Test
    void replacesMessageProcessingPolicyFromConfiguredClassName() {
        CustomMessageProcessingPolicy.initialized.set(0);
        SHARED_LOOPBACK transport = new SHARED_LOOPBACK();

        transport.setMessageProcessingPolicy(CustomMessageProcessingPolicy.class.getName());

        assertThat(CustomMessageProcessingPolicy.initialized).hasValue(1);
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }

    public static class CustomBundler implements Bundler {
        private static final AtomicBoolean initialized = new AtomicBoolean();

        @Override
        public void init(TP transport) {
            initialized.set(true);
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
            return 0;
        }
    }

    public static class CustomLocalTransport implements LocalTransport {
        private static final AtomicInteger created = new AtomicInteger();
        private static final AtomicBoolean started = new AtomicBoolean();

        public CustomLocalTransport() {
            created.incrementAndGet();
        }

        @Override
        public LocalTransport init(TP transport) {
            return this;
        }

        @Override
        public LocalTransport start() {
            started.set(true);
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

    public static class CustomMessageFactory extends DefaultMessageFactory {
    }

    public static class CustomMessageProcessingPolicy implements MessageProcessingPolicy {
        private static final AtomicInteger initialized = new AtomicInteger();

        @Override
        public void init(TP transport) {
            initialized.incrementAndGet();
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
}
