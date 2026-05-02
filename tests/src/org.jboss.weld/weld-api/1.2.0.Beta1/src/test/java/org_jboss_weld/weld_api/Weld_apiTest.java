/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.ConversationScoped;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import org.jboss.weld.context.ConversationContext;
import org.jboss.weld.context.ManagedContext;
import org.jboss.weld.context.ManagedConversation;
import org.junit.jupiter.api.Test;

public class Weld_apiTest {
    @Test
    void conversationContextApiExposesRuntimeMethodMetadata() throws Exception {
        assertThat(ConversationContext.class.getInterfaces()).contains(ManagedContext.class);
        assertMethod(ConversationContext.class, void.class, "activate", String.class);
        assertMethod(ConversationContext.class, void.class, "activate");
        assertMethod(ConversationContext.class, void.class, "invalidate");
        assertMethod(ConversationContext.class, void.class, "setParameterName", String.class);
        assertMethod(ConversationContext.class, String.class, "getParameterName");
        assertMethod(ConversationContext.class, void.class, "setConcurrentAccessTimeout", long.class);
        assertMethod(ConversationContext.class, long.class, "getConcurrentAccessTimeout");
        assertMethod(ConversationContext.class, void.class, "setDefaultTimeout", long.class);
        assertMethod(ConversationContext.class, long.class, "getDefaultTimeout");
        assertMethod(ConversationContext.class, Collection.class, "getConversations");
        assertMethod(ConversationContext.class, ManagedConversation.class, "getConversation", String.class);
        assertMethod(ConversationContext.class, String.class, "generateConversationId");
        assertMethod(ConversationContext.class, ManagedConversation.class, "getCurrentConversation");
    }

    @Test
    void conversationContextActivatesRestoresConfiguresAndInvalidatesConversations() {
        RecordingConversationContext context = new RecordingConversationContext();

        assertThat(context.isActive()).isFalse();

        context.setParameterName("conversationId");
        context.setConcurrentAccessTimeout(750L);
        context.setDefaultTimeout(2_000L);
        context.activate();
        ManagedConversation transientConversation = context.getCurrentConversation();

        assertThat(context.isActive()).isTrue();
        assertThat(context.getScope()).isEqualTo(ConversationScoped.class);
        assertThat(context.getParameterName()).isEqualTo("conversationId");
        assertThat(context.getConcurrentAccessTimeout()).isEqualTo(750L);
        assertThat(context.getDefaultTimeout()).isEqualTo(2_000L);
        assertThat(transientConversation.isTransient()).isTrue();
        assertThat(transientConversation.getId()).isNull();
        assertThat(context.getConversations()).isEmpty();

        transientConversation.begin(context.generateConversationId());
        transientConversation.setTimeout(4_000L);
        transientConversation.touch();
        context.storeCurrentConversation();

        assertThat(transientConversation.isTransient()).isFalse();
        assertThat(transientConversation.getId()).isEqualTo("conversation-1");
        assertThat(transientConversation.getTimeout()).isEqualTo(4_000L);
        assertThat(transientConversation.getLastUsed()).isGreaterThan(0L);
        assertThat(context.getConversation("conversation-1")).isSameAs(transientConversation);
        assertThat(context.getConversations()).containsExactly(transientConversation);

        context.activate("conversation-1");

        assertThat(context.getCurrentConversation()).isSameAs(transientConversation);

        context.invalidate();
        context.deactivate();

        assertThat(context.isActive()).isFalse();
        assertThat(context.getCurrentConversation()).isNull();
        assertThat(context.getConversations()).isEmpty();
    }

    @Test
    void managedConversationSupportsLifecycleLockingAndTimestamps() {
        RecordingManagedConversation conversation = new RecordingManagedConversation("generated-id");

        assertThat(conversation.isTransient()).isTrue();
        assertThat(conversation.lock(100L)).isTrue();
        assertThat(conversation.lock(100L)).isFalse();
        assertThat(conversation.unlock()).isTrue();
        assertThat(conversation.unlock()).isFalse();

        conversation.begin();
        conversation.setTimeout(500L);
        conversation.touch();

        assertThat(conversation.isTransient()).isFalse();
        assertThat(conversation.getId()).isEqualTo("generated-id");
        assertThat(conversation.getTimeout()).isEqualTo(500L);
        assertThat(conversation.getLastUsed()).isGreaterThan(0L);

        conversation.end();

        assertThat(conversation.isTransient()).isTrue();
        assertThat(conversation.getId()).isNull();

        conversation.begin("manual-id");

        assertThat(conversation.isTransient()).isFalse();
        assertThat(conversation.getId()).isEqualTo("manual-id");
    }

