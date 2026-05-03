/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_util_json;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Yytoken.Types;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonObjectTest {
    @Test
    void resolvesEnumValueFromFullyQualifiedName() throws Exception {
        JsonObject object = new JsonObject();
        object.put("token", enumName(Types.LEFT_BRACE));

        Types token = object.getEnum("token");

        assertThat(token).isSameAs(Types.LEFT_BRACE);
    }

    @Test
    void resolvesExistingEnumValueInsteadOfReturningDefault() throws Exception {
        JsonObject object = new JsonObject();
        object.put("token", enumName(Types.RIGHT_SQUARE));

        Types token = object.getEnumOrDefault("token", Types.END);

        assertThat(token).isSameAs(Types.RIGHT_SQUARE);
    }

    private static String enumName(Types token) {
        return Types.class.getName() + "." + token.name();
    }
}
