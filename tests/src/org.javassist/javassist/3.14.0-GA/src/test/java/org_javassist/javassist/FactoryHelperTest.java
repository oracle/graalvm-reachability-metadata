/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javassist.ClassClassPath;
import javassist.bytecode.ClassFile;
import javassist.util.proxy.FactoryHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FactoryHelperTest {
    @TempDir
    Path directory;

    @Test
    void writesAnExistingClassFileWithoutDefiningIt() throws Exception {
        ClassClassPath source = new ClassClassPath(ClassFile.class);
        ClassFile original;
        try (InputStream input = source.openClassfile("javassist.bytecode.ClassFile")) {
            assertNotNull(input);
            original = new ClassFile(new DataInputStream(input));
        } finally {
            source.close();
        }
        FactoryHelper.writeFile(original, directory.toString());
        Path target = directory.resolve("javassist").resolve("bytecode").resolve("ClassFile.class");
        try (DataInputStream input = new DataInputStream(Files.newInputStream(target))) {
            ClassFile restored = new ClassFile(input);
            assertEquals(original.getName(), restored.getName());
            assertEquals(original.getSuperclass(), restored.getSuperclass());
            assertEquals(original.getMethods().size(), restored.getMethods().size());
            assertEquals("()Ljava/lang/String;", restored.getMethod("getName").getDescriptor());
        }
    }
}
