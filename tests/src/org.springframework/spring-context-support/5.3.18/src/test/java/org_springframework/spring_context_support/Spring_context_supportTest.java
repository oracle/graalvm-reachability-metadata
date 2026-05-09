/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context_support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.jcache.config.JCacheConfigurer;
import org.springframework.cache.jcache.config.JCacheConfigurerSupport;
import org.springframework.cache.transaction.AbstractTransactionSupportingCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class Spring_context_supportTest {
    @Test
    void simpleMailMessageStoresCopiesAndCopiesToAnotherMessage() {
        Date sentDate = new Date(1_650_000_000_000L);
        SimpleMailMessage source = new SimpleMailMessage();
        source.setFrom("billing@example.org");
        source.setReplyTo("support@example.org");
        source.setTo("primary@example.org", "secondary@example.org");
        source.setCc("copy@example.org");
        source.setBcc("audit@example.org", "archive@example.org");
        source.setSentDate(sentDate);
        source.setSubject("Invoice ready");
        source.setText("Your invoice is available.");

        SimpleMailMessage copiedByConstructor = new SimpleMailMessage(source);
        SimpleMailMessage copiedByMethod = new SimpleMailMessage();
        source.copyTo(copiedByMethod);

        assertThat(copiedByConstructor).isEqualTo(source);
        assertThat(copiedByConstructor).hasSameHashCodeAs(source);
        assertThat(copiedByMethod).isEqualTo(source);
        assertThat(copiedByMethod.getFrom()).isEqualTo("billing@example.org");
        assertThat(copiedByMethod.getReplyTo()).isEqualTo("support@example.org");
        assertThat(copiedByMethod.getTo()).containsExactly("primary@example.org", "secondary@example.org");
        assertThat(copiedByMethod.getCc()).containsExactly("copy@example.org");
        assertThat(copiedByMethod.getBcc()).containsExactly("audit@example.org", "archive@example.org");
        assertThat(copiedByMethod.getSentDate()).isEqualTo(sentDate);
        assertThat(copiedByMethod.getSubject()).isEqualTo("Invoice ready");
        assertThat(copiedByMethod.getText()).isEqualTo("Your invoice is available.");

        copiedByConstructor.setSubject("Changed subject");
        assertThat(source.getSubject()).isEqualTo("Invoice ready");
        assertThat(copiedByConstructor).isNotEqualTo(source);
        assertThat(source.toString()).contains("billing@example.org", "primary@example.org", "Invoice ready");
    }

    @Test
    void mailSenderContractCanSendSingleAndBatchMessages() {
        RecordingMailSender sender = new RecordingMailSender();
        SimpleMailMessage first = message("first@example.org", "First");
        SimpleMailMessage second = message("second@example.org", "Second");

        sender.send(first);
        sender.send(first, second);

        assertThat(sender.sentMessages()).hasSize(3);
        assertThat(sender.sentMessages().get(0)).isEqualTo(first);
        assertThat(sender.sentMessages().get(1)).isEqualTo(first);
        assertThat(sender.sentMessages().get(2)).isEqualTo(second);

        first.setSubject("mutated after send");
        assertThat(sender.sentMessages().get(0).getSubject()).isEqualTo("First");
        assertThat(sender.sentMessages().get(1).getSubject()).isEqualTo("First");
    }

    @Test
    void mailExceptionsRetainCausesAndFailedMessages() {
        SimpleMailMessage failedMessage = message("customer@example.org", "Delivery update");
        IllegalStateException deliveryFailure = new IllegalStateException("SMTP 451 temporary failure");
        Map<Object, Exception> failedMessages = new LinkedHashMap<>();
        failedMessages.put(failedMessage, deliveryFailure);

        MailSendException sendException = new MailSendException(
                "Could not deliver mail", new RuntimeException("transport closed"), failedMessages);

        assertThat(sendException.getFailedMessages()).containsEntry(failedMessage, deliveryFailure);
        assertThat(sendException.getMessageExceptions()).containsExactly(deliveryFailure);
        assertThat(sendException.getMessage()).contains("Could not deliver mail", "SMTP 451 temporary failure");
        assertThat(sendException.toString()).contains("MailSendException", "SMTP 451 temporary failure");

        RuntimeException authCause = new RuntimeException("invalid password");
        RuntimeException parseCause = new RuntimeException("bad address");
        RuntimeException preparationCause = new RuntimeException("template missing");

        assertThat(new MailAuthenticationException(authCause)).hasCause(authCause);
        assertThat(new MailAuthenticationException("Authentication failed", authCause))
                .hasMessageContaining("Authentication failed")
                .hasCause(authCause);
        assertThat(new MailParseException(parseCause)).hasCause(parseCause);
        assertThat(new MailParseException("Cannot parse message", parseCause))
                .hasMessageContaining("Cannot parse message")
                .hasCause(parseCause);
        assertThat(new MailPreparationException(preparationCause)).hasCause(preparationCause);
        assertThat(new MailPreparationException("Cannot prepare message", preparationCause))
                .hasMessageContaining("Cannot prepare message")
                .hasCause(preparationCause);
    }

    @Test
    void transactionAwareCacheDecoratorDelegatesImmediateOperations() throws Exception {
        ConcurrentMapCache targetCache = new ConcurrentMapCache("orders");
        TransactionAwareCacheDecorator cache = new TransactionAwareCacheDecorator(targetCache);

        assertThat(cache.getTargetCache()).isSameAs(targetCache);
        assertThat(cache.getName()).isEqualTo("orders");
        assertThat(cache.getNativeCache()).isSameAs(targetCache.getNativeCache());

        assertThat(cache.putIfAbsent("order-1", "created")).isNull();
        Cache.ValueWrapper existingValue = cache.putIfAbsent("order-1", "ignored");
        assertThat(existingValue).isNotNull();
        assertThat(existingValue.get()).isEqualTo("created");
        assertThat(cache.get("order-1").get()).isEqualTo("created");
        assertThat(cache.get("order-1", String.class)).isEqualTo("created");

        AtomicInteger loadCount = new AtomicInteger();
        String loaded = cache.get("order-2", () -> "loaded-" + loadCount.incrementAndGet());
        String cached = cache.get("order-2", () -> "loaded-" + loadCount.incrementAndGet());
        assertThat(loaded).isEqualTo("loaded-1");
        assertThat(cached).isEqualTo("loaded-1");
        assertThat(loadCount).hasValue(1);

        assertThat(cache.evictIfPresent("order-1")).isTrue();
        assertThat(cache.get("order-1")).isNull();
        assertThat(cache.invalidate()).isTrue();
        assertThat(cache.get("order-2")).isNull();
    }

    @Test
    void transactionAwareCacheDecoratorDefersTransactionSynchronizedOperationsUntilCommit() {
        ConcurrentMapCache targetCache = new ConcurrentMapCache("workflow");
        TransactionAwareCacheDecorator cache = new TransactionAwareCacheDecorator(targetCache);

        completeSynchronizedTransaction(() -> {
            cache.put("draft", "queued");
            assertThat(targetCache.get("draft")).isNull();
        });
        assertThat(targetCache.get("draft", String.class)).isEqualTo("queued");

        completeSynchronizedTransaction(() -> {
            cache.evict("draft");
            assertThat(targetCache.get("draft", String.class)).isEqualTo("queued");
        });
        assertThat(targetCache.get("draft")).isNull();

        targetCache.put("first", "one");
        targetCache.put("second", "two");
        completeSynchronizedTransaction(() -> {
            cache.clear();
            assertThat(targetCache.get("first", String.class)).isEqualTo("one");
            assertThat(targetCache.get("second", String.class)).isEqualTo("two");
        });
        assertThat(targetCache.get("first")).isNull();
        assertThat(targetCache.get("second")).isNull();
    }

    @Test
    void transactionAwareCacheManagerProxyWrapsCachesFromTargetManager() {
        TransactionAwareCacheManagerProxy unconfiguredProxy = new TransactionAwareCacheManagerProxy();
        assertThatThrownBy(unconfiguredProxy::afterPropertiesSet)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetCacheManager");

        ConcurrentMapCacheManager targetManager = new ConcurrentMapCacheManager("users", "sessions");
        TransactionAwareCacheManagerProxy proxy = new TransactionAwareCacheManagerProxy(targetManager);
        proxy.afterPropertiesSet();

        assertThat(proxy.getCacheNames()).containsExactlyInAnyOrder("users", "sessions");
        Cache usersCache = proxy.getCache("users");
        assertThat(usersCache).isInstanceOf(TransactionAwareCacheDecorator.class);
        assertThat(usersCache.getName()).isEqualTo("users");

        Cache.ValueWrapper previous = usersCache.putIfAbsent("alice", "administrator");
        assertThat(previous).isNull();
        assertThat(usersCache.get("alice", String.class)).isEqualTo("administrator");
        assertThat(targetManager.getCache("users").get("alice", String.class)).isEqualTo("administrator");
    }

    @Test
    void jCacheConfigurerDefaultsToNoExceptionCacheResolver() {
        JCacheConfigurerSupport support = new JCacheConfigurerSupport();
        JCacheConfigurer configurer = support;

        assertThat(support.exceptionCacheResolver()).isNull();
        assertThat(configurer.exceptionCacheResolver()).isNull();
    }

    @Test
    void abstractTransactionSupportingCacheManagerDecoratesInitialAndDynamicCachesWhenEnabled() {
        DynamicTransactionSupportingCacheManager manager = new DynamicTransactionSupportingCacheManager();
        manager.setTransactionAware(true);
        manager.afterPropertiesSet();

        assertThat(manager.isTransactionAware()).isTrue();
        assertThat(manager.getCacheNames()).containsExactly("initial");
        assertThat(manager.getCache("initial")).isInstanceOf(TransactionAwareCacheDecorator.class);

        Cache dynamicCache = manager.getCache("dynamic");
        assertThat(dynamicCache).isInstanceOf(TransactionAwareCacheDecorator.class);
        assertThat(manager.getCacheNames()).containsExactlyInAnyOrder("initial", "dynamic");

        completeSynchronizedTransaction(() -> {
            dynamicCache.put("report", "ready");
            assertThat(manager.targetCache("dynamic").get("report")).isNull();
        });
        assertThat(manager.targetCache("dynamic").get("report", String.class)).isEqualTo("ready");

        DynamicTransactionSupportingCacheManager nonTransactionAwareManager =
                new DynamicTransactionSupportingCacheManager();
        nonTransactionAwareManager.setTransactionAware(false);
        nonTransactionAwareManager.afterPropertiesSet();

        assertThat(nonTransactionAwareManager.isTransactionAware()).isFalse();
        Cache directCache = nonTransactionAwareManager.getCache("initial");
        assertThat(directCache).isSameAs(nonTransactionAwareManager.targetCache("initial"));
    }

    private static void completeSynchronizedTransaction(Runnable action) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            action.run();
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static SimpleMailMessage message(String to, String subject) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("sender@example.org");
        message.setTo(to);
        message.setSubject(subject);
        message.setText("Body for " + subject);
        return message;
    }

    private static final class DynamicTransactionSupportingCacheManager extends AbstractTransactionSupportingCacheManager {
        private final Map<String, ConcurrentMapCache> caches = new LinkedHashMap<>();

        @Override
        protected Collection<? extends Cache> loadCaches() {
            return Collections.singletonList(getOrCreateTargetCache("initial"));
        }

        @Override
        protected Cache getMissingCache(String name) {
            return getOrCreateTargetCache(name);
        }

        private ConcurrentMapCache getOrCreateTargetCache(String name) {
            return caches.computeIfAbsent(name, ConcurrentMapCache::new);
        }

        private ConcurrentMapCache targetCache(String name) {
            return caches.get(name);
        }
    }

    private static final class RecordingMailSender implements MailSender {
        private final List<SimpleMailMessage> sentMessages = new ArrayList<>();

        @Override
        public void send(SimpleMailMessage simpleMessage) throws MailException {
            sentMessages.add(new SimpleMailMessage(simpleMessage));
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) throws MailException {
            for (SimpleMailMessage simpleMessage : simpleMessages) {
                send(simpleMessage);
            }
        }

        List<SimpleMailMessage> sentMessages() {
            return sentMessages;
        }
    }
}
