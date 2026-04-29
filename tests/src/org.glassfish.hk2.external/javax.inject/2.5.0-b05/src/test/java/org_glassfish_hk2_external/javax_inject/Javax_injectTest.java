/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2_external.javax_inject;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Scope;
import javax.inject.Singleton;
import org.junit.jupiter.api.Test;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Javax_injectTest {
    @Test
    void annotationTypesCanBeImplementedAndUsedThroughTheirPublicContracts() {
        Named primaryName = named("primary");
        Named defaultName = named("");
        Inject inject = new Inject() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Inject.class;
            }
        };
        Qualifier qualifier = new Qualifier() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Qualifier.class;
            }
        };
        Scope scope = new Scope() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Scope.class;
            }
        };
        Singleton singleton = new Singleton() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Singleton.class;
            }
        };
        Provider<List<String>> labels = () -> List.of(primaryName.value(), defaultName.value(), "provider");

        assertThat(primaryName.value()).isEqualTo("primary");
        assertThat(defaultName.value()).isEmpty();
        assertThat(primaryName.annotationType()).isSameAs(Named.class);
        assertThat(inject.annotationType()).isSameAs(Inject.class);
        assertThat(qualifier.annotationType()).isSameAs(Qualifier.class);
        assertThat(scope.annotationType()).isSameAs(Scope.class);
        assertThat(singleton.annotationType()).isSameAs(Singleton.class);
        assertThat(labels.get()).containsExactly("primary", "", "provider");
    }

    @Test
    void manuallyWiredInjectableServiceGraphUsesConstructorFieldAndMethodInjectionPoints() {
        AtomicInteger issuedIds = new AtomicInteger();
        Provider<String> requestIdProvider = () -> "request-" + issuedIds.incrementAndGet();
        Provider<MessageTemplate> templateProvider = () -> new MessageTemplate("Hello");
        Provider<String> suffixProvider = () -> "!";

        GreetingService greetingService = new GreetingService(requestIdProvider, templateProvider);
        greetingService.defaultAudience = "team";
        greetingService.configure(" ", suffixProvider);

        assertThat(greetingService.greet(null)).isEqualTo("request-1 Hello, team!");
        assertThat(greetingService.greet("native image")).isEqualTo("request-2 Hello, native image!");
        assertThat(greetingService.greet("  metadata  ")).isEqualTo("request-3 Hello, metadata!");
    }

    @Test
    void namedProvidersCanModelIndependentBindingsForTheSameServiceType() {
        Map<String, Provider<TextFormatter>> formatters = Map.of(
                "plain", PlainTextFormatter::new,
                "bracketed", BracketedTextFormatter::new,
                "upper", UppercaseTextFormatter::new);
        FormatterRegistry registry = new FormatterRegistry(formatters);

        assertThat(registry.format(named("plain"), "alpha")).isEqualTo("alpha");
        assertThat(registry.format(named("bracketed"), "alpha")).isEqualTo("[alpha]");
        assertThat(registry.format(named("upper"), "alpha")).isEqualTo("ALPHA");
        assertThatThrownBy(() -> registry.format(named("missing"), "alpha"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void customQualifiersAndScopesComposeWithDomainSpecificInjectionMetadata() {
        Queue<String> remoteValues = new ArrayDeque<>(List.of("remote-a", "remote-b"));
        Provider<String> remoteSource = remoteValues::remove;
        Provider<String> localSource = () -> "local";
        ScopedAggregator aggregator = new ScopedAggregator(remoteSource);
        aggregator.localSource = localSource;
        aggregator.setJoiner("+");

        assertThat(aggregator.nextPair()).isEqualTo("remote-a+local");
        assertThat(aggregator.nextPair()).isEqualTo("remote-b+local");
        assertThat(aggregator.scopeName()).isEqualTo("cached");
    }

    @Test
    void nestedProvidersDeferStrategySelectionUntilEachLookup() {
        AtomicInteger mode = new AtomicInteger();
        Provider<String> formalGreeting = () -> "Good day";
        Provider<String> casualGreeting = () -> "Hi";
        Provider<Provider<String>> selectedGreeting = () -> mode.get() == 0 ? formalGreeting : casualGreeting;
        DeferredGreeter greeter = new DeferredGreeter(selectedGreeting);

        assertThat(greeter.greet("team")).isEqualTo("Good day, team");

        mode.set(1);
        assertThat(greeter.greet("native image")).isEqualTo("Hi, native image");

        mode.set(0);
        assertThat(greeter.greet("metadata")).isEqualTo("Good day, metadata");
    }

    @Test
    void providerInjectedCollaboratorsCanResolveCircularServiceGraphs() {
        CircularServices services = new CircularServices();
        services.orderService = new OrderService(() -> services.invoiceService);
        services.invoiceService = new InvoiceService(() -> services.orderService);

        assertThat(services.orderService.submit("A-100")).isEqualTo("order A-100 -> invoice for accepted A-100");
        assertThat(services.invoiceService.describe("A-101")).isEqualTo("invoice for pending A-101");
    }

    @Test
    void providerBackedFactoryCreatesIndependentUnscopedInstancesOnDemand() {
        AtomicInteger issuedCartIds = new AtomicInteger();
        Provider<ShoppingCart> cartProvider = () -> new ShoppingCart("cart-" + issuedCartIds.incrementAndGet());
        CheckoutFlow checkoutFlow = new CheckoutFlow(cartProvider);

        Receipt firstReceipt = checkoutFlow.checkout(List.of("book", "pen"));
        Receipt secondReceipt = checkoutFlow.checkout(List.of("notebook"));

        assertThat(firstReceipt.cartId()).isEqualTo("cart-1");
        assertThat(firstReceipt.summary()).isEqualTo("book,pen");
        assertThat(firstReceipt.itemCount()).isEqualTo(2);
        assertThat(secondReceipt.cartId()).isEqualTo("cart-2");
        assertThat(secondReceipt.summary()).isEqualTo("notebook");
        assertThat(secondReceipt.itemCount()).isEqualTo(1);
        assertThat(issuedCartIds).hasValue(2);
    }

    @Test
    void singletonScopedProviderSharesOneLazyInstanceAcrossConsumers() {
        AtomicInteger createdInstances = new AtomicInteger();
        Provider<SharedCounter> counterProvider = new SharedCounterProvider(createdInstances);
        CounterConsumer firstConsumer = new CounterConsumer(counterProvider);
        CounterConsumer secondConsumer = new CounterConsumer(counterProvider);

        assertThat(createdInstances).hasValue(0);
        assertThat(firstConsumer.increment()).isEqualTo(1);
        assertThat(secondConsumer.increment()).isEqualTo(2);
        assertThat(firstConsumer.increment()).isEqualTo(3);
        assertThat(firstConsumer.counter()).isSameAs(secondConsumer.counter());
        assertThat(firstConsumer.counter().instanceNumber()).isEqualTo(1);
        assertThat(createdInstances).hasValue(1);
    }

    @Test
    void qualifierMembersCanCarryBindingAttributesForProviderSelection() {
        Map<String, Provider<NotificationChannel>> channels = Map.of(
                ChannelBinding.key(channel("email", 1)), () -> message -> "email-normal:" + message,
                ChannelBinding.key(channel("email", 10)), () -> message -> "email-urgent:" + message,
                ChannelBinding.key(channel("sms", 5)), () -> message -> "sms-priority:" + message);
        NotificationRouter router = new NotificationRouter(channels);

        assertThat(router.deliver(channel("email", 1), "build complete")).isEqualTo("email-normal:build complete");
        assertThat(router.deliver(channel("email", 10), "incident open")).isEqualTo("email-urgent:incident open");
        assertThat(router.deliver(channel("sms", 5), "deploy ready")).isEqualTo("sms-priority:deploy ready");
        assertThatThrownBy(() -> router.deliver(channel("email", 5), "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email")
                .hasMessageContaining("5");
    }

    private static Named named(String value) {
        return new Named() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Named.class;
            }
        };
    }

    private static Channel channel(String value, int priority) {
        return new Channel() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Channel.class;
            }
        };
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({FIELD, PARAMETER, METHOD, TYPE})
    private @interface Remote {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({FIELD, PARAMETER, METHOD, TYPE})
    private @interface Local {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({FIELD, PARAMETER, METHOD, TYPE})
    private @interface Channel {
        String value();

        int priority();
    }

    @Scope
    @Retention(RetentionPolicy.RUNTIME)
    @Target(TYPE)
    private @interface CachedScope {
    }

    @Singleton
    private static final class MessageTemplate {
        private final String salutation;

        private MessageTemplate(String salutation) {
            this.salutation = salutation;
        }

        private String render(String audience) {
            return salutation + ", " + audience;
        }
    }

    @CachedScope
    private static final class GreetingService {
        private final Provider<String> requestIdProvider;
        private final Provider<MessageTemplate> templateProvider;

        @Inject
        @Named
        private String defaultAudience;

        private String separator;
        private Provider<String> suffixProvider;

        @Inject
        private GreetingService(
                @Named("requestId") Provider<String> requestIdProvider,
                @Remote Provider<MessageTemplate> templateProvider) {
            this.requestIdProvider = requestIdProvider;
            this.templateProvider = templateProvider;
        }

        @Inject
        private void configure(@Named("separator") String separator, @Named("suffix") Provider<String> suffixProvider) {
            this.separator = separator;
            this.suffixProvider = suffixProvider;
        }

        private String greet(String audience) {
            String resolvedAudience = audience == null || audience.isBlank() ? defaultAudience : audience.trim();
            return requestIdProvider.get() + separator
                    + templateProvider.get().render(resolvedAudience)
                    + suffixProvider.get();
        }
    }

    private interface TextFormatter {
        String format(String value);
    }

    private static final class PlainTextFormatter implements TextFormatter {
        @Override
        public String format(String value) {
            return value;
        }
    }

    private static final class BracketedTextFormatter implements TextFormatter {
        @Override
        public String format(String value) {
            return "[" + value + "]";
        }
    }

    private static final class UppercaseTextFormatter implements TextFormatter {
        @Override
        public String format(String value) {
            return value.toUpperCase();
        }
    }

    private static final class FormatterRegistry {
        private final Map<String, Provider<TextFormatter>> formatters;

        @Inject
        private FormatterRegistry(Map<String, Provider<TextFormatter>> formatters) {
            this.formatters = formatters;
        }

        private String format(@Named("binding") Named bindingName, String value) {
            Provider<TextFormatter> formatter = formatters.get(bindingName.value());
            if (formatter == null) {
                throw new IllegalArgumentException("Unknown formatter binding: " + bindingName.value());
            }
            return formatter.get().format(value);
        }
    }

    private interface NotificationChannel {
        String send(String message);
    }

    private static final class ChannelBinding {
        private static String key(Channel channel) {
            return channel.value() + ":" + channel.priority();
        }
    }

    private static final class NotificationRouter {
        private final Map<String, Provider<NotificationChannel>> channels;

        @Inject
        private NotificationRouter(Map<String, Provider<NotificationChannel>> channels) {
            this.channels = channels;
        }

        private String deliver(Channel channel, String message) {
            Provider<NotificationChannel> provider = channels.get(ChannelBinding.key(channel));
            if (provider == null) {
                throw new IllegalArgumentException("Unknown notification channel: " + ChannelBinding.key(channel));
            }
            return provider.get().send(message);
        }
    }

    @CachedScope
    private static final class ScopedAggregator {
        private final Provider<String> remoteSource;

        @Inject
        @Local
        private Provider<String> localSource;

        private String joiner;

        @Inject
        private ScopedAggregator(@Remote Provider<String> remoteSource) {
            this.remoteSource = remoteSource;
        }

        @Inject
        private void setJoiner(@Named("joiner") String joiner) {
            this.joiner = joiner;
        }

        private String nextPair() {
            return remoteSource.get() + joiner + localSource.get();
        }

        private String scopeName() {
            return "cached";
        }
    }

    private static final class DeferredGreeter {
        private final Provider<Provider<String>> greetingProvider;

        @Inject
        private DeferredGreeter(Provider<Provider<String>> greetingProvider) {
            this.greetingProvider = greetingProvider;
        }

        private String greet(String audience) {
            return greetingProvider.get().get() + ", " + audience;
        }
    }

    private static final class CircularServices {
        private OrderService orderService;
        private InvoiceService invoiceService;
    }

    private static final class OrderService {
        private final Provider<InvoiceService> invoiceServiceProvider;
        private String acceptedOrderId;

        @Inject
        private OrderService(Provider<InvoiceService> invoiceServiceProvider) {
            this.invoiceServiceProvider = invoiceServiceProvider;
        }

        private String submit(String orderId) {
            acceptedOrderId = orderId;
            return "order " + orderId + " -> " + invoiceServiceProvider.get().describe(orderId);
        }

        private String status(String orderId) {
            return orderId.equals(acceptedOrderId) ? "accepted" : "pending";
        }
    }

    private static final class InvoiceService {
        private final Provider<OrderService> orderServiceProvider;

        @Inject
        private InvoiceService(Provider<OrderService> orderServiceProvider) {
            this.orderServiceProvider = orderServiceProvider;
        }

        private String describe(String orderId) {
            return "invoice for " + orderServiceProvider.get().status(orderId) + " " + orderId;
        }
    }

    private static final class CheckoutFlow {
        private final Provider<ShoppingCart> cartProvider;

        @Inject
        private CheckoutFlow(Provider<ShoppingCart> cartProvider) {
            this.cartProvider = cartProvider;
        }

        private Receipt checkout(List<String> items) {
            ShoppingCart cart = cartProvider.get();
            for (String item : items) {
                cart.add(item);
            }
            return new Receipt(cart.id(), cart.itemCount(), cart.summary());
        }
    }

    private static final class ShoppingCart {
        private final String id;
        private final StringBuilder summary = new StringBuilder();
        private int itemCount;

        private ShoppingCart(String id) {
            this.id = id;
        }

        private void add(String item) {
            if (summary.length() > 0) {
                summary.append(',');
            }
            summary.append(item);
            itemCount++;
        }

        private String id() {
            return id;
        }

        private int itemCount() {
            return itemCount;
        }

        private String summary() {
            return summary.toString();
        }
    }

    private record Receipt(String cartId, int itemCount, String summary) {
    }

    @Singleton
    private static final class SharedCounter {
        private final int instanceNumber;
        private int value;

        private SharedCounter(int instanceNumber) {
            this.instanceNumber = instanceNumber;
        }

        private int incrementAndGet() {
            value++;
            return value;
        }

        private int instanceNumber() {
            return instanceNumber;
        }
    }

    private static final class SharedCounterProvider implements Provider<SharedCounter> {
        private final AtomicInteger createdInstances;
        private SharedCounter counter;

        @Inject
        private SharedCounterProvider(AtomicInteger createdInstances) {
            this.createdInstances = createdInstances;
        }

        @Override
        public SharedCounter get() {
            if (counter == null) {
                counter = new SharedCounter(createdInstances.incrementAndGet());
            }
            return counter;
        }
    }

    private static final class CounterConsumer {
        private final Provider<SharedCounter> counterProvider;

        @Inject
        private CounterConsumer(Provider<SharedCounter> counterProvider) {
            this.counterProvider = counterProvider;
        }

        private int increment() {
            return counterProvider.get().incrementAndGet();
        }

        private SharedCounter counter() {
            return counterProvider.get();
        }
    }
}
