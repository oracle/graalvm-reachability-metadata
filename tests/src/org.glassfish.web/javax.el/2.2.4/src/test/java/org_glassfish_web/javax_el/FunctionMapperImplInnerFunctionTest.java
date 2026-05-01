/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_web.javax_el;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;

import com.sun.el.lang.FunctionMapperImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionMapperImplInnerFunctionTest {

    @Test
    void serializedFunctionMapperResolvesFunctionMethodLazily() throws Exception {
        FunctionMapperImpl mapper = new FunctionMapperImpl();
        Method originalMethod = FunctionMapperImplInnerFunctionTest.class.getMethod(
                "join", String.class, String.class);
        mapper.addFunction("test", "join", originalMethod);

        FunctionMapperImpl restoredMapper = roundTrip(mapper);
        Method restoredMethod = restoredMapper.resolveFunction("test", "join");

        assertThat(restoredMethod).isEqualTo(originalMethod);
        assertThat(restoredMethod.getParameterTypes()).containsExactly(String.class, String.class);
    }

    public static String join(String left, String right) {
        return left + ":" + right;
    }

    private static FunctionMapperImpl roundTrip(FunctionMapperImpl mapper) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(mapper);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream input = new ObjectInputStream(inputBytes)) {
            return (FunctionMapperImpl) input.readObject();
        }
    }
}
