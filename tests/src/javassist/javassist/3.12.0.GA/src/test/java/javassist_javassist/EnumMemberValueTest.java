/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.EnumMemberValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumMemberValueTest {
    @Test
    void toAnnotationTypeResolvesEnumConstantMemberValue() throws Exception {
        ConstPool constPool = new ConstPool(EnumMemberValueTest.class.getName());
        Annotation annotation = new Annotation(FlavorCarrier.class.getName(), constPool);
        EnumMemberValue enumValue = new EnumMemberValue(constPool);
        enumValue.setType(Flavor.class.getName());
        enumValue.setValue(Flavor.SPICY.name());
        annotation.addMemberValue("value", enumValue);

        FlavorCarrier carrier = (FlavorCarrier) annotation.toAnnotationType(
                EnumMemberValueTest.class.getClassLoader(), null);

        assertThat(carrier.value()).isSameAs(Flavor.SPICY);
        assertThat(enumValue.toString()).isEqualTo(Flavor.class.getName() + "." + Flavor.SPICY.name());
    }

    public @interface FlavorCarrier {
        Flavor value();
    }

    public enum Flavor {
        MILD,
        SPICY
    }
}
