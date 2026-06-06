/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus_gizmo.gizmo2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.quarkus.gizmo2.Gizmo;

public class UtilAnonymous3Test {
    private static final String GENERATED_CLASS_NAME = "io_quarkus_gizmo.gizmo2.generated.UtilAnonymous3Target";
    private static final String GENERATED_CLASS_PATH = "io_quarkus_gizmo/gizmo2/generated/UtilAnonymous3Target.class";
    private static final Pattern ENCODED_CLASS_FILE = Pattern.compile("yv66vg[0-9A-Za-z_-]+=*");

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

        assertThat(generatedClasses.size()).isEqualTo(1);
        assertThat(generatedClasses.containsKey(GENERATED_CLASS_PATH)).isTrue();
        byte[] classBytes = generatedClasses.get(GENERATED_CLASS_PATH);
        assertThat(classBytes).isNotEmpty();

        String classFileText = new String(classBytes, StandardCharsets.ISO_8859_1);
        assertThat(classFileText)
                .contains("supplier")
                .contains("Supplier")
                .contains("defineLambdaCallSite")
                .contains("Base64")
                .contains("defineHiddenClass");

        byte[] lambdaClassBytes = Base64.getUrlDecoder().decode(extractEncodedClassFile(classFileText));
        String lambdaClassFileText = new String(lambdaClassBytes, StandardCharsets.ISO_8859_1);
        assertThat(lambdaClassFileText)
                .contains("$lambda")
                .contains("Supplier")
                .contains("get")
                .contains("created by gizmo");
    }

    private static String extractEncodedClassFile(String classFileText) {
        Matcher matcher = ENCODED_CLASS_FILE.matcher(classFileText);
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }
}
