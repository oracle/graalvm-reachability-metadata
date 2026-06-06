/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_gsonfire.gson_fire;

import com.google.gson.Gson;
import io.gsonfire.GsonFireBuilder;
import io.gsonfire.annotations.ExposeMethodResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MappedMethodInspectorTest {
    @Test
    void serializesAnnotatedMethodResultIntoJsonObject() {
        Gson gson = new GsonFireBuilder()
                .enableExposeMethodResult()
                .createGson();

        String json = gson.toJson(new Invoice("INV-2026-001", 125));

        assertThat(json).contains("\"id\":\"INV-2026-001\"");
        assertThat(json).contains("\"total\":125");
        assertThat(json).contains("\"summary\":\"INV-2026-001:125\"");
        assertThat(json).contains("\"status\":\"paid\"");
    }

    private static final class Invoice {
        private final String id;
        private final int total;
        private final String status = "draft";

        private Invoice(String id, int total) {
            this.id = id;
            this.total = total;
        }

        @ExposeMethodResult("summary")
        private String summary() {
            return id + ":" + total;
        }

        @ExposeMethodResult(
                value = "status",
                conflictResolution = ExposeMethodResult.ConflictResolutionStrategy.OVERWRITE)
        private String exposedStatus() {
            return "paid";
        }
    }
}
