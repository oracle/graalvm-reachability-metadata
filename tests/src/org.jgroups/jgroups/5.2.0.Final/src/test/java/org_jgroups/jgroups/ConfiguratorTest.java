/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.annotations.Property;
import org.jgroups.conf.ProtocolConfiguration;
import org.jgroups.protocols.STATS;
import org.jgroups.stack.Configurator;
import org.jgroups.stack.Protocol;
import org.jgroups.stack.ProtocolStack;
import org.jgroups.util.StackType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfiguratorTest {
    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @Test
    void createsProtocolLayersFromConfiguration() throws Exception {
        ProtocolStack stack = new ProtocolStack();
        ProtocolConfiguration statsConfiguration = new ProtocolConfiguration("STATS");

        List<Protocol> protocols = Configurator.createProtocols(List.of(statsConfiguration), stack);

        assertThat(protocols).hasSize(1);
        assertThat(protocols.get(0)).isInstanceOf(STATS.class);
        assertThat(protocols.get(0).getProtocolStack()).isSameAs(stack);
    }

    @Test
    void initializesAnnotatedFieldsAndMethodsInDependencyOrder() throws Exception {
        DependencyConfiguredProtocol protocol = new DependencyConfiguredProtocol();
        Map<String, String> properties = new HashMap<>();
        properties.put("field_value", "field");
        properties.put("method_value", "method");
        properties.put("dependent_value", "dependent");

        Configurator.initializeAttrs(protocol, properties, StackType.IPv4);

        assertThat(protocol.fieldValue()).isEqualTo("field");
        assertThat(protocol.methodValue()).isEqualTo("method");
        assertThat(protocol.dependentValue()).isEqualTo("dependent");
        assertThat(properties).isEmpty();
    }

    @Test
    void invokesPropertyMethodsFromPublicMethodScan() throws Exception {
        DependencyConfiguredProtocol protocol = new DependencyConfiguredProtocol();
        Map<String, String> properties = new HashMap<>();
        properties.put("method_value", "configured");

        Configurator.resolveAndInvokePropertyMethods(protocol, properties, StackType.IPv4);

        assertThat(protocol.methodValue()).isEqualTo("configured");
        assertThat(properties).isEmpty();
    }

    @Test
    void assignsDefaultAddressThroughSetterAndCollectsAnnotatedAddressFields() throws Exception {
        AddressConfiguredProtocol protocol = new AddressConfiguredProtocol();

        Configurator.setDefaultAddressValues(protocol, StackType.IPv4);
        List<InetAddress> addresses = Configurator.getInetAddresses(List.of(protocol));

        assertThat(protocol.defaultAddress()).isEqualTo(InetAddress.getByName("127.0.0.1"));
        assertThat(addresses).contains(InetAddress.getLoopbackAddress());
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }

    public static class DependencyConfiguredProtocol extends Protocol {
        @Property
        private String field_value;

        @Property(dependsUpon = "method_value")
        private String dependent_value;

        private String method_value;

        @Property
        public DependencyConfiguredProtocol setMethodValue(String value) {
            method_value = value;
            return this;
        }

        public String fieldValue() {
            return field_value;
        }

        public String methodValue() {
            return method_value;
        }

        public String dependentValue() {
            return dependent_value;
        }
    }

    public static class AddressConfiguredProtocol extends Protocol {
        @Property
        private InetAddress field_address = InetAddress.getLoopbackAddress();

        private InetAddress default_address;

        @Property(name = "default_address", defaultValueIPv4 = "127.0.0.1")
        public AddressConfiguredProtocol setDefaultAddress(InetAddress address) {
            default_address = address;
            return this;
        }

        public InetAddress defaultAddress() {
            return default_address;
        }
    }
}
