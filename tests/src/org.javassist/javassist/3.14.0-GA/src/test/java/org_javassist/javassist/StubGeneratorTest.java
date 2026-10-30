/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Set;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.ClassFile;
import javassist.tools.rmi.StubGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StubGeneratorTest {
    @Test
    void emitsClientStubClassFilesWithoutLoadingThem() throws Exception {
        ClassPool pool = TestSupport.classPool();
        pool.appendSystemPath();
        pool.insertClassPath(new ClassClassPath(RmiCatalog.class));
        StubGenerator generator = new StubGenerator();
        generator.start(pool);
        String name = RmiCatalog.class.getName();
        assertFalse(generator.isProxyClass(name));
        assertTrue(generator.makeProxyClass(RmiCatalog.class));
        assertTrue(generator.isProxyClass(name));
        assertFalse(generator.makeProxyClass(RmiCatalog.class));

        CtClass stub = pool.get(name);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(stub.toBytecode()))) {
            ClassFile file = new ClassFile(input);
            assertEquals(name, file.getName());
            assertEquals("java.lang.Object", file.getSuperclass());
            assertEquals(Set.of("java.io.Serializable", "javassist.tools.rmi.Proxy"),
                    Set.of(file.getInterfaces()));
            assertEquals("()I", file.getMethod("_getObjectId").getDescriptor());
            assertEquals("(Ljava/lang/String;)Ljava/lang/String;", file.getMethod("echo").getDescriptor());
            assertNotNull(file.getMethod("echo").getCodeAttribute());
            assertEquals("(Lorg_javassist/javassist/RmiCatalog;)Z", file.getMethod("sameCatalog").getDescriptor());
        }
    }
}
