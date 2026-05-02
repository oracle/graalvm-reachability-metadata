/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.taskdefs.compilers.Kjc;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import at.dms.kjc.Main;

import static org.assertj.core.api.Assertions.assertThat;

public class KjcTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void executeResolvesCompilerGeneratedClassLiteralForCompileSignature() throws Exception {
        Main.reset();
        clearCompilerGeneratedClassCache();
        Kjc compiler = new Kjc();
        compiler.setJavac(newJavacTask());

        try {
            boolean compilationSucceeded = compiler.execute();

            assertThat(compilationSucceeded).isTrue();
            assertThat(Main.invocationCount()).isEqualTo(1);
            assertThat(Main.lastArguments())
                    .contains("-d", temporaryDirectory.resolve("classes").toString())
                    .contains("-classpath")
                    .anySatisfy(argument -> assertThat(argument)
                            .contains(temporaryDirectory.resolve("sources").toString()));
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void compilerGeneratedClassLookupResolvesLibraryClass() throws Throwable {
        try {
            Class<?> resolvedClass = GeneratedPackagePeer.lookupCompilerGeneratedClass(
                    Kjc.class.getName());

            assertThat(resolvedClass).isSameAs(Kjc.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private Javac newJavacTask() throws IOException {
        Path sourceDirectory = temporaryDirectory.resolve("sources");
        Path destinationDirectory = temporaryDirectory.resolve("classes");
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(destinationDirectory);
        Files.writeString(sourceDirectory.resolve("Example.java"), "class Example { }\n");

        Project project = new Project();
        project.setBaseDir(temporaryDirectory.toFile());
        project.init();

        Javac javac = new Javac();
        javac.setProject(project);
        javac.setTaskName("javac");
        javac.createSrc().setLocation(sourceDirectory.toFile());
        javac.setDestdir(destinationDirectory.toFile());
        javac.setIncludeantruntime(false);
        javac.setIncludejavaruntime(false);
        return javac;
    }

    private static void clearCompilerGeneratedClassCache()
            throws IllegalAccessException, NoSuchFieldException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                Kjc.class,
                MethodHandles.lookup());
        VarHandle handle = lookup.findStaticVarHandle(
                Kjc.class,
                "array$Ljava$lang$String",
                Class.class);
        handle.set(null);
    }

    private static final class GeneratedPackagePeer {
        static Class<?> lookupCompilerGeneratedClass(String className) throws Throwable {
            return (Class<?>) classLookupMethod().invoke(className);
        }

        private static MethodHandle classLookupMethod() {
            try {
                MethodHandles.Lookup packageLookup = MethodHandles.privateLookupIn(
                        Kjc.class,
                        MethodHandles.lookup());
                MethodHandles.Lookup peerLookup = packageLookup.defineHiddenClass(
                        packagePeerClassBytes(),
                        true);
                return peerLookup.findStatic(
                        peerLookup.lookupClass(),
                        "lookup",
                        MethodType.methodType(Class.class, String.class));
            } catch (IllegalAccessException | IOException | NoSuchMethodException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        private static byte[] packagePeerClassBytes() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(0xCAFEBABE);
            out.writeShort(0);
            out.writeShort(52);
            out.writeShort(13);
            writeUtf8(out, "org/apache/tools/ant/taskdefs/compilers/KjcLookup");
            writeClass(out, 1);
            writeUtf8(out, "java/lang/Object");
            writeClass(out, 3);
            writeUtf8(out, "lookup");
            writeUtf8(out, "(Ljava/lang/String;)Ljava/lang/Class;");
            writeUtf8(out, "Code");
            writeUtf8(out, "org/apache/tools/ant/taskdefs/compilers/Kjc");
            writeClass(out, 8);
            writeUtf8(out, "class$");
            writeNameAndType(out, 10, 6);
            writeMethodRef(out, 9, 11);
            out.writeShort(0x0021);
            out.writeShort(2);
            out.writeShort(4);
            out.writeShort(0);
            out.writeShort(0);
            out.writeShort(1);
            out.writeShort(0x0009);
            out.writeShort(5);
            out.writeShort(6);
            out.writeShort(1);
            out.writeShort(7);
            out.writeInt(17);
            out.writeShort(1);
            out.writeShort(1);
            out.writeInt(5);
            out.writeByte(0x2A);
            out.writeByte(0xB8);
            out.writeShort(12);
            out.writeByte(0xB0);
            out.writeShort(0);
            out.writeShort(0);
            out.writeShort(0);
            return bytes.toByteArray();
        }

        private static void writeUtf8(DataOutputStream out, String value) throws IOException {
            out.writeByte(1);
            out.writeUTF(value);
        }

        private static void writeClass(DataOutputStream out, int nameIndex) throws IOException {
            out.writeByte(7);
            out.writeShort(nameIndex);
        }

        private static void writeNameAndType(
                DataOutputStream out,
                int nameIndex,
                int descriptorIndex) throws IOException {
            out.writeByte(12);
            out.writeShort(nameIndex);
            out.writeShort(descriptorIndex);
        }

        private static void writeMethodRef(
                DataOutputStream out,
                int classIndex,
                int nameAndTypeIndex) throws IOException {
            out.writeByte(10);
            out.writeShort(classIndex);
            out.writeShort(nameAndTypeIndex);
        }
    }
}
