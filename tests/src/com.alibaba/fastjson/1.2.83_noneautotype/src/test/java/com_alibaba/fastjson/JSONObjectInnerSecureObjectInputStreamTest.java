/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.ParserConfig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JSONObjectInnerSecureObjectInputStreamTest {
    @Test
    void javaSerializationRestoresJSONObjectWithSerializableProxyMap() throws Exception {
        boolean originalAutoTypeSupport = ParserConfig.global.isAutoTypeSupport();
        ParserConfig.global.setAutoTypeSupport(true);
        try {
            JSONObject source = new JSONObject(emptySerializableProxyMap());

            JSONObject restored = deserialize(serialize(source));

            assertThat(restored).isNotSameAs(source);
            assertThat(restored.isEmpty()).isTrue();
            assertThat(restored.size()).isZero();
        } finally {
            ParserConfig.global.setAutoTypeSupport(originalAutoTypeSupport);
        }
    }

    private static byte[] serialize(JSONObject value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static JSONObject deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object value = in.readObject();
            assertThat(value).isInstanceOf(JSONObject.class);
            return (JSONObject) value;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> emptySerializableProxyMap() {
        Object proxy = Proxy.newProxyInstance(
                JSONObjectInnerSecureObjectInputStreamTest.class.getClassLoader(),
                new Class<?>[]{Map.class, Serializable.class},
                new EmptyMapInvocationHandler());
        return (Map<String, Object>) proxy;
    }

    private static final class EmptyMapInvocationHandler implements InvocationHandler, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "entrySet":
                    return Collections.emptySet();
                case "keySet":
                    return Collections.emptySet();
                case "values":
                    return Collections.emptyList();
                case "size":
                    return 0;
                case "isEmpty":
                    return true;
                case "containsKey":
                    return false;
                case "containsValue":
                    return false;
                case "get":
                    return null;
                case "hashCode":
                    return 0;
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "{}";
                default:
                    throw new UnsupportedOperationException(method.toGenericString());
            }
        }
    }
}
