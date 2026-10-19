/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.alibaba.dubbo.config.AbstractInterfaceConfig;
import com.alibaba.dubbo.config.MethodConfig;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class AbstractInterfaceConfigTest {
    @Test
    void validatesInterfaceMethodsAndStubConstructors() {
        MethodConfig method = new MethodConfig();
        method.setName("echo");
        ProbeConfig config = new ProbeConfig();
        config.setStub(DubboEchoStub.class.getName());
        config.setMock(EchoMock.class.getName());

        assertThatCode(
                        () -> {
                            config.validateMethods(Echo.class, method);
                            config.validateStub(Echo.class);
                        })
                .doesNotThrowAnyException();
    }

    public interface Echo {
        String echo(String value);
    }

    public static class EchoMock implements Echo {
        public EchoMock() {}

        @Override
        public String echo(String value) {
            return "mock:" + value;
        }
    }

    public static class ProbeConfig extends AbstractInterfaceConfig {
        void validateMethods(Class<?> type, MethodConfig method) {
            checkInterfaceAndMethods(type, Collections.singletonList(method));
        }

        void validateStub(Class<?> type) {
            checkStubAndMock(type);
        }
    }
}
