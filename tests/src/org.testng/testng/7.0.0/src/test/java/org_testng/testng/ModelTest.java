/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.testng.mustache.Model;

public class ModelTest {
    @Test
    void resolvesPublicFieldFromPushedSubModel() {
        Map<String, Object> rootModel = new HashMap<>();
        rootModel.put("name", "root value");
        Model model = new Model(rootModel);

        model.push("item", new ModelFieldSource("field value"));

        assertThat(model.getTopSubModel()).isEqualTo("item");
        assertThat(model.resolveValue("name").get()).isEqualTo("field value");
        assertThat(model.resolveValueToString("name")).isEqualTo("field value");

        model.popSubModel();
        assertThat(model.resolveValue("name").get()).isEqualTo("root value");
    }

    public static final class ModelFieldSource {
        public final String name;

        ModelFieldSource(String name) {
            this.name = name;
        }
    }
}
