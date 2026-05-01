/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.awt.Point;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.Bytecode;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static javassist.bytecode.Opcode.AASTORE;
import static javassist.bytecode.Opcode.DUP;
import static javassist.bytecode.Opcode.POP;
import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsTest {
    private static final String BRIDGE_CLASS_NAME = "javassist.util.proxy.SecurityActionsCoverageBridge";

    @Test
    void bridgeExercisesDeclaredConstructorLookupAndFieldSet() throws Exception {
        try {
            SecurityActionsProbe probe = (SecurityActionsProbe) createBridgeClass().newInstance();
            Point target = new Point(1, 2);

            probe.run(Point.class.getField("x"), target);

            assertThat(target.x).isEqualTo(7);
        } catch (Throwable throwable) {
            verifyUnsupportedDynamicClassLoading(throwable);
        }
    }

    private static Class<?> createBridgeClass() throws Exception {
        ClassFile classFile = new ClassFile(false, BRIDGE_CLASS_NAME, Object.class.getName());
        classFile.setAccessFlags(AccessFlag.PUBLIC);
        classFile.addInterface(SecurityActionsProbe.class.getName());
        addDefaultConstructor(classFile);
        addRunMethod(classFile);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        classFile.write(new DataOutputStream(bytes));

        ClassPool classPool = new ClassPool(true);
        CtClass bridgeClass = classPool.makeClass(new ByteArrayInputStream(bytes.toByteArray()));
        return classPool.toClass(
                bridgeClass,
                SecurityActionsTest.class.getClassLoader(),
                SecurityActionsTest.class.getProtectionDomain());
    }

    private static void addDefaultConstructor(ClassFile classFile) throws Exception {
        ConstPool constPool = classFile.getConstPool();
        MethodInfo constructor = new MethodInfo(constPool, "<init>", "()V");
        constructor.setAccessFlags(AccessFlag.PUBLIC);

        Bytecode bytecode = new Bytecode(constPool, 1, 1);
        bytecode.addAload(0);
        bytecode.addInvokespecial(Object.class.getName(), "<init>", "()V");
        bytecode.addReturn(null);

        constructor.addAttribute(bytecode.toCodeAttribute());
        classFile.addMethod(constructor);
    }

    private static void addRunMethod(ClassFile classFile) throws Exception {
        ConstPool constPool = classFile.getConstPool();
        MethodInfo method = new MethodInfo(constPool, "run", "(Ljava/lang/reflect/Field;Ljava/awt/Point;)V");
        method.setAccessFlags(AccessFlag.PUBLIC);

        Bytecode bytecode = new Bytecode(constPool, 5, 3);
        addDeclaredConstructorLookup(bytecode, constPool);
        addFieldSet(bytecode);
        bytecode.addReturn(null);

        method.addAttribute(bytecode.toCodeAttribute());
        classFile.addMethod(method);
    }

    private static void addDeclaredConstructorLookup(Bytecode bytecode, ConstPool constPool) {
        bytecode.addLdc(constPool.addClassInfo(StringBuilder.class.getName()));
        bytecode.addIconst(1);
        bytecode.addAnewarray(Class.class.getName());
        bytecode.addOpcode(DUP);
        bytecode.addIconst(0);
        bytecode.addLdc(constPool.addClassInfo(String.class.getName()));
        bytecode.addOpcode(AASTORE);
        bytecode.addInvokestatic(
                "javassist.util.proxy.SecurityActions",
                "getDeclaredConstructor",
                "(Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/reflect/Constructor;");
        bytecode.addOpcode(POP);
    }

    private static void addFieldSet(Bytecode bytecode) {
        bytecode.addAload(1);
        bytecode.addAload(2);
        bytecode.addIconst(7);
        bytecode.addInvokestatic(Integer.class.getName(), "valueOf", "(I)Ljava/lang/Integer;");
        bytecode.addInvokestatic(
                "javassist.util.proxy.SecurityActions",
                "set",
                "(Ljava/lang/reflect/Field;Ljava/lang/Object;Ljava/lang/Object;)V");
    }

    private static void verifyUnsupportedDynamicClassLoading(Throwable throwable) {
        if (!NativeImageSupport.isUnsupportedFeatureError(throwable)) {
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (throwable instanceof Error error) {
                throw error;
            }
            throw new AssertionError(throwable);
        }
    }

    public interface SecurityActionsProbe {
        void run(Field field, Point target) throws Exception;
    }
}
