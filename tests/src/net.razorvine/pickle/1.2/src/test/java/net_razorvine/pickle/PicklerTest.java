/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_razorvine.pickle;

import java.io.Serializable;
import java.util.Map;

import net.razorvine.pickle.Pickler;
import net.razorvine.pickle.Unpickler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PicklerTest {
    @Test
    void picklesSerializableJavaBeanThroughItsPublicAccessors() throws Exception {
        JavaBean bean = new JavaBean("alpha", true, "https://example.test/pickle");

        byte[] pickle = new Pickler().dumps(bean);
        Object unpickled = new Unpickler().loads(pickle);

        assertThat(unpickled).isInstanceOfSatisfying(Map.class, rawMap -> {
            Map<?, ?> map = (Map<?, ?>) rawMap;
            assertThat(map.get("name")).isEqualTo("alpha");
            assertThat(map.get("active")).isEqualTo(true);
            assertThat(map.get("URL")).isEqualTo("https://example.test/pickle");
            assertThat(map.get("__class__")).isEqualTo(JavaBean.class.getName());
        });
    }

    public static final class JavaBean implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final boolean active;
        private final String url;

        public JavaBean(String name, boolean active, String url) {
            this.name = name;
            this.active = active;
            this.url = url;
        }

        public String getName() {
            return name;
        }

        public boolean isActive() {
            return active;
        }

        public String getURL() {
            return url;
        }
    }
}
