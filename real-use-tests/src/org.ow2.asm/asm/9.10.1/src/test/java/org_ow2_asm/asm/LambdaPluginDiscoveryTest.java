/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Scans lambda linkage metadata for an application's plugin discovery subsystem. */
public class LambdaPluginDiscoveryTest {
    @Test
    void findsPluginFactoryLambdaLinkage() throws IOException {
        List<LambdaCallSite> callSites = new ArrayList<>();

        ClassReader reader = new ClassReader(Type.getInternalName(PluginFactory.class));
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitInvokeDynamicInsn(String invokedName,
                            String invokedDescriptor, Handle bootstrapMethodHandle,
                            Object... bootstrapMethodArguments) {
                        Handle implementation = (Handle) bootstrapMethodArguments[1];
                        callSites.add(new LambdaCallSite(
                                name,
                                invokedName,
                                invokedDescriptor,
                                bootstrapMethodHandle.getOwner(),
                                bootstrapMethodHandle.getName(),
                                implementation.getOwner(),
                                implementation.getName(),
                                implementation.getDesc(),
                                implementation.getTag()));
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(List.of(new LambdaCallSite(
                "create",
                "run",
                "()Ljava/lang/Runnable;",
                "java/lang/invoke/LambdaMetafactory",
                "metafactory",
                "org_ow2_asm/asm/LambdaPluginDiscoveryTest$PluginFactory",
                "runPlugin",
                "()V",
                Opcodes.H_INVOKESTATIC)),
                callSites);
    }

    private static final class PluginFactory {
        private static Runnable create() {
            return PluginFactory::runPlugin;
        }

        private static void runPlugin() {
        }
    }

    private record LambdaCallSite(
            String enclosingMethod,
            String invokedName,
            String invokedDescriptor,
            String bootstrapOwner,
            String bootstrapName,
            String implementationOwner,
            String implementationName,
            String implementationDescriptor,
            int implementationTag) {
    }
}
