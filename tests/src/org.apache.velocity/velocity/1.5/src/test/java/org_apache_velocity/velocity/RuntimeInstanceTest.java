/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RuntimeInstanceTest {
    @TempDir
    Path templateDirectory;

    @Test
    void initializesDefaultsDirectivesAndRendersTemplate() throws Exception {
        Files.write(templateDirectory.resolve("names.vm"), List.of("#foreach($name in $names)$name;#end"));

        RuntimeInstance runtime = new RuntimeInstance();
        runtime.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, new NullLogSystem());
        runtime.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, templateDirectory.toString());
        runtime.setProperty(RuntimeConstants.VM_LIBRARY, "");

        runtime.init();

        assertThat(runtime.isInitialized()).isTrue();
        assertThat(runtime.createNewParser()).isNotNull();

        VelocityContext context = new VelocityContext();
        context.put("names", List.of("Ada", "Bob"));
        StringWriter writer = new StringWriter();

        Template template = runtime.getTemplate("names.vm");
        template.merge(context, writer);

        assertThat(writer.toString()).isEqualTo("Ada;Bob;");
    }

    @Test
    void reportsUserDirectiveThatDoesNotImplementDirectiveInterface() throws Exception {
        RuntimeInstance runtime = new RuntimeInstance();
        runtime.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, new NullLogSystem());
        runtime.setProperty(RuntimeConstants.VM_LIBRARY, "");
        runtime.addProperty("userdirective", String.class.getName());

        runtime.init();

        assertThat(runtime.isInitialized()).isTrue();
        assertThat(runtime.createNewParser()).isNotNull();
    }
}
