/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_inject.javax_inject;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
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
    void annotationInstancesExposeTheirPublicContracts() {
        Named namedBinding = named("primary");
        Named defaultBinding = named("");
        Inject inject = inject();
        Qualifier qualifier = qualifier();
        Scope scope = scope();
        Singleton singleton = singleton();
        Provider<List<String>> provider = () -> List.of(namedBinding.value(), defaultBinding.value(), "provided");

        assertThat(namedBinding.annotationType()).isSameAs(Named.class);
        assertThat(namedBinding.value()).isEqualTo("primary");
        assertThat(defaultBinding.value()).isEmpty();
        assertThat(inject.annotationType()).isSameAs(Inject.class);
        assertThat(qualifier.annotationType()).isSameAs(Qualifier.class);
        assertThat(scope.annotationType()).isSameAs(Scope.class);
        assertThat(singleton.annotationType()).isSameAs(Singleton.class);
        assertThat(provider.get()).containsExactly("primary", "", "provided");
    }

    @Test
    void injectAnnotationsCanDescribeConstructorFieldAndMethodSuppliedCollaborators() {
        AtomicInteger issuedRequestIds = new AtomicInteger();
        Provider<String> requestIds = () -> "request-" + issuedRequestIds.incrementAndGet();
        Provider<MessageTemplate> templates = () -> new MessageTemplate("Hello");
        GreetingService service = new GreetingService(requestIds, templates);

        service.defaultAudience = "team";
        service.configure(" ", () -> "!");

        assertThat(service.greet(null)).isEqualTo("request-1 Hello, team!");
        assertThat(service.greet("native image")).isEqualTo("request-2 Hello, native image!");
        assertThat(service.greet("  metadata  ")).isEqualTo("request-3 Hello, metadata!");
    }

    @Test
    void injectAnnotationsCanDescribeStaticMembersUsedByApplicationWideServices() {
        ApplicationStartup.reset();
        ApplicationStartup.environmentName = "native-image";
        ApplicationStartup.registerHook(new StartupHook("database"));
        ApplicationStartup.registerHook(new StartupHook("scheduler"));

        assertThat(ApplicationStartup.runHooks()).containsExactly("native-image:database", "native-image:scheduler");
        ApplicationStartup.reset();
        assertThat(ApplicationStartup.runHooks()).isEmpty();
    }

    @Test
    void namedBindingsSelectIndependentProvidersForTheSameServiceType() {
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
    void customQualifiersCanCarryBindingAttributesForProviderSelection() {
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
                .hasMessageContaining("email:5");
    }

    @Test
    void markerQualifiersDistinguishCollaboratorsWithoutBindingAttributes() {
        Provider<PaymentGateway> localGateway = () -> new LocalPaymentGateway("terminal");
        Provider<PaymentGateway> remoteGateway = () -> new RemotePaymentGateway("processor");
        PaymentAuthorizer authorizer = new PaymentAuthorizer(localGateway, remoteGateway);

        assertThat(authorizer.authorize(local(), "order-17", 1250)).isEqualTo("local:terminal:order-17:1250");
        assertThat(authorizer.authorize(remote(), "order-18", 3199)).isEqualTo("remote:processor:order-18:3199");
    }

    @Test
    void scopeAnnotationsCanModelSharedAndUnscopedProviderLifecycles() {
        AtomicInteger createdCounters = new AtomicInteger();
        Provider<SharedCounter> singletonProvider = new SharedCounterProvider(createdCounters);
        Provider<ShoppingCart> cartProvider = new PrototypeCartProvider();
        CounterConsumer firstConsumer = new CounterConsumer(singletonProvider);
        CounterConsumer secondConsumer = new CounterConsumer(singletonProvider);
        CheckoutFlow checkoutFlow = new CheckoutFlow(cartProvider);

        Receipt firstReceipt = checkoutFlow.checkout(List.of("book", "pen"));
        Receipt secondReceipt = checkoutFlow.checkout(List.of("notebook"));

        assertThat(firstConsumer.increment()).isEqualTo(1);
        assertThat(secondConsumer.increment()).isEqualTo(2);
        assertThat(firstConsumer.counter()).isSameAs(secondConsumer.counter());
        assertThat(firstConsumer.counter().instanceNumber()).isEqualTo(1);
        assertThat(createdCounters).hasValue(1);
        assertThat(firstReceipt.cartId()).isEqualTo("cart-1");
        assertThat(firstReceipt.itemCount()).isEqualTo(2);
        assertThat(firstReceipt.summary()).isEqualTo("book,pen");
        assertThat(secondReceipt.cartId()).isEqualTo("cart-2");
        assertThat(secondReceipt.itemCount()).isEqualTo(1);
        assertThat(secondReceipt.summary()).isEqualTo("notebook");
    }

    @Test
    void providersDeferStrategySelectionUntilEachLookup() {
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

    private static Inject inject() {
        return new Inject() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Inject.class;
            }
        };
    }

    private static Qualifier qualifier() {
        return new Qualifier() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Qualifier.class;
            }
        };
    }

    private static Scope scope() {
        return new Scope() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Scope.class;
            }
        };
    }

    private static Singleton singleton() {
        return new Singleton() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Singleton.class;
            }
        };
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

    private static Local local() {
        return new Local() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Local.class;
            }
        };
    }

    private static Remote remote() {
        return new Remote() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Remote.class;
            }
        };
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({FIELD, PARAMETER, METHOD, TYPE})
    private @interface Local {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({FIELD, PARAMETER, METHOD, TYPE})
    private @interface Remote {
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
    private @interface RequestScoped {
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

    @RequestScoped
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

    private record StartupHook(String name) {
        private String start(String environmentName) {
            return environmentName + ":" + name;
        }
    }

    private static final class ApplicationStartup {
        @Inject
        private static String environmentName;

        private static final Queue<StartupHook> hooks = new ArrayDeque<>();

        private ApplicationStartup() {
        }

        @Inject
        private static void registerHook(StartupHook hook) {
            hooks.add(hook);
        }

        private static List<String> runHooks() {
            Queue<String> startedHooks = new ArrayDeque<>();
            while (!hooks.isEmpty()) {
                startedHooks.add(hooks.remove().start(environmentName));
            }
            return List.copyOf(startedHooks);
        }

        private static void reset() {
            environmentName = "";
            hooks.clear();
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
            return value.toUpperCase(Locale.ROOT);
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

    private interface PaymentGateway {
        String authorize(String orderId, int cents);
    }

    private static final class LocalPaymentGateway implements PaymentGateway {
        private final String terminalName;

        private LocalPaymentGateway(String terminalName) {
            this.terminalName = terminalName;
        }

        @Override
        public String authorize(String orderId, int cents) {
            return "local:" + terminalName + ":" + orderId + ":" + cents;
        }
    }

    private static final class RemotePaymentGateway implements PaymentGateway {
        private final String processorName;

        private RemotePaymentGateway(String processorName) {
            this.processorName = processorName;
        }

        @Override
        public String authorize(String orderId, int cents) {
            return "remote:" + processorName + ":" + orderId + ":" + cents;
        }
    }

    private static final class PaymentAuthorizer {
        private final Provider<PaymentGateway> localGateway;
        private final Provider<PaymentGateway> remoteGateway;

        @Inject
        private PaymentAuthorizer(
                @Local Provider<PaymentGateway> localGateway,
                @Remote Provider<PaymentGateway> remoteGateway) {
            this.localGateway = localGateway;
            this.remoteGateway = remoteGateway;
        }

        private String authorize(@Local Local qualifier, String orderId, int cents) {
            return localGateway.get().authorize(orderId, cents);
        }

        private String authorize(@Remote Remote qualifier, String orderId, int cents) {
            return remoteGateway.get().authorize(orderId, cents);
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

    private static final class PrototypeCartProvider implements Provider<ShoppingCart> {
        private int issuedCartIds;

        @Override
        public ShoppingCart get() {
            issuedCartIds++;
            return new ShoppingCart("cart-" + issuedCartIds);
        }
    }

    private static final class ShoppingCart {
        private final String id;
        private final Queue<String> items = new ArrayDeque<>();

        private ShoppingCart(String id) {
            this.id = id;
        }

        private void add(String item) {
            items.add(item);
        }

        private String id() {
            return id;
        }

        private int itemCount() {
            return items.size();
        }

        private String summary() {
            return String.join(",", items);
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
