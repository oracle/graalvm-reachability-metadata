/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.taobao.hsf.app.spring.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.seata.integration.tx.api.remoting.Protocols;
import org.apache.seata.integration.tx.api.remoting.RemotingDesc;
import org.apache.seata.integration.tx.api.remoting.parser.HSFRemotingParser;
import org.junit.jupiter.api.Test;

public class HSFRemotingParserTest {
    @Test
    void describesHsfProviderServiceFromMetadataInterfaceName() {
        HsfOrderServiceImpl targetBean = new HsfOrderServiceImpl();
        HsfMetadata metadata = new HsfMetadata(HsfOrderService.class.getName(), "stable", "orders", targetBean);
        HSFSpringProviderBean providerBean = new HSFSpringProviderBean(new HsfProviderBean(metadata));
        HSFRemotingParser parser = new HSFRemotingParser();

        RemotingDesc remotingDesc = parser.getServiceDesc(providerBean, "orderServiceProvider");

        assertThat(remotingDesc).isNotNull();
        assertThat(remotingDesc.getServiceClass()).isEqualTo(HsfOrderService.class);
        assertThat(remotingDesc.getServiceClassName()).isEqualTo(HsfOrderService.class.getName());
        assertThat(remotingDesc.getUniqueId()).isEqualTo("stable");
        assertThat(remotingDesc.getGroup()).isEqualTo("orders");
        assertThat(remotingDesc.getTargetBean()).isSameAs(targetBean);
        assertThat(remotingDesc.getProtocol()).isEqualTo(Protocols.HSF);
        assertThat(remotingDesc.isService()).isTrue();
        assertThat(remotingDesc.isReference()).isFalse();
    }

    static class HsfProviderBean {
        private final HsfMetadata metadata;

        HsfProviderBean(HsfMetadata metadata) {
            this.metadata = metadata;
        }

        HsfMetadata getMetadata() {
            return metadata;
        }
    }

    static class HsfMetadata {
        private final String interfaceName;
        private final String version;
        private final String group;
        private final Object target;

        HsfMetadata(String interfaceName, String version, String group, Object target) {
            this.interfaceName = interfaceName;
            this.version = version;
            this.group = group;
            this.target = target;
        }

        String getInterfaceName() {
            return interfaceName;
        }

        String getVersion() {
            return version;
        }

        String getGroup() {
            return group;
        }
    }
}

class HSFSpringConsumerBean {
}

class HSFSpringProviderBean {
    private final HSFRemotingParserTest.HsfProviderBean providerBean;

    HSFSpringProviderBean(HSFRemotingParserTest.HsfProviderBean providerBean) {
        this.providerBean = providerBean;
    }
}

interface HsfOrderService {
    String findOrder(String orderId);
}

class HsfOrderServiceImpl implements HsfOrderService {
    @Override
    public String findOrder(String orderId) {
        return orderId;
    }
}
