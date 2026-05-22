/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.util.concurrent.atomic.AtomicReference;

import org.jgroups.JChannel;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.stack.Protocol;
import org.jgroups.stack.ProtocolHook;
import org.jgroups.stack.ProtocolStack;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtocolStackTest {
    @Test
    void initializesClassConfiguratorWhenConstructedForAChannel() throws Exception {
        ProtocolStack stack = new ProtocolStack((JChannel) null);

        assertThat(stack.getChannel()).isNull();
    }

    @Test
    void createsProtocolsByClassNameThroughTheStackFactory() throws Exception {
        ExposedProtocolStack stack = new ExposedProtocolStack();

        Protocol protocol = stack.create(GMS.class.getName());

        assertThat(protocol).isInstanceOf(GMS.class);
        assertThat(protocol.getProtocolStack()).isSameAs(stack);
    }

    @Test
    void printsFieldAndMethodBackedProtocolProperties() {
        ProtocolStack stack = new ProtocolStack();
        GMS gms = new GMS();

        stack.addProtocol(gms);

        String protocolSpec = stack.printProtocolSpec(true);
        assertThat(protocolSpec).contains(GMS.class.getName())
                .contains("join_timeout=2000")
                .contains("membership_change_policy=");
    }

    @Test
    void invokesProtocolAfterCreationHooksDuringInitialization() throws Exception {
        RecordingProtocolHook.createdProtocol().set(null);
        ProtocolStack stack = new ProtocolStack();
        HookedProtocol protocol = new HookedProtocol();
        protocol.setValue("after_creation_hook", RecordingProtocolHook.class.getName());

        stack.addProtocol(protocol);
        stack.initProtocolStack();

        assertThat(RecordingProtocolHook.createdProtocol()).hasValue(protocol);
    }

    public static class ExposedProtocolStack extends ProtocolStack {
        Protocol create(String className) throws Exception {
            return createProtocol(className);
        }
    }

    public static class HookedProtocol extends Protocol {
    }

    public static class RecordingProtocolHook implements ProtocolHook {
        private static final AtomicReference<Protocol> CREATED_PROTOCOL = new AtomicReference<>();

        static AtomicReference<Protocol> createdProtocol() {
            return CREATED_PROTOCOL;
        }

        @Override
        public void afterCreation(Protocol protocol) {
            CREATED_PROTOCOL.set(protocol);
        }
    }
}
