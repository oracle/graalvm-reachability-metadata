/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import javax.naming.StringRefAddr;

import org.apache.naming.ResourceRef;
import org.apache.naming.factory.BeanFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanFactoryTest {

    @Test
    void createsAndConfiguresBeanWithContextClassLoader() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(BeanFactoryTest.class.getClassLoader());
        try {
            ConfigurableResource resource = createConfiguredResource();

            assertThat(resource.getName()).isEqualTo("configured-resource");
            assertThat(resource.getPort()).isEqualTo(8080);
            assertThat(resource.isEnabled()).isTrue();
            assertThat(resource.getEndpoint()).extracting(Endpoint::value).isEqualTo("primary");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void createsBeanWhenContextClassLoaderIsUnavailable() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            ConfigurableResource resource = createConfiguredResource();

            assertThat(resource.getName()).isEqualTo("configured-resource");
            assertThat(resource.getEndpoint()).extracting(Endpoint::value).isEqualTo("primary");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static ConfigurableResource createConfiguredResource() throws Exception {
        ResourceRef reference = new ResourceRef(ConfigurableResource.class.getName(), null, null, null, true);
        reference.add(new StringRefAddr("name", "configured-resource"));
        reference.add(new StringRefAddr("port", "8080"));
        reference.add(new StringRefAddr("enabled", "true"));
        reference.add(new StringRefAddr("endpoint", "primary"));

        Object instance = new BeanFactory().getObjectInstance(reference, null, null, null);

        assertThat(instance).isInstanceOf(ConfigurableResource.class);
        return (ConfigurableResource) instance;
    }

    public static class ConfigurableResource {

        private String name;
        private int port;
        private boolean enabled;
        private Endpoint endpoint;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Endpoint getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = new Endpoint(endpoint);
        }
    }

    public record Endpoint(String value) {
    }
}
