/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.h2.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class NullBytecodeSourceCompiler extends SourceCompiler {
    private static final String ANCHOR_CLASS_NAME = "h2.generated.SourceCompilerLoaderAnchor";
    private boolean returnNullBytecode;

    public Class<?> findSystemClassThroughAnonymousLoader(Class<?> systemClass) throws Throwable {
        setJavaSystemCompiler(false);
        setSource(ANCHOR_CLASS_NAME, "Object marker() { return null; }");
        ClassLoader sourceCompilerLoader = getClass(ANCHOR_CLASS_NAME).getClassLoader();

        setSource(systemClass.getName(), "Object value() { return null; }");
        returnNullBytecode = true;

        MethodHandle findClass = MethodHandles.lookup().findVirtual(
                sourceCompilerLoader.getClass(),
                "findClass",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) findClass.invoke(sourceCompilerLoader, systemClass.getName());
    }

    @Override
    byte[] javacCompile(String packageName, String className, String source) {
        if (returnNullBytecode) {
            return null;
        }
        return super.javacCompile(packageName, className, source);
    }
}
