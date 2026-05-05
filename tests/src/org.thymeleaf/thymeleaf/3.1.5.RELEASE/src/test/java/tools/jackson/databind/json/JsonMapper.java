/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools.jackson.databind.json;

import java.text.DateFormat;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

public class JsonMapper extends ObjectMapper {

    private static boolean builderAvailable = true;

    public static Builder builder() {
        if (!builderAvailable) {
            throw new IllegalStateException("JsonMapper builder intentionally unavailable");
        }
        return new Builder();
    }

    public static void setBuilderAvailable(boolean builderAvailable) {
        JsonMapper.builderAvailable = builderAvailable;
    }

    public static final class Builder {

        private final JsonMapper mapper = new JsonMapper();

        public Builder defaultDateFormat(DateFormat dateFormat) {
            this.mapper.setDateFormat(dateFormat);
            return this;
        }

        public Builder disable(SerializationFeature... features) {
            return this;
        }

        public Builder findAndAddModules() {
            return this;
        }

        public JsonMapper build() {
            return this.mapper;
        }
    }
}
