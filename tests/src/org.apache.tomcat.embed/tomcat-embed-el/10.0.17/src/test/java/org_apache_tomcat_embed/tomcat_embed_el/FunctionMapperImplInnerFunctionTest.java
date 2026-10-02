/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;

import org.apache.el.lang.FunctionMapperImpl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionMapperImplInnerFunctionTest {

    @Test
    void resolvesMappedFunction() throws ReflectiveOperationException {
        FunctionMapperImpl mapper = new FunctionMapperImpl();
        mapper.mapFunction("lib", "join", FunctionLibrary.class.getMethod("join"));

        Method mappedMethod = mapper.resolveFunction("lib", "join");

        assertThat(mappedMethod).isNotNull();
        assertThat(mappedMethod.getDeclaringClass()).isEqualTo(FunctionLibrary.class);
        assertThat(mappedMethod.getName()).isEqualTo("join");
        assertThat(mappedMethod.getParameterTypes()).isEmpty();
        assertThat(mappedMethod.invoke(null)).isEqualTo("joined");
    }

    @Test
    void resolvesFunctionFromCompatibleSerializedMapping()
            throws IOException, ReflectiveOperationException {
        FunctionMapperImpl mapper = new FunctionMapperImpl();
        mapper.mapFunction("lib", "join", FunctionLibrary.class.getMethod("join"));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new CompatibleObjectOutputStream(bytes)) {
            output.writeObject(mapper);
        }

        FunctionMapperImpl restoredMapper;
        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restoredMapper = (FunctionMapperImpl) input.readObject();
        }
        Method resolvedMethod = restoredMapper.resolveFunction("lib", "join");

        assertThat(resolvedMethod).isNotNull();
        assertThat(resolvedMethod.getDeclaringClass()).isEqualTo(FunctionLibrary.class);
        assertThat(resolvedMethod.getName()).isEqualTo("join");
        assertThat(resolvedMethod.getParameterTypes()).isEmpty();
        assertThat(resolvedMethod.invoke(null)).isEqualTo("joined");
    }

    private static final class CompatibleObjectOutputStream extends ObjectOutputStream {
        private CompatibleObjectOutputStream(ByteArrayOutputStream output) throws IOException {
            super(output);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) {
            if (object instanceof Class<?>[] parameterTypes) {
                String[] typeNames = new String[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    typeNames[i] = parameterTypes[i].getName();
                }
                return typeNames;
            }
            return object;
        }
    }

    public static final class FunctionLibrary {
        private FunctionLibrary() {
        }

        public static String join() {
            return "joined";
        }
    }
}
