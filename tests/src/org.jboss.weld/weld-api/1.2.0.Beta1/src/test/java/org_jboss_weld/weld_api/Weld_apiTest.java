/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.enterprise.context.Conversation;
import javax.inject.Qualifier;
import org.jboss.weld.conversation.ConversationConcurrentAccessTimeout;
import org.jboss.weld.conversation.ConversationIdGenerator;
import org.jboss.weld.conversation.ConversationIdName;
import org.jboss.weld.conversation.ConversationInactivityTimeout;
import org.jboss.weld.conversation.ConversationManager;
import org.junit.jupiter.api.Test;

public class Weld_apiTest {
    @Test
    void qualifierAnnotationsExposeRuntimeCdiMetadata() throws Exception {
        assertQualifierMetadata(ConversationIdName.class);
        assertQualifierMetadata(ConversationConcurrentAccessTimeout.class);
        assertQualifierMetadata(ConversationInactivityTimeout.class);

        Class<AnnotatedConversationConfiguration> configurationClass = AnnotatedConversationConfiguration.class;
        Field conversationIdField = configurationClass.getDeclaredField("conversationIdParameterName");
        Method timeoutMethod = configurationClass.getDeclaredMethod("configureTimeouts", long.class, long.class);
        Parameter[] parameters = timeoutMethod.getParameters();

        assertThat(annotation(configurationClass, ConversationIdName.class)).isNotNull();
        assertThat(annotation(conversationIdField, ConversationIdName.class)).isNotNull();
        assertThat(annotation(timeoutMethod, ConversationInactivityTimeout.class)).isNotNull();
        assertThat(annotation(parameters[0], ConversationConcurrentAccessTimeout.class)).isNotNull();
        assertThat(annotation(parameters[1], ConversationInactivityTimeout.class)).isNotNull();
    }

    @Test
    void conversationIdGeneratorContractCanBeImplementedAndInvokedThroughApiType() {
        ConversationIdGenerator generator = new PrefixingConversationIdGenerator("conversation");

        assertThat(generator.nextId()).isEqualTo("conversation-1");
        assertThat(generator.nextId()).isEqualTo("conversation-2");
        assertThat(generator.nextId()).isEqualTo("conversation-3");
    }

    @Test
    void conversationManagerBeginsRestoresCleansUpAndDestroysConversations() {
        RecordingConversationManager manager = new RecordingConversationManager();

        manager.beginOrRestoreConversation(null);
        Conversation transientConversation = manager.getCurrentConversation();

        assertThat(transientConversation.isTransient()).isTrue();
        assertThat(transientConversation.getId()).isNull();
        assertThat(manager.getLongRunningConversations()).isEmpty();

        manager.cleanupConversation();

        assertThat(manager.getCurrentConversation()).isNull();

        manager.beginOrRestoreConversation("checkout");
        Conversation checkoutConversation = manager.getCurrentConversation();
        checkoutConversation.setTimeout(2_000L);

        assertThat(checkoutConversation.isTransient()).isFalse();
        assertThat(checkoutConversation.getId()).isEqualTo("checkout");
        assertThat(checkoutConversation.getTimeout()).isEqualTo(2_000L);
        assertThat(manager.getLongRunningConversations()).containsExactly(checkoutConversation);

        manager.beginOrRestoreConversation("checkout");

        assertThat(manager.getCurrentConversation()).isSameAs(checkoutConversation);

        manager.beginOrRestoreConversation("support");

        assertThat(manager.getLongRunningConversations())
                .extracting(Conversation::getId)
                .containsExactly("checkout", "support");

        manager.destroyAllConversations();

        assertThat(manager.getCurrentConversation()).isNull();
        assertThat(manager.getLongRunningConversations()).isEmpty();
    }

