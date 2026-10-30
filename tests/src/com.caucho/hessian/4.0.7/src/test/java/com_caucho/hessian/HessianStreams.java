/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.AbstractHessianInput;
import com.caucho.hessian.io.AbstractHessianOutput;
import com.caucho.hessian.io.AbstractSerializerFactory;
import com.caucho.hessian.io.Deserializer;
import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;
import com.caucho.hessian.io.Serializer;
import com.caucho.hessian.io.SerializerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class HessianStreams {
    private HessianStreams() {
    }

    static SerializerFactory codecs(Class<?> type, Serializer serializer, Deserializer deserializer) {
        SerializerFactory factory = new SerializerFactory();
        factory.addFactory(new AbstractSerializerFactory() {
            @Override
            public Serializer getSerializer(Class candidate) {
                return candidate == type ? serializer : null;
            }

            @Override
            public Deserializer getDeserializer(Class candidate) {
                return candidate == type ? deserializer : null;
            }
        });
        return factory;
    }

    static <T> T roundTrip(T value) throws IOException {
        return roundTrip(value, new SerializerFactory(), true);
    }

    @SuppressWarnings("unchecked")
    static <T> T roundTrip(T value, SerializerFactory factory, boolean hessian2) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AbstractHessianOutput output = hessian2 ? new Hessian2Output(bytes) : new HessianOutput(bytes);
        output.setSerializerFactory(factory);
        output.writeObject(value);
        output.close();
        AbstractHessianInput input = hessian2
                ? new Hessian2Input(new ByteArrayInputStream(bytes.toByteArray()))
                : new HessianInput(new ByteArrayInputStream(bytes.toByteArray()));
        input.setSerializerFactory(factory);
        try {
            return (T) input.readObject();
        } finally {
            input.close();
        }
    }
}
