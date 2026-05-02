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

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public class UiBinderWriterTest {
    private static final String MODULE_NAME =
            "org_gwtproject.gwt_user.uibinder.UiBinderWriterCoverage";

    @Test
    public void compilesUiRendererTemplateWithEventDispatch()
            throws IOException, UnableToCompleteException {
        Path outputDirectory = Files.createTempDirectory("gwt-uibinder-writer-test");
        CompilerOptionsImpl options = createCompilerOptions(outputDirectory);

        try {
            assertThat(Compiler.compile(TreeLogger.NULL, options)).isTrue();
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
        return options;
    }
}
