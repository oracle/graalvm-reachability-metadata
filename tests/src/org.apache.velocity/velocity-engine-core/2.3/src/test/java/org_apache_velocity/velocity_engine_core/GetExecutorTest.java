/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity_engine_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.Test;

public class GetExecutorTest {
    @Test
    void resolvesPropertyThroughContextGetMethod() {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.init();

        VelocityContext nestedContext = new VelocityContext();
        nestedContext.put("answer", "resolved through get");

        VelocityContext context = new VelocityContext();
        context.put("lookup", nestedContext);
        StringWriter writer = new StringWriter();

        boolean rendered = velocityEngine.evaluate(
                context,
                writer,
                "context-get-method-property-access",
                "$lookup.answer");

        assertThat(rendered).isTrue();
        assertThat(writer).hasToString("resolved through get");
    }
}
