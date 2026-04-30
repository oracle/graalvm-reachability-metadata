/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import org.codehaus.commons.compiler.AbstractJavaSourceClassLoader;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractJavaSourceClassLoaderTest {
    private static String[] invokedArguments;

    @Test
    void loadsClassAndInvokesItsMainMethod() throws Exception {
        invokedArguments = null;
        TestJavaSourceClassLoader.resetLastCreated();
        Path sourceDirectory = Files.createTempDirectory("commons-compiler-source-path");

        try {
            AbstractJavaSourceClassLoader.main(new String[] {
                "-sourcepath", sourceDirectory.toString(),
                "-encoding", "UTF-8",
                "-g",
                AbstractJavaSourceClassLoaderTest.class.getName(),
                "alpha",
                "beta"
            });

            TestJavaSourceClassLoader loader = TestJavaSourceClassLoader.lastCreated();

            assertThat(invokedArguments).containsExactly("alpha", "beta");
            assertThat(loader.loadedClassName()).isEqualTo(AbstractJavaSourceClassLoaderTest.class.getName());
            assertThat(loader.sourcePath()).containsExactly(sourceDirectory.toFile());
            assertThat(loader.characterEncoding()).isEqualTo("UTF-8");
            assertThat(loader.debugLines()).isTrue();
            assertThat(loader.debugVars()).isTrue();
            assertThat(loader.debugSource()).isTrue();
        } finally {
            Files.deleteIfExists(sourceDirectory);
        }
    }

    public static void main(String[] args) {
        invokedArguments = args.clone();
    }
}

class TestJavaSourceClassLoader extends AbstractJavaSourceClassLoader {
    private static TestJavaSourceClassLoader lastCreated;

    private File[] sourcePath = new File[0];
    private String characterEncoding;
    private boolean debugLines;
    private boolean debugVars;
    private boolean debugSource;
    private String loadedClassName;

    TestJavaSourceClassLoader() {
        lastCreated = this;
    }

    static void resetLastCreated() {
        lastCreated = null;
    }

    static TestJavaSourceClassLoader lastCreated() {
        return lastCreated;
    }

    File[] sourcePath() {
        return sourcePath.clone();
    }

    String characterEncoding() {
        return characterEncoding;
    }

    boolean debugLines() {
        return debugLines;
    }

    boolean debugVars() {
        return debugVars;
    }

    boolean debugSource() {
        return debugSource;
    }

    String loadedClassName() {
        return loadedClassName;
    }

    @Override
    public void setSourcePath(File[] sourcePath) {
        this.sourcePath = sourcePath.clone();
    }

    @Override
    public void setSourceFileCharacterEncoding(String optionalCharacterEncoding) {
        this.characterEncoding = optionalCharacterEncoding;
    }

    @Override
    public void setDebuggingInfo(boolean lines, boolean vars, boolean source) {
        debugLines = lines;
        debugVars = vars;
        debugSource = source;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        loadedClassName = name;
        if (AbstractJavaSourceClassLoaderTest.class.getName().equals(name)) {
            return AbstractJavaSourceClassLoaderTest.class;
        }
        return super.loadClass(name);
    }
}
