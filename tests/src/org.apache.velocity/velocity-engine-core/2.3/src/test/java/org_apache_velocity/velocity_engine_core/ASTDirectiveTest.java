/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity_engine_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.util.List;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.Test;

public class ASTDirectiveTest {
    @Test
    void initializesAndRendersBuiltInDirectiveFromParsedTemplate() {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.init();

        VelocityContext context = new VelocityContext();
        context.put("names", List.of("Ada", "Grace", "Katherine"));
        StringWriter writer = new StringWriter();

        boolean rendered = velocityEngine.evaluate(
                context,
                writer,
                "ast-directive-foreach",
                "#foreach($name in $names)$name#end");

        assertThat(rendered).isTrue();
        assertThat(writer).hasToString("AdaGraceKatherine");
    }
}
