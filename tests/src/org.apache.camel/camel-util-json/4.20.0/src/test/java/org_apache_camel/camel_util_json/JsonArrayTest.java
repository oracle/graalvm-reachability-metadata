/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_util_json;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.Yytoken.Types;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonArrayTest {
    @Test
    void resolvesEnumValueFromFullyQualifiedName() throws Exception {
        JsonArray array = new JsonArray();
        array.add(enumName(Types.LEFT_SQUARE));

        Types token = array.getEnum(0);

        assertThat(token).isSameAs(Types.LEFT_SQUARE);
    }

    @Test
    void returnsNullEnumWhenArrayEntryIsNull() throws Exception {
        JsonArray array = new JsonArray();
        array.add(null);

        Types token = array.getEnum(0);

        assertThat(token).isNull();
    }

    private static String enumName(Types token) {
        return Types.class.getName() + "." + token.name();
    }
}
