/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import org.junit.jupiter.api.Test;

public class InnerGsonInnerTypesTest {
    @Test
    void resolvesGenericArrayComponentTypeForDeserialization() {
        Gson gson = new Gson();
        TypeToken<List<String>[]> type = new TypeToken<>() { };
        String json = """
                [["east","west"],[],["north"]]
                """;

        List<String>[] routes = gson.fromJson(json, type);

        assertThat(routes).hasSize(3);
        assertThat(routes[0]).containsExactly("east", "west");
        assertThat(routes[1]).isEmpty();
        assertThat(routes[2]).containsExactly("north");
        assertThat(JsonParser.parseString(gson.toJson(routes, type.getType())))
                .isEqualTo(JsonParser.parseString(json));
    }
}
