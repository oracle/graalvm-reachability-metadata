/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jobrunr.jobrunr_spring_boot_4_starter;

import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.jobrunr.spring.aot.JobRunrBeanFactoryInitializationAotProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;

import static org.assertj.core.api.Assertions.assertThat;

public class JobRunrBeanFactoryInitializationAotProcessorTest {

    @Test
    void registersHandlerAndRequestReflectionHintsDuringSpringAotProcessing() {
        RuntimeHints hints = new RuntimeHints();
        GenerationContext generationContext = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get(JobRunrBeanFactoryInitializationAotProcessorTest.class)),
                new InMemoryGeneratedFiles(), hints);

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("notificationHandler", NotificationHandler.class);
            new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext);
        }

        assertThat(hints.reflection().getTypeHint(NotificationHandler.class)).isNotNull()
                .satisfies(hint -> assertThat(hint.getMemberCategories())
                        .contains(MemberCategory.INVOKE_PUBLIC_METHODS,
                                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
        assertThat(hints.reflection().getTypeHint(JobRequest.class)).isNotNull()
                .satisfies(hint -> assertThat(hint.getMemberCategories())
                        .contains(MemberCategory.INVOKE_PUBLIC_METHODS,
                                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                                MemberCategory.ACCESS_DECLARED_FIELDS));
    }

    @Test
    void contributesNothingWhenNoJobsOrEnabledServicesArePresent() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        JobRunrBeanFactoryInitializationAotProcessor processor =
                new JobRunrBeanFactoryInitializationAotProcessor();

        assertThat(processor.processAheadOfTime(beanFactory)).isNull();
    }

    public static class NotificationHandler implements JobRequestHandler<JobRequest> {
        private JobRequest lastRequest;

        @Override
        public void run(JobRequest request) {
            lastRequest = request;
        }

        public JobRequest getLastRequest() {
            return lastRequest;
        }
    }
}
