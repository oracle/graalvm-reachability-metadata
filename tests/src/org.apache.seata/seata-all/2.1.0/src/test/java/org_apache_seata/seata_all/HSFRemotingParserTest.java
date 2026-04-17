/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import com.taobao.hsf.app.spring.util.HSFSpringProviderBean;

import org.apache.seata.integration.tx.api.remoting.Protocols;
import org.apache.seata.integration.tx.api.remoting.RemotingDesc;
import org.apache.seata.integration.tx.api.remoting.parser.HSFRemotingParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HSFRemotingParserTest {
    @Test
    void getServiceDescLoadsTheDeclaredServiceClassForProviderBeans() throws Exception {
        HSFRemotingParser parser = new HSFRemotingParser();
        SampleHsfServiceImpl target = new SampleHsfServiceImpl();
        HSFSpringProviderBean bean = new HSFSpringProviderBean(new ProviderBean(
                new ProviderMetadata(SampleHsfService.class.getName(), "1.0.0", "orders", target)));

        RemotingDesc remotingDesc = parser.getServiceDesc(bean, "hsfProvider");

        assertThat(parser.isRemoting(bean, "hsfProvider")).isTrue();
        assertThat(parser.isService(bean, "hsfProvider")).isTrue();
        assertThat(parser.isService(bean.getClass())).isTrue();
        assertThat(remotingDesc).isNotNull();
        assertThat(remotingDesc.getServiceClass()).isSameAs(SampleHsfService.class);
        assertThat(remotingDesc.getServiceClassName()).isEqualTo(SampleHsfService.class.getName());
        assertThat(remotingDesc.getUniqueId()).isEqualTo("1.0.0");
        assertThat(remotingDesc.getGroup()).isEqualTo("orders");
        assertThat(remotingDesc.getTargetBean()).isSameAs(target);
        assertThat(remotingDesc.getProtocol()).isEqualTo(Protocols.HSF);
        assertThat(remotingDesc.isReference()).isFalse();
        assertThat(remotingDesc.isService()).isTrue();
    }

    public interface SampleHsfService {
        String ping();
    }

    private static final class SampleHsfServiceImpl implements SampleHsfService {
        @Override
        public String ping() {
            return "pong";
        }
    }

    private static final class ProviderBean {
        private final ProviderMetadata metadata;

        private ProviderBean(ProviderMetadata metadata) {
            this.metadata = metadata;
        }

        public ProviderMetadata getMetadata() {
            return metadata;
        }
    }

    private static final class ProviderMetadata {
        private final String interfaceName;
        private final String version;
        private final String group;
        private final Object target;

        private ProviderMetadata(String interfaceName, String version, String group, Object target) {
            this.interfaceName = interfaceName;
            this.version = version;
            this.group = group;
            this.target = target;
        }

        public String getInterfaceName() {
            return interfaceName;
        }

        public String getVersion() {
            return version;
        }

        public String getGroup() {
            return group;
        }
    }
}
