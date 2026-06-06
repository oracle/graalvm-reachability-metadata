/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.ParserConfig;

import java.util.HashMap;
import org.junit.jupiter.api.Test;

public class DefaultJSONParserTest {
    @Test
    void parseInstantiatesEmptyAutoTypedHashMap() {
        ParserConfig config = new ParserConfig();

        Object value = JSON.parse("{\"@type\":\"java.util.HashMap\"}", config, JSON.DEFAULT_PARSER_FEATURE);

        assertThat(value).isInstanceOf(HashMap.class);
        assertThat((HashMap<?, ?>) value).isEmpty();
    }
}
