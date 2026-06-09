/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import javassist.bytecode.AccessFlag;
import javassist.bytecode.Bytecode;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.tools.web.Viewer;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ViewerTest {
    private static final String GENERATED_APPLICATION_NAME =
            "example.ViewerGeneratedApplication";

    @Test
    void runsGeneratedMainClassFetchedByViewer() throws Throwable {
        byte[] bytecode = makeGeneratedApplicationBytecode();
        ByteArrayViewer viewer = new ByteArrayViewer(
                "127.0.0.1", 0, GENERATED_APPLICATION_NAME, bytecode);

        try {
            viewer.run(GENERATED_APPLICATION_NAME, new String[] {"left", "right"});
            assertThat(viewer.fetchedClasses()).isEqualTo(1);
        } catch (Error error) {
            rethrowUnlessUnsupportedDynamicClassLoading(error);
        }
    }

    @Test
    void loadsSystemClassesThroughViewerSystemLookup() throws Exception {
        Viewer viewer = new ByteArrayViewer("127.0.0.1", 0, "unused", new byte[0]);

        Class<?> loadedClass = viewer.loadClass(String.class.getName());

        assertThat(loadedClass).isSameAs(String.class);
    }

    private static byte[] makeGeneratedApplicationBytecode() throws Exception {
        ClassFile classFile = new ClassFile(
                false, GENERATED_APPLICATION_NAME, Object.class.getName());
        classFile.setAccessFlags(AccessFlag.PUBLIC);
        ConstPool constPool = classFile.getConstPool();
        MethodInfo main = new MethodInfo(constPool, "main", "([Ljava/lang/String;)V");
        main.setAccessFlags(AccessFlag.PUBLIC | AccessFlag.STATIC);
        Bytecode code = new Bytecode(constPool, 0, 1);
        code.addReturn(null);
        main.setCodeAttribute(code.toCodeAttribute());
        classFile.addMethod(main);

        ByteArrayOutputStream bytecode = new ByteArrayOutputStream();
        classFile.write(new DataOutputStream(bytecode));
        return bytecode.toByteArray();
    }

    private static void rethrowUnlessUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static final class ByteArrayViewer extends Viewer {
        private final String className;
        private final byte[] bytecode;
        private int fetchedClasses;

        private ByteArrayViewer(String host, int port, String className, byte[] bytecode) {
            super(host, port);
            this.className = className;
            this.bytecode = bytecode;
        }

        private int fetchedClasses() {
            return fetchedClasses;
        }

        @Override
        protected byte[] fetchClass(String requestedClassName) {
            if (className.equals(requestedClassName)) {
                fetchedClasses++;
                return bytecode;
            }
            return null;
        }
    }
}
