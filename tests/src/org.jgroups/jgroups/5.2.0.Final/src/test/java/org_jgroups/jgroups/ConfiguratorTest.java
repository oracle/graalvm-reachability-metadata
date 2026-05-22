/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jgroups.annotations.Property;
import org.jgroups.logging.LogFactory;
import org.jgroups.protocols.HDRS;
import org.jgroups.protocols.SHARED_LOOPBACK;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.stack.Configurator;
import org.jgroups.stack.Protocol;
import org.jgroups.stack.ProtocolStack;
import org.jgroups.util.StackType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfiguratorTest {
    @Test
    void createsProtocolLayerByNameWithDefaultConstructor() throws Exception {
        ProtocolStack stack = new ProtocolStack();

        Protocol protocol = Configurator.createProtocol(HDRS.class.getName(), stack, false);

        assertThat(protocol).isInstanceOf(HDRS.class);
        assertThat(protocol.getProtocolStack()).isSameAs(stack);
    }

    @Test
    void initializesAnnotatedFieldsAndMethodsThroughPropertyDependencies() throws Exception {
        boolean previousUseJdkLogger = LogFactory.useJdkLogger();
        LogFactory.useJdkLogger(true);
        try {
            GMS protocol = new GMS();
            Map<String, String> properties = new HashMap<>();
            properties.put("join_timeout", "3210");
            properties.put("level", "warn");

            Configurator.initializeAttrs(protocol, properties, StackType.IPv4);

            assertThat(protocol.getJoinTimeout()).isEqualTo(3210);
            assertThat(protocol.getLevel()).isEqualTo("WARNING");
            assertThat(properties).isEmpty();
        } finally {
            LogFactory.useJdkLogger(previousUseJdkLogger);
        }
    }

    @Test
    void resolvesAnnotatedMethodsAndFieldsThroughPublicConfiguratorApi() throws Exception {
        boolean previousUseJdkLogger = LogFactory.useJdkLogger();
        LogFactory.useJdkLogger(true);
        try {
            GMS protocol = new GMS();
            Map<String, String> methodProperties = new HashMap<>();
            methodProperties.put("level", "error");

            Configurator.resolveAndInvokePropertyMethods(protocol, methodProperties, StackType.IPv4);

            assertThat(protocol.getLevel()).isEqualTo("SEVERE");
            assertThat(methodProperties).isEmpty();

            Map<String, String> fieldProperties = new HashMap<>();
            fieldProperties.put("max_join_attempts", "4");

            Configurator.resolveAndAssignFields(protocol, fieldProperties, StackType.IPv4);

            assertThat(protocol.getMaxJoinAttempts()).isEqualTo(4);
            assertThat(fieldProperties).isEmpty();
        } finally {
            LogFactory.useJdkLogger(previousUseJdkLogger);
        }
    }

    @Test
    void extractsInetAddressValuesFromAnnotatedProtocolFields() throws Exception {
        SHARED_LOOPBACK transport = new SHARED_LOOPBACK();
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        transport.setBindAddress(loopbackAddress);

        List<InetAddress> addresses = Configurator.getInetAddresses(List.of(transport));

        assertThat(addresses).contains(loopbackAddress);
    }

    @Test
    void assignsDefaultInetAddressValuesThroughAnnotatedSetterMethods() throws Exception {
        AddressDefaults target = new AddressDefaults();

        Configurator.setDefaultAddressValues(target, StackType.IPv4);

        assertThat(target.defaultAddress()).isEqualTo(InetAddress.getByName("127.0.0.1"));
    }

    public static class AddressDefaults {
        private InetAddress defaultAddress;

        InetAddress defaultAddress() {
            return defaultAddress;
        }

        @Property(name = "method_address", defaultValueIPv4 = "127.0.0.1")
        public void setDefaultAddress(InetAddress newDefaultAddress) {
            defaultAddress = newDefaultAddress;
        }
    }
}
