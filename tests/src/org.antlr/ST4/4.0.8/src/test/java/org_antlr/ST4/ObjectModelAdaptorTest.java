/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.ST4;

import org.junit.jupiter.api.Test;
import org.stringtemplate.v4.ST;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectModelAdaptorTest {
    @Test
    void rendersObjectPropertiesResolvedByPublicAccessorsAndFields() {
        final ST template = new ST("<model.name>|<model.active>|<model.warnings>|<model.location>");
        template.add("model", new TemplateModel());

        final String rendered = template.render();

        assertThat(rendered).isEqualTo("Ada|true|false|London");
    }

    public static final class TemplateModel {
        public final String location = "London";

        public String getName() {
            return "Ada";
        }

        public boolean isActive() {
            return true;
        }

        public boolean hasWarnings() {
            return false;
        }
    }
}
