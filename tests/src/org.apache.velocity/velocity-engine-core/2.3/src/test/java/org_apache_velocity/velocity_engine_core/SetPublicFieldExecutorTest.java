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
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.util.introspection.UberspectPublicFields;
import org.junit.jupiter.api.Test;

public class SetPublicFieldExecutorTest {
    @Test
    void assignsValueToPublicFieldFromTemplate() {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.UBERSPECT_CLASSNAME, UberspectPublicFields.class.getName());
        velocityEngine.init();

        PublicFieldHolder holder = new PublicFieldHolder("before");
        VelocityContext context = new VelocityContext();
        context.put("holder", holder);
        StringWriter writer = new StringWriter();

        boolean rendered = velocityEngine.evaluate(
                context,
                writer,
                "public-field-assignment",
                "#set($holder.label = 'after')$holder.label");

        assertThat(rendered).isTrue();
        assertThat(holder.label).isEqualTo("after");
        assertThat(writer).hasToString("after");
    }

    public static class PublicFieldHolder {
        public String label;

        PublicFieldHolder(String label) {
            this.label = label;
        }
    }
}
