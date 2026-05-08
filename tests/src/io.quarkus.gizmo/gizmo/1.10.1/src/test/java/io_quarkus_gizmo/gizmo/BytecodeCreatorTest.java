/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus_gizmo.gizmo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.MethodCreator;

public class BytecodeCreatorTest {
    @Test
    void throwsNewExceptionWithMessage() {
        GeneratedClasses generatedClasses = new GeneratedClasses();

        try (ClassCreator classCreator = ClassCreator.builder()
                .classOutput(generatedClasses)
                .className("io.quarkus.gizmo.generated.MessageThrower")
                .build()) {
            MethodCreator methodCreator = classCreator.getMethodCreator("raise", void.class);

            methodCreator.throwException(IllegalStateException.class, "created by Gizmo");
        }

        assertGeneratedClass(generatedClasses, "io/quarkus/gizmo/generated/MessageThrower");
    }

    @Test
    void throwsNewExceptionWrappingExistingThrowable() {
        GeneratedClasses generatedClasses = new GeneratedClasses();

        try (ClassCreator classCreator = ClassCreator.builder()
                .classOutput(generatedClasses)
                .className("io.quarkus.gizmo.generated.WrappingThrower")
                .build()) {
            MethodCreator methodCreator = classCreator.getMethodCreator("raise", void.class, Throwable.class);

            methodCreator.throwException(IllegalStateException.class, "wrapped by Gizmo",
                    methodCreator.getMethodParam(0));
        }

        assertGeneratedClass(generatedClasses, "io/quarkus/gizmo/generated/WrappingThrower");
    }

    private static void assertGeneratedClass(GeneratedClasses generatedClasses, String className) {
        byte[] generatedClass = generatedClasses.classBytes.get(className);
        assertNotNull(generatedClass);
        assertTrue(generatedClass.length >= 4);
        assertArrayEquals(new byte[] { (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe },
                new byte[] { generatedClass[0], generatedClass[1], generatedClass[2], generatedClass[3] });
    }

    private static final class GeneratedClasses implements ClassOutput {
        private final Map<String, byte[]> classBytes = new LinkedHashMap<>();

        @Override
        public void write(String name, byte[] data) {
            classBytes.put(name, data);
        }
    }
}
