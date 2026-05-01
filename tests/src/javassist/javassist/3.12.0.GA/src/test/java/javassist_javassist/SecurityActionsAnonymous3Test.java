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
import java.lang.reflect.Method;
import java.security.AccessController;
import java.util.concurrent.Callable;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.Bytecode;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static javassist.bytecode.Opcode.ARETURN;
import static javassist.bytecode.Opcode.DUP;
import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous3Test {
    private static final String BRIDGE_CLASS_NAME = "javassist.util.proxy.SecurityActionsAnonymous3CoverageBridge";
    private static final String SECURITY_ACTION_CLASS_NAME = "javassist.util.proxy.SecurityActions$3";

    @Test
    void privilegedExceptionActionReadsDeclaredMethod() throws Exception {
        try {
            Callable<?> callable = (Callable<?>) createBridgeClass().newInstance();
            Method method = (Method) callable.call();

            assertThat(method.getName()).isEqualTo("getLocation");
            assertThat(method.getParameterTypes()).isEmpty();
            assertThat(method.getDeclaringClass()).isEqualTo(Point.class);
        } catch (Throwable throwable) {
            verifyUnsupportedDynamicClassLoading(throwable);
        }
    }

    private static Class<?> createBridgeClass() throws Exception {
        ClassFile classFile = new ClassFile(false, BRIDGE_CLASS_NAME, Object.class.getName());
        classFile.setAccessFlags(AccessFlag.PUBLIC);
        classFile.addInterface(Callable.class.getName());
        addDefaultConstructor(classFile);
        addCallMethod(classFile);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        classFile.write(new DataOutputStream(bytes));

        ClassPool classPool = new ClassPool(true);
        CtClass bridgeClass = classPool.makeClass(new ByteArrayInputStream(bytes.toByteArray()));
        return classPool.toClass(
                bridgeClass,
                SecurityActionsAnonymous3Test.class.getClassLoader(),
                SecurityActionsAnonymous3Test.class.getProtectionDomain());
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

    private static void addCallMethod(ClassFile classFile) throws Exception {
        ConstPool constPool = classFile.getConstPool();
        MethodInfo method = new MethodInfo(constPool, "call", "()Ljava/lang/Object;");
        method.setAccessFlags(AccessFlag.PUBLIC);

        Bytecode bytecode = new Bytecode(constPool, 5, 1);
        bytecode.addNew(SECURITY_ACTION_CLASS_NAME);
        bytecode.addOpcode(DUP);
        bytecode.addLdc(constPool.addClassInfo(Point.class.getName()));
        bytecode.addLdc("getLocation");
        bytecode.addIconst(0);
        bytecode.addAnewarray(Class.class.getName());
        bytecode.addInvokespecial(
                SECURITY_ACTION_CLASS_NAME,
                "<init>",
                "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)V");
        bytecode.addInvokestatic(
                AccessController.class.getName(),
                "doPrivileged",
                "(Ljava/security/PrivilegedExceptionAction;)Ljava/lang/Object;");
        bytecode.addOpcode(ARETURN);

        method.addAttribute(bytecode.toCodeAttribute());
        classFile.addMethod(method);
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
}
