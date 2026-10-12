/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_events_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;
import org.springframework.transaction.event.TransactionalEventListener;

public class TransactionalEventListenerAotProcessorTest {

    @Test
    void registersTransactionalListenerEventTypeForReflection() {
        RuntimeHints runtimeHints = new RuntimeHints();
        GenerationContext generationContext = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get(TransactionalEventListenerAotProcessorTest.class)),
                new InMemoryGeneratedFiles(),
                runtimeHints);

        try (GenericApplicationContext applicationContext = new GenericApplicationContext()) {
            applicationContext.registerBean(TransactionalListener.class);
            new ApplicationContextAotGenerator().processAheadOfTime(applicationContext, generationContext);
        }

        TypeHint eventTypeHint = runtimeHints.reflection().getTypeHint(DomainEvent.class);
        assertThat(eventTypeHint).isNotNull();
        assertThat(eventTypeHint.getMemberCategories())
                .contains(MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
    }

    public static class TransactionalListener {

        @TransactionalEventListener
        public void on(DomainEvent event) {}
    }

    public static class DomainEvent {}
}
