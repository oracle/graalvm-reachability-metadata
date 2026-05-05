/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_web.javax_el;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.sun.el.lang.FunctionMapperImpl;

public class FunctionMapperImplInnerFunctionTest {
    @Test
    void getMethodRestoresSerializedMethodDescriptor() throws Exception {
        Method originalMethod = FunctionMapperImplInnerFunctionTest.class.getMethod("join", String.class, String.class);
        FunctionMapperImpl.Function originalFunction = new FunctionMapperImpl.Function("sample", "join", originalMethod);

        FunctionMapperImpl.Function restoredFunction = roundTrip(originalFunction);
        Method restoredMethod = restoredFunction.getMethod();

        assertThat(restoredMethod).isEqualTo(originalMethod);
        assertThat(restoredMethod.invoke(null, "left", "right")).isEqualTo("left:right");
    }

    public static String join(String left, String right) {
        return left + ":" + right;
    }

    private static FunctionMapperImpl.Function roundTrip(FunctionMapperImpl.Function function) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(function);
        }

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (FunctionMapperImpl.Function) in.readObject();
        }
    }
}
