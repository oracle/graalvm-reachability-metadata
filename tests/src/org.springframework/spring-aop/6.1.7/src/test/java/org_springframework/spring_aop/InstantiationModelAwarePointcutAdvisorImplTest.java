/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_aop;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.Test;

import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.annotation.ReflectiveAspectJAdvisorFactory;
import org.springframework.aop.aspectj.annotation.SingletonMetadataAwareAspectInstanceFactory;

public class InstantiationModelAwarePointcutAdvisorImplTest {

    @Test
    void deserializesAdvisorAndRestoresAdviceMethod() throws Exception {
        Advisor advisor = createAdvisor(new SerializableAspect());

        Advisor deserializedAdvisor = serializeAndDeserialize(advisor);

        assertThat(deserializedAdvisor).isNotSameAs(advisor);
        assertThat(deserializedAdvisor.getAdvice()).isNotNull();
        assertThat(deserializedAdvisor.toString()).contains("beforeInvocation");
    }

    private static Advisor createAdvisor(SerializableAspect aspect) {
        ReflectiveAspectJAdvisorFactory advisorFactory = new ReflectiveAspectJAdvisorFactory();
        SingletonMetadataAwareAspectInstanceFactory aspectInstanceFactory =
                new SingletonMetadataAwareAspectInstanceFactory(aspect, "serializableAspect");
        List<Advisor> advisors = advisorFactory.getAdvisors(aspectInstanceFactory);

        assertThat(advisors).hasSize(1);
        return advisors.get(0);
    }

    private static Advisor serializeAndDeserialize(Advisor advisor) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(advisor);
        }

        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        try (ObjectInputStream objectInput = new ObjectInputStream(input)) {
            return (Advisor) objectInput.readObject();
        }
    }

    @Aspect
    public static class SerializableAspect implements Serializable {

        private static final long serialVersionUID = 1L;

        @Before("execution(* *(..))")
        public void beforeInvocation() {
        }
    }
}
