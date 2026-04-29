/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.annotations.Property;
import org.jgroups.protocols.DISCARD;
import org.jgroups.protocols.UDP;
import org.jgroups.stack.Configurator;
import org.jgroups.stack.Protocol;
import org.jgroups.util.StackType;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfiguratorTest {
    @Test
    void createsProtocolLayerByProtocolName() throws Exception {
        Protocol protocol = Configurator.createProtocol("DISCARD", null, false);

        assertThat(protocol).isInstanceOf(DISCARD.class);
    }

    @Test
    void initializesAnnotatedFieldPropertiesInDependencyPass() throws Exception {
        DISCARD protocol = new DISCARD();
        Map<String, String> properties = new HashMap<>(Map.of(
            "up", "0.25",
            "down", "0.5"));

        Configurator.initializeAttrs(protocol, properties, StackType.IPv4);

        assertThat(protocol.getUpDiscardRate()).isEqualTo(0.25);
        assertThat(protocol.getDownDiscardRate()).isEqualTo(0.5);
        assertThat(properties).isEmpty();
    }

    @Test
    void assignsAnnotatedFieldsFromProperties() throws Exception {
        DISCARD protocol = new DISCARD();
        Map<String, String> properties = new HashMap<>(Map.of("up", "0.75"));

        Configurator.resolveAndAssignFields(protocol, properties, StackType.IPv4);

        assertThat(protocol.getUpDiscardRate()).isEqualTo(0.75);
        assertThat(properties).isEmpty();
    }

    @Test
    void invokesAnnotatedPropertyMethodsFromProperties() throws Exception {
        DISCARD protocol = new DISCARD();
        Map<String, String> properties = new HashMap<>(Map.of("level", "trace"));

        Configurator.resolveAndInvokePropertyMethods(protocol, properties, StackType.IPv4);

        assertThat(protocol.getLevel()).isEqualToIgnoringCase("trace");
        assertThat(properties).isEmpty();
    }

    @Test
    void readsInetAddressPropertiesFromProtocols() throws Exception {
        UDP protocol = new UDP();
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        Map<String, String> properties = new HashMap<>(Map.of("bind_addr", loopback.getHostAddress()));
        Configurator.resolveAndAssignFields(protocol, properties, StackType.IPv4);

        List<InetAddress> addresses = Configurator.getInetAddresses(List.of(protocol));

        assertThat(addresses).contains(loopback);
    }

    @Test
    void appliesDefaultInetAddressThroughAnnotatedPropertyMethod() throws Exception {
        MethodDefaultAddressTarget target = new MethodDefaultAddressTarget();

        Configurator.setDefaultAddressValues(target, StackType.IPv4);

        assertThat(target.bindAddress()).isEqualTo(InetAddress.getByName("127.0.0.1"));
    }

    public static class MethodDefaultAddressTarget {
        private InetAddress bindAddress;

        public InetAddress bindAddress() {
            return bindAddress;
        }

        @Property(defaultValueIPv4 = "127.0.0.1", defaultValueIPv6 = "::1")
        public void setBindAddress(InetAddress bindAddress) {
            this.bindAddress = bindAddress;
        }
    }
}
