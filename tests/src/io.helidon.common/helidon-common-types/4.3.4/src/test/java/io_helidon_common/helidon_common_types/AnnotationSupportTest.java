/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_common.helidon_common_types;

import java.util.Map;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationSupportTest {
    @Test
    void resolvesClassValuedAnnotationPropertyFromClassName() {
        Annotation annotation = Annotation.create(TypeName.create(Deprecated.class),
                                                  Map.of("value", String.class.getName()));

        assertThat(annotation.classValue()).contains(String.class);
    }
}
