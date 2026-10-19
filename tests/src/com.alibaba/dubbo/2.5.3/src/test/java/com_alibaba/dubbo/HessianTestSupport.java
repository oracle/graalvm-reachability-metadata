/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import com.alibaba.com.caucho.hessian.io.Hessian2Input;
import com.alibaba.com.caucho.hessian.io.Hessian2Output;
import com.alibaba.com.caucho.hessian.io.SerializerFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

final class HessianTestSupport {
    private HessianTestSupport() {}

    static Object roundTrip(Object value) throws Exception {
        return roundTrip(value, new SerializerFactory());
    }

    static Object roundTrip(Object value, SerializerFactory factory) throws Exception {
        factory.setAllowNonSerializable(true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Hessian2Output output = new Hessian2Output(bytes);
        output.setSerializerFactory(factory);
        output.writeObject(value);
        output.flush();

        Hessian2Input input = new Hessian2Input(new ByteArrayInputStream(bytes.toByteArray()));
        input.setSerializerFactory(factory);
        return input.readObject(value.getClass());
    }
}
