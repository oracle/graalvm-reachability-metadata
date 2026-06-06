/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_gsonfire.gson_fire;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.gsonfire.GsonFireBuilder;
import io.gsonfire.annotations.PostDeserialize;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodInvokerTest {
    @Test
    void invokesPostDeserializeHookWithSupportedArguments() {
        Gson gson = new GsonFireBuilder()
                .enableHooks(HookedMessage.class)
                .createGson();

        HookedMessage message = gson.fromJson("""
                {
                  "text": "ready",
                  "priority": 5
                }
                """, HookedMessage.class);

        assertThat(message.getText()).isEqualTo("ready");
        assertThat(message.getObservedPriority()).isEqualTo(5);
        assertThat(message.isGsonArgumentObserved()).isTrue();
    }

    public static final class HookedMessage {
        private String text;
        private int priority;
        private int observedPriority;
        private boolean gsonArgumentObserved;

        public HookedMessage() {
        }

        @PostDeserialize
        private void rememberSource(JsonElement source, Gson gson) {
            this.observedPriority = source.getAsJsonObject().get("priority").getAsInt();
            this.gsonArgumentObserved = "ok".equals(gson.fromJson("\"ok\"", String.class));
        }

        public String getText() {
            return text;
        }

        public int getObservedPriority() {
            return observedPriority;
        }

        public boolean isGsonArgumentObserved() {
            return gsonArgumentObserved;
        }
    }
}
