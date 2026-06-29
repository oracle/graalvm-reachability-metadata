/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;
import org.jgroups.Receiver;
import org.jgroups.annotations.Property;
import org.jgroups.conf.PropertyConverter;
import org.jgroups.protocols.FRAG2;
import org.jgroups.protocols.SHARED_LOOPBACK;
import org.jgroups.protocols.SHARED_LOOPBACK_PING;
import org.jgroups.protocols.STATS;
import org.jgroups.protocols.UNICAST3;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.pbcast.NAKACK2;
import org.jgroups.protocols.pbcast.STABLE;
import org.jgroups.stack.Protocol;
import org.jgroups.stack.ProtocolHook;
import org.jgroups.stack.ProtocolStack;
import org.jgroups.util.StackType;
import org.jgroups.util.Util;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtocolStackTest {
    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @Test
    void localChannelsShareViewExchangeMessageAndPrintProtocolProperties() throws Exception {
        String clusterName = "ProtocolStackTest-" + System.nanoTime();
        RecordingReceiver receiver = new RecordingReceiver();
        JChannel first = null;
        JChannel second = null;
        try {
            first = new JChannel(testStack()).name("first");
            second = new JChannel(testStack()).name("second").setReceiver(receiver);
            first.connect(clusterName);
            second.connect(clusterName);

            Util.waitUntilAllChannelsHaveSameView(10_000, 500, first, second);
            first.send(new ObjectMessage(null, "hello from first"));

            assertThat(first.getView().size()).isEqualTo(2);
            assertThat(second.getView().size()).isEqualTo(2);
            assertThat(receiver.pollMessage()).isEqualTo("hello from first");
            assertThat(first.getProtocolStack().printProtocolSpec(true))
                    .contains("SHARED_LOOPBACK")
                    .contains("GMS");
        } finally {
            if (second != null) {
                second.close();
            }
            if (first != null) {
                first.close();
            }
        }
    }

    @Test
    void constructsChannelStackAndCreatesProtocolByName() throws Exception {
        JChannel channel = new JChannel(false);
        try {
            ExposedProtocolStack stack = new ExposedProtocolStack(channel);

            Protocol protocol = stack.createProtocolByName("STATS");

            assertThat(stack.getChannel()).isSameAs(channel);
            assertThat(protocol).isInstanceOf(STATS.class);
            assertThat(protocol.getProtocolStack()).isSameAs(stack);
        } finally {
            channel.close();
        }
    }

    @Test
    void invokesAfterCreationHookAndPrintsFieldAndSetterProperties() throws Exception {
        CreationHook.lastProtocolName.set(null);
        CustomProtocol protocol = new CustomProtocol().useHook(CreationHook.class.getName());
        ProtocolStack stack = new ProtocolStack().addProtocol(protocol);

        stack.initProtocolStack();
        String specification = stack.printProtocolSpec(true);

        assertThat(CreationHook.lastProtocolName).hasValue("CustomProtocol");
        assertThat(specification)
                .contains("field_value=converted-field")
                .contains("method_value=converted-method");
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }

    private static Protocol[] testStack() {
        return new Protocol[] {
                new SHARED_LOOPBACK(),
                new SHARED_LOOPBACK_PING(),
                new NAKACK2(),
                new UNICAST3(),
                new STABLE(),
                new GMS().setJoinTimeout(10_000),
                new FRAG2().setFragSize(8000)
        };
    }

    public static class ExposedProtocolStack extends ProtocolStack {
        public ExposedProtocolStack(JChannel channel) throws Exception {
            super(channel);
        }

        public Protocol createProtocolByName(String classname) throws Exception {
            return createProtocol(classname);
        }
    }

    public static class CustomProtocol extends Protocol {
        @Property(converter = PrefixingConverter.class)
        private String field_value = "field";

        private String method_value = "method";

        @Property(name = "method_value", converter = PrefixingConverter.class)
        public CustomProtocol setMethodValue(String value) {
            method_value = value;
            return this;
        }

        public CustomProtocol useHook(String hookClassName) {
            after_creation_hook = hookClassName;
            return this;
        }
    }

    public static class PrefixingConverter implements PropertyConverter {
        @Override
        public Object convert(
                Object obj,
                Class<?> propertyFieldType,
                String propertyName,
                String propertyValue,
                boolean checkScope,
                StackType ipVersion) {
            return propertyValue;
        }

        @Override
        public String toString(Object value) {
            return "converted-" + value;
        }
    }

    public static class CreationHook implements ProtocolHook {
        private static final AtomicReference<String> lastProtocolName = new AtomicReference<>();

        @Override
        public void afterCreation(Protocol prot) {
            lastProtocolName.set(prot.getName());
        }
    }

    private static class RecordingReceiver implements Receiver {
        private final BlockingQueue<Object> messages = new LinkedBlockingQueue<>();

        @Override
        public void receive(Message msg) {
            messages.add(msg.getObject());
        }

        Object pollMessage() throws InterruptedException {
            return messages.poll(10, TimeUnit.SECONDS);
        }
    }
}
