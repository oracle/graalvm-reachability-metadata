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
    void resolvesMethodFromSerializedFunctionMapping()
            throws ClassNotFoundException, IOException, NoSuchMethodException {
        FunctionMapperImpl mapper = new FunctionMapperImpl();
        mapper.mapFunction("lib", "join", FunctionLibrary.class.getMethod("join"));

        FunctionMapperImpl restoredMapper = roundTrip(mapper);
        Method restoredMethod = restoredMapper.resolveFunction("lib", "join");

        assertThat(restoredMethod).isNotNull();
        assertThat(restoredMethod.getDeclaringClass()).isEqualTo(FunctionLibrary.class);
        assertThat(restoredMethod.getName()).isEqualTo("join");
        assertThat(restoredMethod.getParameterTypes()).isEmpty();
    }

    private FunctionMapperImpl roundTrip(FunctionMapperImpl mapper)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(mapper);
        }

        try (ObjectInputStream objectInput =
                new ObjectInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            return (FunctionMapperImpl) objectInput.readObject();
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
