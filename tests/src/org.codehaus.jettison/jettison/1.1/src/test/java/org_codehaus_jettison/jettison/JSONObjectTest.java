/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jettison.jettison;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JSONObjectTest {
    @Test
    void constructsObjectFromSelectedPublicFields() throws JSONException {
        Integer source = Integer.valueOf(7);

        JSONObject object = new JSONObject(source, new String[] {"MAX_VALUE", "TYPE", "missing"});

        assertThat(object.getInt("MAX_VALUE")).isEqualTo(Integer.MAX_VALUE);
        assertThat(object.get("TYPE")).isSameAs(Integer.TYPE);
        assertThat(object.has("missing")).isFalse();
    }
}
