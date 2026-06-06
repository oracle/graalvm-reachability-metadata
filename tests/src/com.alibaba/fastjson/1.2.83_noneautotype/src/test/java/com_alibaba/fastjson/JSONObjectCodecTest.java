/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

public class JSONObjectCodecTest {
    @Test
    void serializesOrgJsonObjectThroughFastjsonPublicApi() {
        JSONObject object = new JSONObject();
        object.put("name", "fastjson");
        object.put("count", 2);

        String json = JSON.toJSONString(object);

        assertThat(json).contains("\"name\":\"fastjson\"");
        assertThat(json).contains("\"count\":2");
    }
}
