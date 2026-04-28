/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import bsh.ClassPathException;
import bsh.Interpreter;
import bsh.classpath.BshClassPath;
import bsh.classpath.ClassManagerImpl;
import bsh.util.ClassBrowser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JTextArea;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassBrowserTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    public void selectingClassDisplaysPublicConstructorsMethodsAndFields() throws Exception {
        BshClassPath classPath = classPathContaining(Interpreter.class);
        ClassBrowser classBrowser = new ClassBrowser(new StaticClassPathManager(classPath));

        classBrowser.init();
        classBrowser.driveToClass(Interpreter.class.getName());

        JTextArea methodLine = findMethodLine(classBrowser);
        assertThat(methodLine.getText())
                .contains(Interpreter.class.getName())
                .contains("Dir:");
    }

    private BshClassPath classPathContaining(Class<?> mappedClass) throws IOException {
        Path classFile = temporaryDirectory.resolve(mappedClass.getName().replace('.', '/') + ".class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, new byte[] {0});
        URL[] pathComponents = {temporaryDirectory.toUri().toURL()};
        return new BshClassPath("class browser test path", pathComponents);
    }

    private static JTextArea findMethodLine(Container container) {
        JTextArea methodLine = findMethodLineOrNull(container);
        if (methodLine == null) {
            throw new AssertionError("ClassBrowser method line was not found");
        }
        return methodLine;
    }

    private static JTextArea findMethodLineOrNull(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JTextArea) {
                return (JTextArea) component;
            }
            if (component instanceof Container) {
                JTextArea methodLine = findMethodLineOrNull((Container) component);
                if (methodLine != null) {
                    return methodLine;
                }
            }
        }
        return null;
    }

    private static class StaticClassPathManager extends ClassManagerImpl {
        private final BshClassPath classPath;

        StaticClassPathManager(BshClassPath classPath) {
            this.classPath = classPath;
        }

        @Override
        public BshClassPath getClassPath() throws ClassPathException {
            return classPath;
        }
    }
}
