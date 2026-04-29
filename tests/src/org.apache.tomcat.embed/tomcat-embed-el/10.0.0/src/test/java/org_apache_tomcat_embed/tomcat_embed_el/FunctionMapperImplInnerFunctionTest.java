/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.apache.el.lang.FunctionMapperImpl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionMapperImplInnerFunctionTest {

    @Test
    void resolvesMethodFromSerializedFunctionMapping()
            throws ClassNotFoundException, IOException, NoSuchFieldException, NoSuchMethodException,
                    IllegalAccessException {
        FunctionMapperImpl mapper = new FunctionMapperImpl();
        mapper.mapFunction("lib", "join", FunctionLibrary.class.getMethod("join"));
        replaceFunctionsMapWithSerializableMap(mapper);

        FunctionMapperImpl restoredMapper = roundTrip(mapper);
        Method restoredMethod = restoredMapper.resolveFunction("lib", "join");

        assertThat(restoredMethod).isNotNull();
        assertThat(restoredMethod.getDeclaringClass()).isEqualTo(FunctionLibrary.class);
        assertThat(restoredMethod.getName()).isEqualTo("join");
        assertThat(restoredMethod.getParameterTypes()).isEmpty();
    }

    private void replaceFunctionsMapWithSerializableMap(FunctionMapperImpl mapper)
            throws NoSuchFieldException, IllegalAccessException {
        Field functions = FunctionMapperImpl.class.getDeclaredField("functions");
        functions.setAccessible(true);
        Map<?, ?> mappedFunctions = (Map<?, ?>) functions.get(mapper);
        SerializableConcurrentMap serializableMap = new SerializableConcurrentMap();
        for (Map.Entry<?, ?> entry : mappedFunctions.entrySet()) {
            serializableMap.put((String) entry.getKey(), entry.getValue());
        }
        functions.set(mapper, serializableMap);
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

    public static final class SerializableConcurrentMap extends HashMap<String, Object>
            implements ConcurrentMap<String, Object>, Externalizable {
        public SerializableConcurrentMap() {
        }

        @Override
        public Object putIfAbsent(String key, Object value) {
            Object currentValue = get(key);
            if (currentValue == null) {
                put(key, value);
            }
            return currentValue;
        }

        @Override
        public boolean remove(Object key, Object value) {
            if (containsKey(key) && get(key).equals(value)) {
                remove(key);
                return true;
            }
            return false;
        }

        @Override
        public boolean replace(String key, Object oldValue, Object newValue) {
            if (containsKey(key) && get(key).equals(oldValue)) {
                put(key, newValue);
                return true;
            }
            return false;
        }

        @Override
        public Object replace(String key, Object value) {
            if (containsKey(key)) {
                return put(key, value);
            }
            return null;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeInt(size());
            for (Map.Entry<String, Object> entry : entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeObject(entry.getValue());
            }
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            clear();
            int size = in.readInt();
            for (int i = 0; i < size; i++) {
                put(in.readUTF(), in.readObject());
            }
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
