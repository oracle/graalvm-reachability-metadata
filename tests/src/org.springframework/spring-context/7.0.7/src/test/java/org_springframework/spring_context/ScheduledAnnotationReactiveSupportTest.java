/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.ScheduledTask;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScheduledAnnotationReactiveSupportTest {
    @Test
    void createsPublisherOnceAndResubscribesForEachScheduledExecution() {
        ScheduledAnnotationBeanPostProcessor processor = new ScheduledAnnotationBeanPostProcessor();
        ReactiveJob job = new ReactiveJob();
        try {
            processor.postProcessAfterInitialization(job, "reactiveJob");
            assertEquals(1, job.publisherCreations);
            assertEquals(1, processor.getScheduledTasks().size());
            ScheduledTask task = processor.getScheduledTasks().iterator().next();
            task.getTask().getRunnable().run();
            task.getTask().getRunnable().run();
            assertEquals(1, job.publisherCreations);
            assertEquals(2, job.executions);
        } finally {
            processor.destroy();
        }
    }

    public static class ReactiveJob {
        private int publisherCreations;
        private int executions;

        @Scheduled(fixedDelay = 10000)
        public Mono<Void> execute() {
            publisherCreations++;
            return Mono.fromRunnable(() -> executions++);
        }
    }
}