    private static void assertMethod(
            Class<?> apiType, Class<?> returnType, String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = apiType.getMethod(methodName, parameterTypes);

        assertThat(method.getReturnType()).isEqualTo(returnType);
    }

    private static final class RecordingConversationContext implements ConversationContext {
        private final Map<String, RecordingManagedConversation> conversations = new LinkedHashMap<>();
        private String parameterName = "cid";
        private long concurrentAccessTimeout;
        private long defaultTimeout;
        private int sequence;
        private boolean active;
        private boolean invalid;
        private RecordingManagedConversation currentConversation;

        @Override
        public void activate(String cid) {
            active = true;
            invalid = false;
            if (cid == null) {
                currentConversation = new RecordingManagedConversation(null);
                currentConversation.setTimeout(defaultTimeout);
                return;
            }
            currentConversation = conversations.computeIfAbsent(
                    cid,
                    id -> {
                        RecordingManagedConversation conversation = new RecordingManagedConversation(generateConversationId());
                        conversation.begin(id);
                        conversation.setTimeout(defaultTimeout);
                        return conversation;
                    });
            currentConversation.touch();
        }

        @Override
        public void activate() {
            activate(null);
        }

        @Override
        public void invalidate() {
            invalid = true;
        }

        @Override
        public void deactivate() {
            active = false;
            currentConversation = null;
            if (invalid) {
                conversations.clear();
                invalid = false;
            }
        }

        @Override
        public void setParameterName(String cid) {
            parameterName = cid;
        }

        @Override
        public String getParameterName() {
            return parameterName;
        }

        @Override
        public void setConcurrentAccessTimeout(long timeout) {
            concurrentAccessTimeout = timeout;
        }

        @Override
        public long getConcurrentAccessTimeout() {
            return concurrentAccessTimeout;
        }

        @Override
        public void setDefaultTimeout(long timeout) {
            defaultTimeout = timeout;
        }

        @Override
        public long getDefaultTimeout() {
            return defaultTimeout;
        }

        @Override
        public Collection<ManagedConversation> getConversations() {
            List<ManagedConversation> managedConversations = new ArrayList<>(conversations.values());
            return managedConversations;
        }

        @Override
        public ManagedConversation getConversation(String id) {
            return conversations.get(id);
        }

        @Override
        public String generateConversationId() {
            sequence++;
            return "conversation-" + sequence;
        }

        @Override
        public ManagedConversation getCurrentConversation() {
            return currentConversation;
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return ConversationScoped.class;
        }

        @Override
        public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
            return null;
        }

        @Override
        public <T> T get(Contextual<T> contextual) {
            return null;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        void storeCurrentConversation() {
            if (currentConversation != null && !currentConversation.isTransient()) {
                conversations.put(currentConversation.getId(), currentConversation);
            }
        }
    }

    private static final class RecordingManagedConversation implements ManagedConversation {
        private final String generatedId;
        private String id;
        private long timeout;
        private long lastUsed;
        private boolean locked;

        RecordingManagedConversation(String generatedId) {
            this.generatedId = generatedId;
        }

        @Override
        public void begin() {
            begin(generatedId);
        }

        @Override
        public void begin(String id) {
            this.id = id;
            touch();
        }

        @Override
        public void end() {
            id = null;
            touch();
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

        @Override
        public boolean unlock() {
            if (!locked) {
                return false;
            }
            locked = false;
            return true;
        }

        @Override
        public boolean lock(long timeout) {
            if (locked) {
                return false;
            }
            locked = true;
            return true;
        }

        @Override
        public long getLastUsed() {
            return lastUsed;
        }

        @Override
        public void touch() {
            lastUsed = System.nanoTime();
        }
    }
}
