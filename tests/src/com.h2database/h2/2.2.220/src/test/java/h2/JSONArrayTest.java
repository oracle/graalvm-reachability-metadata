/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.util.json.JSONArray;
import org.h2.util.json.JSONString;
import org.h2.util.json.JSONStringSource;
import org.h2.util.json.JSONValue;
import org.h2.util.json.JSONValueTarget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JSONArrayTest {
    @Test
    void convertsParsedValuesToTypedArray() {
        JSONValue jsonValue = JSONStringSource.parse("[\"alpha\", \"beta\", \"gamma\"]", new JSONValueTarget());
        assertThat(jsonValue).isInstanceOf(JSONArray.class);
        JSONArray jsonArray = (JSONArray) jsonValue;

        String[] values = jsonArray.getArray(String.class, value -> ((JSONString) value).getString());

        assertThat(values).isInstanceOf(String[].class).containsExactly("alpha", "beta", "gamma");
    }
}
