/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.implementation.bytecode.constant.TextConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AdviceInnerOffsetMappingInnerForStackManipulationInnerOfAnnotationPropertyTest {
    @Test
    void bindsAnnotationPropertyAsTypedConstant() {
        Advice.OffsetMapping.Factory<TestFixtures.Label> factory =
                Advice.OffsetMapping.ForStackManipulation.OfAnnotationProperty.of(TestFixtures.Label.class, "value");
        ParameterDescription.InDefinedShape parameter = TestFixtures.method("accept").getParameters().getOnly();
        AnnotationDescription.Loadable<TestFixtures.Label> annotation = AnnotationDescription.Builder.ofType(TestFixtures.Label.class)
                .define("value", "injected").build().prepare(TestFixtures.Label.class);
        Advice.OffsetMapping mapping = factory.make(parameter, annotation, Advice.OffsetMapping.Factory.AdviceType.INLINING);
        assertEquals(new Advice.OffsetMapping.ForStackManipulation(new TextConstant("injected"),
                TypeDescription.STRING.asGenericType(), parameter.getType(), Assigner.Typing.STATIC), mapping);
        assertThrows(IllegalArgumentException.class,
                () -> Advice.OffsetMapping.ForStackManipulation.OfAnnotationProperty.of(TestFixtures.Label.class, "missing"));
    }
}
