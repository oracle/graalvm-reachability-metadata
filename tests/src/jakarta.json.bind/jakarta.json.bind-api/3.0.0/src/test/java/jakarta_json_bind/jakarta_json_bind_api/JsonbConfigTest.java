/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_json_bind.jakarta_json_bind_api;

import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

public class JsonbConfigTest {
    @Test
    public void mergesAdaptersRegisteredInMultipleCalls() throws Exception {
        final JsonbAdapter<String, String> firstAdapter = new PrefixAdapter("first:");
        final JsonbAdapter<String, String> secondAdapter = new PrefixAdapter("second:");

        final JsonbConfig config = new JsonbConfig()
                .withAdapters(firstAdapter)
                .withAdapters(secondAdapter);

        final Object adaptersProperty = config.getProperty(JsonbConfig.ADAPTERS).orElseThrow();
        assertInstanceOf(JsonbAdapter[].class, adaptersProperty);

        final Object[] adapters = (Object[]) adaptersProperty;
        assertEquals(2, adapters.length);
        assertSame(firstAdapter, adapters[0]);
        assertSame(secondAdapter, adapters[1]);
        assertEquals("first:value", firstAdapter.adaptToJson("value"));
        assertEquals("second:value", secondAdapter.adaptToJson("value"));
    }

    private static final class PrefixAdapter implements JsonbAdapter<String, String> {
        private final String prefix;

        private PrefixAdapter(final String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String adaptToJson(final String obj) {
            return prefix + obj;
        }

        @Override
        public String adaptFromJson(final String obj) {
            if (!obj.startsWith(prefix)) {
                throw new IllegalArgumentException("Adapted value does not start with the expected prefix");
            }
            return obj.substring(prefix.length());
        }
    }
}
