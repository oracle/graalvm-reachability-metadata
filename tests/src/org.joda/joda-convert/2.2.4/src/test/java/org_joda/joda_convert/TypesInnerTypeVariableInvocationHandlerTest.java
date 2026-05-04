/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_joda.joda_convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

import org.joda.convert.StringConvert;
import org.junit.jupiter.api.Test;

public class TypesInnerTypeVariableInvocationHandlerTest {
    @Test
    public void artificialTypeVariablesDelegateSupportedMethodsToBackingImplementation() throws Exception {
        TypeVariable<?> variable = newArtificialTypeVariable(
                TypesInnerTypeVariableInvocationHandlerTest.class,
                "Synthetic",
                Number.class);

        assertEquals("Synthetic", variable.getName());
        assertSame(TypesInnerTypeVariableInvocationHandlerTest.class, variable.getGenericDeclaration());
        assertEquals("Synthetic", variable.toString());
    }

    @Test
    public void handlesParameterizedTypesWithWildcardBounds() {
        StringConvert convert = StringConvert.create();
        ParameterizedType type = convert.convertFromString(
                ParameterizedType.class,
                "java.util.List<? super java.lang.Integer>");

        assertTrue(convert.convertToString(ParameterizedType.class, type).contains("java.lang.Integer"));
    }

    private static TypeVariable<?> newArtificialTypeVariable(
            GenericDeclaration declaration, String name, Type... bounds) throws Exception {
        Method newArtificialTypeVariable = Class.forName("org.joda.convert.Types")
                .getDeclaredMethod(
                        "newArtificialTypeVariable",
                        GenericDeclaration.class,
                        String.class,
                        Type[].class);
        newArtificialTypeVariable.setAccessible(true);
        return (TypeVariable<?>) newArtificialTypeVariable.invoke(null, declaration, name, bounds);
    }
}
