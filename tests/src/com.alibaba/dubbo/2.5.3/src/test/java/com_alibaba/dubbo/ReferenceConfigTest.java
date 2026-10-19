/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.config.ReferenceConfig;
import org.junit.jupiter.api.Test;

public class ReferenceConfigTest {
    @Test
    void resolvesConfiguredInterfaceName() {
        ReferenceConfig<RpcTestSupport.Echo> reference =
                new ReferenceConfig<RpcTestSupport.Echo>();
        reference.setInterface(RpcTestSupport.Echo.class.getName());

        assertThat(reference.getInterfaceClass()).isEqualTo(RpcTestSupport.Echo.class);
    }
}
