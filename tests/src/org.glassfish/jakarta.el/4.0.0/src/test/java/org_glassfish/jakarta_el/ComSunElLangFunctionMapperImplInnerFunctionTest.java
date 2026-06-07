/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish.jakarta_el;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.sun.el.lang.FunctionMapperImpl;

public class ComSunElLangFunctionMapperImplInnerFunctionTest {
    @Test
    void resolvesSerializedFunctionMethod() throws Exception {
        FunctionMapperImpl mapper = new FunctionMapperImpl();
        Method greetingMethod = ComSunElLangFunctionMapperImplInnerFunctionTest.class
                .getMethod("greeting", String.class);
        mapper.addFunction("demo", "greeting", greetingMethod);

        FunctionMapperImpl restoredMapper = deserialize(serialize(mapper));
        Method restoredMethod = restoredMapper.resolveFunction("demo", "greeting");

        assertThat(restoredMethod).isNotNull();
        assertThat(restoredMethod.getDeclaringClass())
                .isSameAs(ComSunElLangFunctionMapperImplInnerFunctionTest.class);
        assertThat(restoredMethod.getName()).isEqualTo("greeting");
    }

    public static String greeting(String name) {
        return "Hello " + name;
    }

    private static byte[] serialize(FunctionMapperImpl mapper) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(mapper);
        }
        return bytes.toByteArray();
    }

    private static FunctionMapperImpl deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (FunctionMapperImpl) input.readObject();
        }
    }
}
