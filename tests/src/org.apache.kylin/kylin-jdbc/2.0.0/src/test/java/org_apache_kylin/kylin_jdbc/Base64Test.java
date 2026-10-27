/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.util.Base64;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Base64Test {
    @Test
    void encodesAndDecodesASerializableObject() throws Exception {
        Map<String, Serializable> input = new LinkedHashMap<>();
        input.put("project", "sales");
        input.put("limit", 25);

        Object output = Base64.decodeToObject(Base64.encodeObject((Serializable) input));

        assertThat(output).isEqualTo(input);
    }
}
