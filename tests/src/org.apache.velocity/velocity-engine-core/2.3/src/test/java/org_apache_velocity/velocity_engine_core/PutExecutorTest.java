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
import org.apache.velocity.util.ExtProperties;
import org.junit.jupiter.api.Test;

public class PutExecutorTest {
    @Test
    void assignsPropertyThroughPutMethod() {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.init();

        ExtProperties properties = new ExtProperties();
        VelocityContext context = new VelocityContext();
        context.put("properties", properties);
        StringWriter writer = new StringWriter();

        boolean rendered = velocityEngine.evaluate(
                context,
                writer,
                "put-method-property-assignment",
                "#set($properties.applicationName = 'Velocity')");

        assertThat(rendered).isTrue();
        assertThat(writer).hasToString("");
        assertThat(properties.getString("applicationName")).isEqualTo("Velocity");
    }
}
