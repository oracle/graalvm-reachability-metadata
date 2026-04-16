/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.immutables.value.internal.$generator$;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.immutables.value.Value;
import org.junit.jupiter.api.Test;

@Target(ElementType.TYPE_USE)
@Retention(RetentionPolicy.CLASS)
@interface TypeUseMarker {
}

@Value.Immutable
interface GetTypeAnnotationsValue {
    @TypeUseMarker
    String value();
}

class $AnnotationMirrors$GetTypeAnnotationsTest {
    @Test
    void generatesAnImmutableImplementationForTypeUseAnnotatedAttributes() {
        ImmutableGetTypeAnnotationsValue value = ImmutableGetTypeAnnotationsValue.builder()
                .value("covered")
                .build();

        assertThat(value.value()).isEqualTo("covered");
    }
}
