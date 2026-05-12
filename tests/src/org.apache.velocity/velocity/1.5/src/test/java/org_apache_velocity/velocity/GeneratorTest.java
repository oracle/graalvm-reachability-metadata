/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.apache.velocity.texen.Generator;
import org.apache.velocity.texen.util.FileUtil;
import org.apache.velocity.texen.util.PropertiesUtil;
import org.apache.velocity.util.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GeneratorTest {
    @TempDir
    Path templateDirectory;

    @Test
    void initializesSingletonFromDefaultTexenPropertiesResource() throws Exception {
        Files.writeString(templateDirectory.resolve("greeting.vm"), "Hello $name");
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, new NullLogSystem());
        velocityEngine.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, templateDirectory.toString());
        velocityEngine.setProperty(RuntimeConstants.VM_LIBRARY, "");
        velocityEngine.init();

        Generator generator = Generator.getInstance();
        generator.setVelocityEngine(velocityEngine);

        VelocityContext context = new VelocityContext();
        context.put("name", "Texen");

        String output = generator.parse("greeting.vm", context);

        assertThat(output).isEqualTo("Hello Texen");
        assertThat(context.get("strings")).isInstanceOf(StringUtils.class);
        assertThat(context.get("files")).isInstanceOf(FileUtil.class);
        assertThat(context.get("properties")).isInstanceOf(PropertiesUtil.class);
    }
}
