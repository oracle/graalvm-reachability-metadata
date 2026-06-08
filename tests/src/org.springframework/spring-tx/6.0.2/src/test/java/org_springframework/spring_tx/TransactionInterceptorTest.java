/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_tx;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import org.springframework.transaction.interceptor.TransactionInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionInterceptorTest {

    @Test
    void serializationRoundTripUsesTransactionInterceptorCustomSerialization() throws Exception {
        TransactionInterceptor interceptor = new TransactionInterceptor();

        byte[] serialized = serialize(interceptor);
        Object deserialized = deserialize(serialized);

        assertThat(deserialized).isInstanceOf(TransactionInterceptor.class);
        TransactionInterceptor restored = (TransactionInterceptor) deserialized;
        assertThat(restored).isNotSameAs(interceptor);
        assertThat(restored.getTransactionManager()).isNull();
        assertThat(restored.getTransactionAttributeSource()).isNull();
    }

    private static byte[] serialize(TransactionInterceptor interceptor) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(interceptor);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return inputStream.readObject();
        }
    }
}
