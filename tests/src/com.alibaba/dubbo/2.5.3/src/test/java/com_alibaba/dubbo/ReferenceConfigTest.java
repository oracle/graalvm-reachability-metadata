/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ProtocolConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.alibaba.dubbo.config.ServiceConfig;
import org.junit.jupiter.api.Test;

public class ReferenceConfigTest {
    @Test
    void refersToAnInjvmServiceConfiguredByInterfaceName() {
        ServiceConfigTest.installStaticallyRepresentableAdaptiveExtensions();
        ApplicationConfig application = new ApplicationConfig("reference-config-test");
        RegistryConfig registry = new RegistryConfig(RegistryConfig.NO_AVAILABLE);
        ProtocolConfig protocol = new ProtocolConfig("injvm", 0);
        ServiceConfig<Echo> service = new ServiceConfig<Echo>();
        service.setApplication(application);
        service.setRegistry(registry);
        service.setProtocol(protocol);
        service.setProxy("jdk");
        service.setInterface(Echo.class);
        service.setRef(new EchoService());

        ReferenceConfig<Echo> reference = new ReferenceConfig<Echo>();
        reference.setApplication(application);
        reference.setRegistry(registry);
        reference.setInjvm(true);
        reference.setProxy("jdk");
        reference.setInterface(Echo.class.getName());

        try {
            service.export();
            Echo echo = reference.get();

            assertThat(echo.echo("dubbo")).isEqualTo("echo:dubbo");
            assertThat(reference.getInterfaceClass()).isEqualTo(Echo.class);
        } finally {
            reference.destroy();
            service.unexport();
        }
    }

    public interface Echo {
        String echo(String value);
    }

    public static class EchoService implements Echo {
        @Override
        public String echo(String value) {
            return "echo:" + value;
        }
    }
}
