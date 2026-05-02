/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.dev.Compiler;
import com.google.gwt.dev.CompilerOptionsImpl;
import com.google.gwt.dev.util.log.PrintWriterTreeLogger;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public class MessagesMethodCreatorTest {
    private static final String MODULE_NAME =
            "org_gwtproject.gwt_user.messagesmethodcreator.MessagesMethodCreatorCoverage";

    @Test
    public void compilesPluralMessagesForLocalizedLocale()
            throws IOException, UnableToCompleteException {
        Path outputDirectory = Files.createTempDirectory("gwt-messages-method-creator-test");
        CompilerOptionsImpl options = createCompilerOptions(outputDirectory);

        ByteArrayOutputStream logBuffer = new ByteArrayOutputStream();
        PrintWriterTreeLogger logger = new PrintWriterTreeLogger(new PrintWriter(logBuffer, true));
        logger.setMaxDetail(TreeLogger.ERROR);

        try {
            assertThat(Compiler.compile(logger, options))
                    .as(logBuffer.toString(StandardCharsets.UTF_8))
                    .isTrue();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static CompilerOptionsImpl createCompilerOptions(Path outputDirectory)
            throws IOException {
        Path warDirectory = Files.createDirectories(outputDirectory.resolve("war"));
        Path workDirectory = Files.createDirectories(outputDirectory.resolve("work"));
        Path generatedDirectory = Files.createDirectories(outputDirectory.resolve("generated"));
        Path extraDirectory = Files.createDirectories(outputDirectory.resolve("extra"));
        Path deployDirectory = Files.createDirectories(outputDirectory.resolve("deploy"));

        CompilerOptionsImpl options = new CompilerOptionsImpl();
        options.addModuleName(MODULE_NAME);
        options.setLogLevel(TreeLogger.ERROR);
        options.setLocalWorkers(1);
        options.setOptimizationLevel(0);
        options.setWarDir(warDirectory.toFile());
        options.setWorkDir(workDirectory.toFile());
        options.setGenDir(generatedDirectory.toFile());
        options.setExtraDir(extraDirectory.toFile());
        options.setDeployDir(deployDirectory.toFile());
        options.setPropertyValues("user.agent", Collections.singletonList("safari"));
        options.setPropertyValues("locale", Collections.singletonList("fr"));
        return options;
    }
}