    @Test
    void conversationInterfaceSupportsExplicitAndGeneratedLongRunningIds() {
        PrefixingConversationIdGenerator generator = new PrefixingConversationIdGenerator("generated");
        RecordingConversation conversation = new RecordingConversation(generator);

        assertThat(conversation.isTransient()).isTrue();
        assertThat(conversation.getId()).isNull();

        conversation.begin();
        conversation.setTimeout(500L);

        assertThat(conversation.isTransient()).isFalse();
        assertThat(conversation.getId()).isEqualTo("generated-1");
        assertThat(conversation.getTimeout()).isEqualTo(500L);

        conversation.end();

        assertThat(conversation.isTransient()).isTrue();
        assertThat(conversation.getId()).isNull();

        conversation.begin("manual-id");

        assertThat(conversation.isTransient()).isFalse();
        assertThat(conversation.getId()).isEqualTo("manual-id");
    }

    private static void assertQualifierMetadata(Class<? extends Annotation> annotationType) {
        Target target = annotation(annotationType, Target.class);
        Retention retention = annotation(annotationType, Retention.class);

        assertThat(annotation(annotationType, Qualifier.class)).isNotNull();
        assertThat(annotation(annotationType, Documented.class)).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(Arrays.asList(target.value()))
                .containsExactlyInAnyOrder(
                        java.lang.annotation.ElementType.TYPE,
                        java.lang.annotation.ElementType.METHOD,
                        java.lang.annotation.ElementType.PARAMETER,
                        java.lang.annotation.ElementType.FIELD);
    }

    // Checkstyle: allow direct annotation access
    private static <A extends Annotation> A annotation(AnnotatedElement element, Class<A> annotationType) {
        AnnotatedElement elementAnnotationAccess = element;
        return elementAnnotationAccess.getAnnotation(annotationType);
    }
    // Checkstyle: disallow direct annotation access

    @ConversationIdName
    private static final class AnnotatedConversationConfiguration {
        @ConversationIdName
        private String conversationIdParameterName;

        @ConversationInactivityTimeout
        void configureTimeouts(
                @ConversationConcurrentAccessTimeout long concurrentAccessTimeout,
                @ConversationInactivityTimeout long inactivityTimeout) {
            this.conversationIdParameterName = Long.toString(concurrentAccessTimeout + inactivityTimeout);
        }
    }

    private static final class PrefixingConversationIdGenerator implements ConversationIdGenerator {
        private final String prefix;
        private int sequence;

        PrefixingConversationIdGenerator(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String nextId() {
            sequence++;
            return prefix + "-" + sequence;
        }
    }

    private static final class RecordingConversationManager implements ConversationManager {
        private final ConversationIdGenerator generator = new PrefixingConversationIdGenerator("managed");
        private final Map<String, RecordingConversation> longRunningConversations = new LinkedHashMap<>();
        private Conversation currentConversation;

        @Override
        public void beginOrRestoreConversation(String cid) {
            if (cid == null) {
                currentConversation = new RecordingConversation(generator);
                return;
            }
            RecordingConversation conversation = longRunningConversations.computeIfAbsent(
                    cid,
                    id -> {
                        RecordingConversation createdConversation = new RecordingConversation(generator);
                        createdConversation.begin(id);
                        return createdConversation;
                    });
            currentConversation = conversation;
        }

        @Override
        public void cleanupConversation() {
            if (currentConversation != null && currentConversation.isTransient()) {
                currentConversation = null;
            }
        }

        @Override
        public void destroyAllConversations() {
            longRunningConversations.clear();
            currentConversation = null;
        }

        @Override
        public Set<Conversation> getLongRunningConversations() {
            return new LinkedHashSet<>(longRunningConversations.values());
        }

        Conversation getCurrentConversation() {
            return currentConversation;
        }
    }

    private static final class RecordingConversation implements Conversation {
        private final ConversationIdGenerator generator;
        private String id;
        private long timeout;

        RecordingConversation(ConversationIdGenerator generator) {
            this.generator = generator;
        }

        @Override
        public void begin() {
            begin(generator.nextId());
        }

        @Override
        public void begin(String id) {
            this.id = id;
        }

        @Override
        public void end() {
            id = null;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public long getTimeout() {
            return timeout;
        }

        @Override
        public void setTimeout(long milliseconds) {
            timeout = milliseconds;
        }

        @Override
        public boolean isTransient() {
            return id == null;
        }
    }
}
