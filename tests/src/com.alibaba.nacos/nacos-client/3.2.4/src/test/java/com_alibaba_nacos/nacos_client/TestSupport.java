/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Properties;
import com.alibaba.nacos.api.PropertyKeyConst;

final class TestSupport {
    private TestSupport() {
    }

    static Properties clientProperties() {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:18848");
        properties.setProperty(PropertyKeyConst.NAMESPACE, "metadata-tests");
        return properties;
    }

    // The collections' documented Serializable contract is the API under test.
    @SuppressWarnings("unchecked")
    static <T> T roundTrip(T value) throws Exception {
        if (!(value instanceof Serializable)) {
            throw new IllegalArgumentException("A Serializable value is required");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
