/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.JChannel;
import org.jgroups.annotations.Property;
import org.jgroups.conf.PropertyConverter;
import org.jgroups.stack.Protocol;
import org.jgroups.stack.ProtocolHook;
import org.jgroups.stack.ProtocolStack;
import org.jgroups.util.StackType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtocolStackTest {
    @Test
    void createsStackForProgrammaticChannel() throws Exception {
        JChannel channel = new JChannel(false);
        ProtocolStack stack = new ProtocolStack(channel);

        assertThat(stack.getChannel()).isSameAs(channel);
    }

    @Test
    void createsProtocolByClassName() throws Exception {
        ExposedProtocolStack stack = new ExposedProtocolStack();

        Protocol protocol = stack.newProtocol(HookedProtocol.class.getName());

        assertThat(protocol).isInstanceOf(HookedProtocol.class);
        assertThat(protocol.getProtocolStack()).isSameAs(stack);
    }

    @Test
    void invokesConfiguredAfterCreationHookWhenInitializingProtocols() throws Exception {
        HookRecorder.reset();
        HookedProtocol protocol = new HookedProtocol();
        protocol.setValue("after_creation_hook", HookRecorder.class.getName());
        ProtocolStack stack = new ProtocolStack().addProtocol(protocol);

        stack.initProtocolStack();

        assertThat(HookRecorder.wasCalled()).isTrue();
    }

    @Test
    void printsFieldAndSetterPropertiesWithConverters() {
        ProtocolStack stack = new ProtocolStack().addProtocol(new PropertyProtocol());

        String specification = stack.printProtocolSpec(true);

        assertThat(specification).contains(PropertyProtocol.class.getName());
        assertThat(specification).contains("fieldValue=converted-alpha");
        assertThat(specification).contains("methodValue=converted-bravo");
    }

    public static class ExposedProtocolStack extends ProtocolStack {
        public Protocol newProtocol(String protocolName) throws Exception {
            return createProtocol(protocolName);
        }
    }

    public static class HookedProtocol extends Protocol {
    }

    public static class HookRecorder implements ProtocolHook {
        private static final AtomicBoolean CALLED = new AtomicBoolean();

        public static void reset() {
            CALLED.set(false);
        }

        public static boolean wasCalled() {
            return CALLED.get();
        }

        @Override
        public void afterCreation(Protocol protocol) {
            CALLED.set(protocol instanceof HookedProtocol);
        }
    }

    public static class PropertyProtocol extends Protocol {
        @Property(converter = PrefixingConverter.class)
        private String fieldValue = "alpha";

        private String methodValue = "bravo";

        @Property(name = "methodValue", converter = PrefixingConverter.class)
        public void setMethodValue(String methodValue) {
            this.methodValue = methodValue;
        }
    }

    public static class PrefixingConverter implements PropertyConverter {
        @Override
        public Object convert(Object obj, Class<?> propertyFieldType, String propertyName, String propertyValue,
            boolean checkScope, StackType ipVersion) {
            return propertyValue;
        }

        @Override
        public String toString(Object value) {
            return "converted-" + value;
        }
    }
}
