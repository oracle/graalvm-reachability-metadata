/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_beanbag.smallrye_beanbag;

import java.lang.reflect.Constructor;

import io.smallrye.beanbag.BeanBag;
import io.smallrye.beanbag.BeanSupplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstructorSupplierTest {
    @Test
    void createsBeanWithResolvedConstructorArguments() throws NoSuchMethodException {
        ServiceConfig config = new ServiceConfig("orders", 3);
        Constructor<ServiceClient> constructor = ServiceClient.class.getConstructor(ServiceConfig.class, String.class);

        BeanBag beanBag = BeanBag.builder()
                .addBeanInstance(config)
                .addBean(ServiceClient.class)
                .buildSupplier()
                .setConstructor(constructor)
                .addConstructorArgument(ServiceConfig.class)
                .addConstructorArgument(BeanSupplier.of("primary"))
                .build()
                .build()
                .build();

        ServiceClient client = beanBag.requireBean(ServiceClient.class);

        assertThat(client.config()).isSameAs(config);
        assertThat(client.routeName()).isEqualTo("primary-orders");
        assertThat(client.retryCount()).isEqualTo(3);
    }

    public static final class ServiceConfig {
        private final String serviceName;
        private final int retryCount;

        public ServiceConfig(String serviceName, int retryCount) {
            this.serviceName = serviceName;
            this.retryCount = retryCount;
        }

        String serviceName() {
            return serviceName;
        }

        int retryCount() {
            return retryCount;
        }
    }

    public static final class ServiceClient {
        private final ServiceConfig config;
        private final String routeName;

        public ServiceClient(ServiceConfig config, String routePrefix) {
            this.config = config;
            this.routeName = routePrefix + "-" + config.serviceName();
        }

        ServiceConfig config() {
            return config;
        }

        String routeName() {
            return routeName;
        }

        int retryCount() {
            return config.retryCount();
        }
    }
}
