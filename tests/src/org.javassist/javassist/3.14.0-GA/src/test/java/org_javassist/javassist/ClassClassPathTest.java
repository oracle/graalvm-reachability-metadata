/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import java.io.DataInputStream;
import java.io.InputStream;
import java.net.URL;

import javassist.ClassClassPath;
import javassist.ClassPath;
import javassist.ClassPool;
import javassist.bytecode.ClassFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ClassClassPathTest {
    @Test
    void readsPlatformBytecodeFromTheDefaultSystemSearchPath() throws Exception {
        ClassPool pool = new ClassPool(false);
        ClassPath path = pool.appendSystemPath();
        try (InputStream input = path.openClassfile("java.lang.Object")) {
            assertNotNull(input);
            ClassFile file = new ClassFile(new DataInputStream(input));
            assertEquals("java.lang.Object", file.getName());
            assertNotNull(file.getMethod("toString"));
            assertEquals("java.lang.Object.class", path.toString());
        } finally {
            pool.removeClassPath(path);
            path.close();
        }
    }

    @Test
    void locatesClassResourcesAndReadsTheirBytecode() throws Exception {
        ClassClassPath path = new ClassClassPath(ClassFile.class);
        String name = "javassist.bytecode.ClassFile";
        try {
            URL location = path.find(name);
            assertNotNull(location);
            try (InputStream input = path.openClassfile(name)) {
                assertNotNull(input);
                ClassFile file = new ClassFile(new DataInputStream(input));
                assertEquals(name, file.getName());
                assertEquals("java.lang.Object", file.getSuperclass());
                assertNotNull(file.getMethod("getName"));
            }
            try (InputStream input = location.openStream()) {
                assertEquals(name, new ClassFile(new DataInputStream(input)).getName());
            }
            assertNull(path.find("example.AbsentClass"));
        } finally {
            path.close();
        }
    }
}
