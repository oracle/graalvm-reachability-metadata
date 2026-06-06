/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_gsonfire.gson_fire;

import com.google.gson.Gson;
import io.gsonfire.GsonFireBuilder;
import io.gsonfire.annotations.MergeMap;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MergeMapPostProcessorTest {
    @Test
    @SuppressWarnings("deprecation")
    void serializesAnnotatedMapEntriesIntoContainingJsonObject() {
        Gson gson = new GsonFireBuilder()
                .enableMergeMaps(MergeMapDocument.class)
                .createGson();

        String json = gson.toJson(new MergeMapDocument("release-notes"));

        assertThat(json).contains("\"name\":\"release-notes\"");
        assertThat(json).contains("\"status\":\"published\"");
        assertThat(json).contains("\"revision\":7");
    }

    private static final class MergeMapDocument {
        private final String name;

        @MergeMap
        private final Map<String, Object> details;

        private MergeMapDocument(String name) {
            this.name = name;
            this.details = new LinkedHashMap<>();
            this.details.put("status", "published");
            this.details.put("revision", 7);
        }
    }
}
