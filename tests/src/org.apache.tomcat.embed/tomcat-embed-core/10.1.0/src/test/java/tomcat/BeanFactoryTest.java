/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.net.URI;
import javax.naming.StringRefAddr;

import org.apache.naming.ResourceRef;
import org.apache.naming.factory.BeanFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanFactoryTest {
    @Test
    void convertsAndPopulatesBeanProperties() throws Exception {
        ResourceRef reference = new ResourceRef(Settings.class.getName(), null, null, null, true);
        reference.add(new StringRefAddr("name", "catalog"));
        reference.add(new StringRefAddr("limit", "12"));
        reference.add(new StringRefAddr("enabled", "true"));
        reference.add(new StringRefAddr("endpoint", "https://example.test/catalog"));
        Settings bean = (Settings) new BeanFactory().getObjectInstance(reference, null, null, null);
        assertThat(bean.getName()).isEqualTo("catalog");
        assertThat(bean.getLimit()).isEqualTo(12);
        assertThat(bean.isEnabled()).isTrue();
        assertThat(bean.getEndpoint()).isEqualTo(URI.create("https://example.test/catalog"));
    }

    public static class Settings {
        private String name;
        private int limit;
        private boolean enabled;
        private URI endpoint;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public URI getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(URI endpoint) {
            this.endpoint = endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = URI.create(endpoint);
        }
    }
}
