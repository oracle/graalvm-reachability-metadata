/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Constructor;
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

import static javassist.bytecode.Opcode.AASTORE;
import static javassist.bytecode.Opcode.ARETURN;
import static javassist.bytecode.Opcode.DUP;
import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous4Test {
    private static final String BRIDGE_CLASS_NAME = "javassist.util.proxy.SecurityActionsAnonymous4CoverageBridge";
    private static final String SECURITY_ACTION_CLASS_NAME = "javassist.util.proxy.SecurityActions$4";

    @Test
    void privilegedExceptionActionReadsDeclaredConstructor() throws Exception {
        try {
            Callable<?> callable = (Callable<?>) createBridgeClass().newInstance();
            Constructor<?> constructor = (Constructor<?>) callable.call();

            assertThat(constructor.getDeclaringClass()).isEqualTo(StringBuilder.class);
            assertThat(constructor.getParameterTypes()).containsExactly(String.class);
        } catch (Error error) {
            verifyUnsupportedDynamicClassLoading(error);
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
                SecurityActionsAnonymous4Test.class.getClassLoader(),
                SecurityActionsAnonymous4Test.class.getProtectionDomain());
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

        Bytecode bytecode = new Bytecode(constPool, 7, 1);
        bytecode.addNew(SECURITY_ACTION_CLASS_NAME);
        bytecode.addOpcode(DUP);
        bytecode.addLdc(constPool.addClassInfo(StringBuilder.class.getName()));
        bytecode.addIconst(1);
        bytecode.addAnewarray(Class.class.getName());
        bytecode.addOpcode(DUP);
        bytecode.addIconst(0);
        bytecode.addLdc(constPool.addClassInfo(String.class.getName()));
        bytecode.addOpcode(AASTORE);
        bytecode.addInvokespecial(
                SECURITY_ACTION_CLASS_NAME,
                "<init>",
                "(Ljava/lang/Class;[Ljava/lang/Class;)V");
        bytecode.addInvokestatic(
                AccessController.class.getName(),
                "doPrivileged",
                "(Ljava/security/PrivilegedExceptionAction;)Ljava/lang/Object;");
        bytecode.addOpcode(ARETURN);

        method.addAttribute(bytecode.toCodeAttribute());
        classFile.addMethod(method);
    }

    private static void verifyUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
