/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.AbstractHessianInput;
import com.caucho.hessian.io.AbstractHessianOutput;
import com.caucho.hessian.io.ExtSerializerFactory;
import com.caucho.hessian.io.FieldDeserializer2Factory;
import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;
import com.caucho.hessian.io.JavaDeserializer;
import com.caucho.hessian.io.JavaSerializer;
import com.caucho.hessian.io.SerializerFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class SerializationSupport {
    private SerializationSupport() {
    }

    public static SerializerFactory reflectionFactory(Class<?> type) {
        ExtSerializerFactory extensions = new ExtSerializerFactory();
        extensions.addSerializer(type, new JavaSerializer(type));
        extensions.addDeserializer(type, new JavaDeserializer(type, new FieldDeserializer2Factory()));
        SerializerFactory factory = new SerializerFactory();
        factory.addFactory(extensions);
        return factory;
    }

    public static Object roundTrip(Object value) throws IOException {
        return roundTrip(value, new SerializerFactory(), true);
    }

    public static Object roundTrip(Object value, SerializerFactory factory, boolean hessian2) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AbstractHessianOutput output = hessian2 ? new Hessian2Output(bytes) : new HessianOutput(bytes);
        output.setSerializerFactory(factory);
        try {
            output.writeObject(value);
            output.flush();
            AbstractHessianInput input = hessian2
                    ? new Hessian2Input(new ByteArrayInputStream(bytes.toByteArray()))
                    : new HessianInput(new ByteArrayInputStream(bytes.toByteArray()));
            input.setSerializerFactory(factory);
            try {
                return input.readObject();
            } finally {
                input.close();
            }
        } finally {
            output.close();
        }
    }
}
