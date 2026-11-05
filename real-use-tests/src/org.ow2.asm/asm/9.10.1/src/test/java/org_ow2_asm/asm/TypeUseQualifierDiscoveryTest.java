/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Discovers type-use qualifiers for an application's dependency injection subsystem. */
public class TypeUseQualifierDiscoveryTest {
    @Test
    void discoversQualifierOnGenericHandlerParameter() throws IOException {
        List<InjectionPoint> injectionPoints = new ArrayList<>();
        ClassReader reader = new ClassReader(Type.getInternalName(OrderHandler.class));

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (!name.equals("handle")) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath,
                            String annotationDescriptor, boolean visible) {
                        TypeReference reference = new TypeReference(typeRef);
                        if (reference.getSort() != TypeReference.METHOD_FORMAL_PARAMETER
                                || !annotationDescriptor.equals(Type.getDescriptor(Qualifier.class))) {
                            return null;
                        }
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String attributeName, Object value) {
                                if (attributeName.equals("value")) {
                                    injectionPoints.add(new InjectionPoint(
                                            reference.getFormalParameterIndex(),
                                            typePath == null ? "" : typePath.toString(),
                                            (String) value));
                                }
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals(List.of(new InjectionPoint(0, "0;", "order-id")), injectionPoints);
    }

    private record InjectionPoint(int parameterIndex, String typePath, String qualifier) {
    }

    private static final class OrderHandler {
        private void handle(List<@Qualifier("order-id") String> orderIds) {
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    private @interface Qualifier {
        String value();
    }
}
