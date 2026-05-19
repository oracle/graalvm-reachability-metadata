/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.stream.binder.PartitionHandler;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.OperatorOverloader;
import org.springframework.expression.TypeComparator;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.TypeLocator;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.support.StandardEvaluationContext;

public class PartitionHandlerTest {

    @Test
    void extractingBeanFactoryFromEvaluationContextInvokesFieldAccess() throws Throwable {
        MethodHandle extractBeanFactory = MethodHandles.privateLookupIn(PartitionHandler.class, MethodHandles.lookup())
                .findStatic(PartitionHandler.class, "extractBeanFactoryFromEvaluationContext",
                        MethodType.methodType(BeanFactory.class, EvaluationContext.class));
        try (GenericApplicationContext applicationContext = new GenericApplicationContext()) {
            applicationContext.refresh();
            TestEvaluationContext evaluationContext = new TestEvaluationContext(applicationContext.getBeanFactory());

            Object beanFactory = extractBeanFactory.invoke(evaluationContext);

            assertSame(applicationContext.getBeanFactory(), beanFactory);
        }
    }

    private static final class TestEvaluationContext extends BeanFactoryResolver implements EvaluationContext {

        private final StandardEvaluationContext delegate = new StandardEvaluationContext();

        private TestEvaluationContext(BeanFactory beanFactory) {
            super(beanFactory);
        }

        @Override
        public TypedValue getRootObject() {
            return this.delegate.getRootObject();
        }

        @Override
        public BeanResolver getBeanResolver() {
            return this.delegate.getBeanResolver();
        }

        @Override
        public TypeLocator getTypeLocator() {
            return this.delegate.getTypeLocator();
        }

        @Override
        public TypeConverter getTypeConverter() {
            return this.delegate.getTypeConverter();
        }

        @Override
        public TypeComparator getTypeComparator() {
            return this.delegate.getTypeComparator();
        }

        @Override
        public OperatorOverloader getOperatorOverloader() {
            return this.delegate.getOperatorOverloader();
        }

        @Override
        public void setVariable(String name, Object value) {
            this.delegate.setVariable(name, value);
        }

        @Override
        public Object lookupVariable(String name) {
            return this.delegate.lookupVariable(name);
        }
    }
}
