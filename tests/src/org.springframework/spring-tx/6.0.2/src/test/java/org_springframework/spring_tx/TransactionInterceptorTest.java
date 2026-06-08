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
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionInterceptorTest {

    @Test
    void serializationWritesTransactionInterceptorCustomState() throws Exception {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManagerBeanName("testTransactionManager");

        byte[] serialized = serialize(interceptor);

        assertThat(new String(serialized, StandardCharsets.ISO_8859_1)).contains("testTransactionManager");
    }

    @Test
    void deserializationReadsTransactionInterceptorCustomState() throws Exception {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManagerBeanName("testTransactionManager");

        TransactionInterceptor deserialized = deserialize(serialize(interceptor));

        assertThat(new String(serialize(deserialized), StandardCharsets.ISO_8859_1))
                .contains("testTransactionManager");
    }

    @Test
    void invokeWithTransactionAttributeCommitsAroundInvocation() throws Throwable {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        NameMatchTransactionAttributeSource attributeSource = new NameMatchTransactionAttributeSource();
        attributeSource.addTransactionalMethod("call", new DefaultTransactionAttribute());
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(attributeSource);
        CallbackMethodInvocation invocation = new CallbackMethodInvocation(Callable.class.getMethod("call"));

        Object result = interceptor.invoke(invocation);

        assertThat(result).isEqualTo("transactional result");
        assertThat(invocation.proceeded).isTrue();
        assertThat(transactionManager.getTransactionCount).isEqualTo(1);
        assertThat(transactionManager.commitCount).isEqualTo(1);
        assertThat(transactionManager.rollbackCount).isZero();
    }

    private static byte[] serialize(TransactionInterceptor interceptor) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(interceptor);
        }
        return bytes.toByteArray();
    }

    private static TransactionInterceptor deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (TransactionInterceptor) inputStream.readObject();
        }
    }

    private static final class CallbackMethodInvocation implements MethodInvocation {
        private final Method method;
        private boolean proceeded;

        private CallbackMethodInvocation(Method method) {
            this.method = method;
        }

        @Override
        public Method getMethod() {
            return this.method;
        }

        @Override
        public Object[] getArguments() {
            return new Object[0];
        }

        @Override
        public Object proceed() {
            this.proceeded = true;
            return "transactional result";
        }

        @Override
        public Object getThis() {
            return null;
        }

        @Override
        public AccessibleObject getStaticPart() {
            return this.method;
        }
    }

    private static final class RecordingTransactionManager implements PlatformTransactionManager {
        private int getTransactionCount;
        private int commitCount;
        private int rollbackCount;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            this.getTransactionCount++;
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            this.commitCount++;
        }

        @Override
        public void rollback(TransactionStatus status) {
            this.rollbackCount++;
        }
    }
}
