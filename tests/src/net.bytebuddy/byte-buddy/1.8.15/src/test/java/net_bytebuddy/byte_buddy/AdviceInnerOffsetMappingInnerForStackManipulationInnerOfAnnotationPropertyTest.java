/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.asm.Advice;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.assertj.core.api.Assertions.assertThat;

public class AdviceInnerOffsetMappingInnerForStackManipulationInnerOfAnnotationPropertyTest {
    @Test
    void bindsCustomMappingToAnnotationPropertyByName() {
        Advice.WithCustomMapping customMapping = Advice.withCustomMapping()
                .bindProperty(PropertyBinding.class, "value");

        assertThat(customMapping).isNotNull();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface PropertyBinding {
        String value();
    }
}
