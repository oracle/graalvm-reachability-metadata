/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import org.apache.velocity.app.FieldMethodizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldMethodizerTest {
    @Test
    void exposesPublicStaticFieldsByNameFromObject() throws Exception {
        FieldMethodizer methodizer = new FieldMethodizer();

        methodizer.addObject(new TemplateConstants());

        assertThat(methodizer.get("TITLE")).isEqualTo("Velocity");
        assertThat(methodizer.get("COUNT")).isEqualTo(3);
        assertThat(methodizer.get("instanceOnly")).isNull();
        assertThat(methodizer.get("missing")).isNull();
    }

    public static class TemplateConstants {
        public static String TITLE = "Velocity";
        public static Integer COUNT = 3;
        public String instanceOnly = "not exposed";
    }
}
