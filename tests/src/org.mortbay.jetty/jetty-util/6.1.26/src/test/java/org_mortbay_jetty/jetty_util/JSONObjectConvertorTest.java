/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mortbay.util.ajax.JSON;
import org.mortbay.util.ajax.JSONObjectConvertor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class JSONObjectConvertorTest {
    @Test
    public void serializesGettersAndExcludesSelectedProperties() {
        JSON json = new JSON();
        json.addConvertor(Account.class, new JSONObjectConvertor(false, new String[]{"secret"}));
        Map<?, ?> result = (Map<?, ?>) json.fromJSON(json.toJSON(new Account()));
        assertEquals("reader", result.get("name"));
        assertEquals(true, result.get("active"));
        assertFalse(result.containsKey("secret"));
        assertFalse(result.containsKey("class"));
    }

    public static class Account {
        public String getName() {
            return "reader";
        }

        public boolean isActive() {
            return true;
        }

        public String getSecret() {
            return "private";
        }
    }
}
