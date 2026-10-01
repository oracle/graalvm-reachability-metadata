/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_minidev.json_smart;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import net.minidev.json.JSONValue;
import org.junit.jupiter.api.Test;

public class DefaultMapperCollectionTest {
    @Test
    void parsesObjectIntoConcreteMapImplementation() {
        HashMap<?, ?> parsed = JSONValue.parse(
                "{\"name\":\"json-smart\",\"enabled\":true}", HashMap.class);

        assertThat(parsed).isInstanceOf(HashMap.class);
        assertThat(parsed.get("name")).isEqualTo("json-smart");
        assertThat(parsed.get("enabled")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void parsesArrayIntoConcreteListImplementation() {
        ArrayList<?> parsed = JSONValue.parse("[\"alpha\",2,true]", ArrayList.class);

        assertThat(parsed).isInstanceOf(ArrayList.class);
        assertThat(parsed).hasSize(3);
        assertThat(parsed.get(0)).isEqualTo("alpha");
        assertThat(parsed.get(1)).isEqualTo(Integer.valueOf(2));
        assertThat(parsed.get(2)).isEqualTo(Boolean.TRUE);
    }
}
