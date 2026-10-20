/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import org.junit.jupiter.api.Test;

public class ReferenceConfigTest {
    @Test
    void resolvesAnInterfaceConfiguredByName() {
        ServiceConfigTest.installStaticallyRepresentableAdaptiveExtensions();
        ReferenceConfig<Echo> reference = new ReferenceConfig<Echo>();
        reference.setInterface(Echo.class.getName());

        assertThat(reference.getInterfaceClass()).isEqualTo(Echo.class);
        assertThat(reference.getInterface()).isEqualTo(Echo.class.getName());
    }

    @Test
    void rejectsAClientStubThatDoesNotImplementTheNamedInterface() {
        ServiceConfigTest.installStaticallyRepresentableAdaptiveExtensions();
        ReferenceConfig<Echo> reference = new ReferenceConfig<Echo>();
        reference.setApplication(new ApplicationConfig("reference-config-test"));
        reference.setInterface(Echo.class.getName());
        reference.setStub(String.class.getName());

        assertThatThrownBy(reference::get)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.class.getName())
                .hasMessageContaining("not implement interface")
                .hasMessageContaining(Echo.class.getName());
    }

    public interface Echo {
        String echo(String value);
    }
}
