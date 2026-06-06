/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_gsonfire.gson_fire;

import com.google.gson.Gson;
import io.gsonfire.GsonFireBuilder;
import io.gsonfire.annotations.ExcludeByValue;
import io.gsonfire.gson.ExclusionByValueStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExcludeByValueTypeAdapterFactoryInnerExcludeByValueTypeAdapterTest {
    @Test
    void serializesObjectWithoutFieldRejectedByValueStrategy() {
        Gson gson = new GsonFireBuilder()
                .enableExclusionByValue()
                .createGson();

        String json = gson.toJson(new SecretDocument("public-title", "hidden-token"));

        assertThat(json).contains("public-title");
        assertThat(json).doesNotContain("hidden-token");
        assertThat(json).doesNotContain("secret");
    }

    public static class SecretDocument {
        private final String title;

        @ExcludeByValue(HiddenTokenStrategy.class)
        private final String secret;

        SecretDocument(String title, String secret) {
            this.title = title;
            this.secret = secret;
        }
    }

    public static class HiddenTokenStrategy implements ExclusionByValueStrategy<String> {
        @Override
        public boolean shouldSkipField(String fieldValue) {
            return "hidden-token".equals(fieldValue);
        }
    }
}
