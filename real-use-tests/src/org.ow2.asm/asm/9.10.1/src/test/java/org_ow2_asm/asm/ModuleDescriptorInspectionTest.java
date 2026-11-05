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
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Inspects Java module descriptors for an application's plugin compatibility subsystem. */
public class ModuleDescriptorInspectionTest {
    @Test
    void readsModuleRequirementsExportsOpensAndServices() {
        ClassReader reader = new ClassReader(moduleDescriptor());
        ModuleDetails details = new ModuleDetails();

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public ModuleVisitor visitModule(String name, int access, String version) {
                details.name = name;
                details.access = access;
                details.version = version;
                return new ModuleVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitRequire(String module, int access, String version) {
                        details.requires.add(module + ":" + access + ":" + version);
                    }

                    @Override
                    public void visitExport(String packageName, int access, String... modules) {
                        details.exports.add(packageName + ":" + access + ":" + String.join(",", modules));
                    }

                    @Override
                    public void visitOpen(String packageName, int access, String... modules) {
                        details.opens.add(packageName + ":" + access + ":" + String.join(",", modules));
                    }

                    @Override
                    public void visitUse(String service) {
                        details.uses.add(service);
                    }

                    @Override
                    public void visitProvide(String service, String... implementations) {
                        details.providers.add(service + ":" + String.join(",", implementations));
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals("com.example.orders", details.name);
        assertEquals(Opcodes.ACC_OPEN, details.access);
        assertEquals("9.10", details.version);
        assertEquals(List.of("java.base:" + Opcodes.ACC_MANDATED + ":null",
                "com.example.logging:0:2.4"), details.requires);
        assertEquals(List.of("com/example/orders/api:0:com.example.client"), details.exports);
        assertEquals(List.of("com/example/orders/internal:0:com.example.framework"), details.opens);
        assertEquals(List.of("com.example.orders.spi.OrderCodec"), details.uses);
        assertEquals(List.of("com.example.orders.spi.OrderCodec:com.example.orders.JsonOrderCodec"),
                details.providers);
    }

    private static byte[] moduleDescriptor() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_MODULE, "module-info", null, null, null);
        ModuleVisitor module = writer.visitModule("com.example.orders", Opcodes.ACC_OPEN, "9.10");
        module.visitRequire("java.base", Opcodes.ACC_MANDATED, null);
        module.visitRequire("com.example.logging", 0, "2.4");
        module.visitExport("com/example/orders/api", 0, "com.example.client");
        module.visitOpen("com/example/orders/internal", 0, "com.example.framework");
        module.visitUse("com.example.orders.spi.OrderCodec");
        module.visitProvide("com.example.orders.spi.OrderCodec", "com.example.orders.JsonOrderCodec");
        module.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static final class ModuleDetails {
        private int access;
        private String name;
        private String version;
        private final List<String> requires = new ArrayList<>();
        private final List<String> exports = new ArrayList<>();
        private final List<String> opens = new ArrayList<>();
        private final List<String> uses = new ArrayList<>();
        private final List<String> providers = new ArrayList<>();
    }
}
