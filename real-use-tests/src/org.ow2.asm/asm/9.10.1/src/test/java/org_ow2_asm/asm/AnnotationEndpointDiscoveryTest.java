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

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Discovers annotation-based HTTP endpoints for an application's routing subsystem. */
public class AnnotationEndpointDiscoveryTest {
    @Test
    void discoversEndpointRouteAndHttpMethod() throws IOException {
        EndpointMetadata metadata = new EndpointMetadata();
        ClassReader reader = new ClassReader(Type.getInternalName(OrderEndpoint.class));

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.equals(Type.getDescriptor(Route.class))) {
                    return stringValue(value -> metadata.route = value);
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotationDescriptor,
                            boolean visible) {
                        if (annotationDescriptor.equals(Type.getDescriptor(HttpGet.class))) {
                            metadata.methods.add(name);
                            return stringValue(value -> metadata.paths.add(value));
                        }
                        return null;
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals("/orders", metadata.route);
        assertEquals(List.of("list"), metadata.methods);
        assertEquals(List.of("/summary"), metadata.paths);
    }

    private static AnnotationVisitor stringValue(Consumer<String> consumer) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override
            public void visit(String name, Object value) {
                if (name.equals("value")) {
                    consumer.accept((String) value);
                }
            }
        };
    }

    private static final class EndpointMetadata {
        private String route;
        private final List<String> methods = new ArrayList<>();
        private final List<String> paths = new ArrayList<>();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    private @interface Route {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface HttpGet {
        String value();
    }

    @Route("/orders")
    private static final class OrderEndpoint {
        @HttpGet("/summary")
        private List<String> list() {
            return List.of("pending");
        }
    }
}
