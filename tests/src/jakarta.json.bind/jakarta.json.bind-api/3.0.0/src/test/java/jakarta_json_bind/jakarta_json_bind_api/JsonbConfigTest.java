/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_json_bind.jakarta_json_bind_api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;

import org.junit.jupiter.api.Test;

public class JsonbConfigTest {
    @Test
    void mergesAdaptersRegisteredAcrossMultipleCalls() {
        JsonbAdapter<String, Integer> firstAdapter = new StringIntegerAdapter();
        JsonbAdapter<Long, String> secondAdapter = new LongStringAdapter();

        JsonbConfig config = new JsonbConfig()
                .withAdapters(firstAdapter)
                .withAdapters(secondAdapter);

        Object adapters = config.getProperty(JsonbConfig.ADAPTERS).orElseThrow();
        assertThat(adapters).isInstanceOf(JsonbAdapter[].class);
        assertThat((Object[]) adapters).containsExactly(firstAdapter, secondAdapter);
    }

    private static final class StringIntegerAdapter implements JsonbAdapter<String, Integer> {
        @Override
        public Integer adaptToJson(final String obj) {
            return obj == null ? null : obj.length();
        }

        @Override
        public String adaptFromJson(final Integer obj) {
            return obj == null ? null : obj.toString();
        }
    }

    private static final class LongStringAdapter implements JsonbAdapter<Long, String> {
        @Override
        public String adaptToJson(final Long obj) {
            return obj == null ? null : obj.toString();
        }

        @Override
        public Long adaptFromJson(final String obj) {
            return obj == null ? null : Long.valueOf(obj);
        }
    }
}
