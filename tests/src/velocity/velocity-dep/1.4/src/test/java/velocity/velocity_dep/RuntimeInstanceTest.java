/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

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
    void initializesDefaultRuntimeServicesAndRendersTemplate() throws Exception {
        Files.write(templateDirectory.resolve("names.vm"), Arrays.asList("#foreach($name in $names)$name;#end"));

        final RuntimeInstance runtime = new RuntimeInstance();
        runtime.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, new NullLogSystem());
        runtime.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, templateDirectory.toString());
        runtime.setProperty(RuntimeConstants.VM_LIBRARY, "");

        runtime.init();

        assertThat(runtime.getUberspect()).isNotNull();
        assertThat(runtime.getLoaderNameForResource("names.vm")).contains("FileResourceLoader");

        final VelocityContext context = new VelocityContext();
        context.put("names", Arrays.asList("Ada", "Bob"));
        final StringWriter writer = new StringWriter();

        final Template template = runtime.getTemplate("names.vm");
        template.merge(context, writer);

        assertThat(writer.toString()).isEqualTo("Ada;Bob;");
    }
}
