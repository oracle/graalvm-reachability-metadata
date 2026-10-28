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

import javassist.LoaderClassPath;
import javassist.bytecode.ClassFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class LoaderClassPathTest {
    @Test
    void readsLibraryBytecodeThroughAnExistingClassLoader() throws Exception {
        LoaderClassPath path = new LoaderClassPath(ClassFile.class.getClassLoader());
        String name = "javassist.bytecode.ConstPool";
        try {
            URL location = path.find(name);
            assertNotNull(location);
            try (InputStream input = path.openClassfile(name)) {
                assertNotNull(input);
                ClassFile file = new ClassFile(new DataInputStream(input));
                assertEquals(name, file.getName());
                assertNotNull(file.getMethod("addUtf8Info"));
            }
            try (InputStream input = location.openStream()) {
                assertEquals(name, new ClassFile(new DataInputStream(input)).getName());
            }
            assertNull(path.openClassfile("example.AbsentClass"));
        } finally {
            path.close();
        }
    }
}
