/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_razorvine.serpent;

import net.razorvine.serpent.Serializer;
import org.junit.jupiter.api.Test;
import org.python.core.PyByteArray;
import org.python.core.PyComplex;
import org.python.core.PyFloat;
import org.python.core.PyMemoryView;
import org.python.core.PyTuple;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializerTest {
    @Test
    void serializesJythonTupleThroughPublicApi() {
        PyTuple tuple = new PyTuple(1, "two", true);

        String serialized = serialize(tuple);

        assertThat(serialized).isEqualTo("# serpent utf-8 python3.2\n(1,'two',True)");
    }

    @Test
    void serializesJythonComplexThroughPublicApi() {
        PyComplex complex = new PyComplex(new PyFloat(3.5), new PyFloat(-2.25));

        String serialized = serialize(complex);

        assertThat(serialized).isEqualTo("# serpent utf-8 python3.2\n(3.5-2.25j)");
    }

    @Test
    void serializesJythonByteArrayThroughPublicApi() {
        PyByteArray byteArray = new PyByteArray(new byte[] {1, 2, 3, 4});

        String serialized = serialize(byteArray);

        assertThat(serialized)
                .contains("'data':'AQIDBA=='")
                .contains("'encoding':'base64'");
    }

    @Test
    void serializesJythonMemoryViewThroughPublicApi() {
        PyMemoryView memoryView = new PyMemoryView(new byte[] {5, 6, 7});

        String serialized = serialize(memoryView);

        assertThat(serialized)
                .contains("'data':'BQYH'")
                .contains("'encoding':'base64'");
    }

    @Test
    void serializesSerializableBeanPropertiesThroughPublicApi() {
        SampleBean bean = new SampleBean("serpent", true, "https://example.test/serpent");

        String serialized = serialize(bean);

        assertThat(serialized)
                .contains("'__class__':'SampleBean'")
                .contains("'name':'serpent'")
                .contains("'active':True")
                .contains("'URL':'https://example.test/serpent'");
    }

    private static String serialize(Object value) {
        return new String(new Serializer().serialize(value), StandardCharsets.UTF_8);
    }

    public static final class SampleBean implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final boolean active;
        private final String url;

        private SampleBean(String name, boolean active, String url) {
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
