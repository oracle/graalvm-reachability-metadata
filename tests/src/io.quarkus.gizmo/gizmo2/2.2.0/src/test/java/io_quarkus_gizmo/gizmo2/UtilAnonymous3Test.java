/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus_gizmo.gizmo2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import io.quarkus.gizmo2.Gizmo;

public class UtilAnonymous3Test {
    private static final String GENERATED_CLASS_NAME = "io_quarkus_gizmo.gizmo2.generated.UtilAnonymous3Target";
    private static final String GENERATED_CLASS_PATH = "io_quarkus_gizmo/gizmo2/generated/UtilAnonymous3Target.class";

    @Test
    void createsLambdaFromFunctionalInterfaceClass() {
        Map<String, byte[]> generatedClasses = new HashMap<>();
        Gizmo gizmo = Gizmo.create(generatedClasses::put);

        gizmo.class_(GENERATED_CLASS_NAME, classCreator -> classCreator.staticMethod("supplier", methodCreator -> {
            methodCreator.returning(Supplier.class);
            methodCreator.body(block -> block.return_(block.lambda(Supplier.class, lambda -> {
                lambda.body(lambdaBlock -> lambdaBlock.return_("created by gizmo"));
            })));
        }));

        assertThat(generatedClasses).containsOnlyKeys(GENERATED_CLASS_PATH);
        byte[] classBytes = generatedClasses.get(GENERATED_CLASS_PATH);
        assertThat(classBytes).isNotEmpty();

        String classFileText = new String(classBytes, StandardCharsets.ISO_8859_1);
        assertThat(classFileText)
                .contains("supplier")
                .contains("Supplier")
                .contains("get")
                .contains("created by gizmo");
    }
}
