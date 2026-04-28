/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_atinject_1_0_spec;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Scope;
import javax.inject.Singleton;
import org.junit.jupiter.api.Test;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static org.assertj.core.api.Assertions.assertThat;

public class Geronimo_atinject_1_0_specTest {
    @Test
    void publicAnnotationContractsCanBeImplementedAndUsedAsNormalAnnotationInstances() {
        Named defaultNamed = namedAnnotation("");
        Named primaryNamed = namedAnnotation("primary");
        Inject inject = injectAnnotation();
        Qualifier qualifier = qualifierAnnotation();
        Scope scope = scopeAnnotation();
        Singleton singleton = singletonAnnotation();

        assertThat(defaultNamed.annotationType()).isSameAs(Named.class);
        assertThat(defaultNamed.value()).isEmpty();
        assertThat(primaryNamed.annotationType()).isSameAs(Named.class);
        assertThat(primaryNamed.value()).isEqualTo("primary");
        assertThat(inject.annotationType()).isSameAs(Inject.class);
        assertThat(qualifier.annotationType()).isSameAs(Qualifier.class);
        assertThat(scope.annotationType()).isSameAs(Scope.class);
        assertThat(singleton.annotationType()).isSameAs(Singleton.class);
    }

    @Test
    void providerSuppliesFreshValuesAndCanBeComposedIntoServices() {
        AtomicInteger sequence = new AtomicInteger();
        Provider<String> requestIdProvider = () -> "request-" + sequence.incrementAndGet();
        Provider<List<String>> itemProvider = () -> List.of("alpha", "beta", "gamma");
        Provider<OrderFormatter> formatterProvider = () -> new OrderFormatter(Locale.ROOT);

        OrderService service = new OrderService(requestIdProvider, itemProvider);
        service.setFormatter(formatterProvider);

        assertThat(service.describeOrder("Ada")).isEqualTo("request-1|ADA|ALPHA,BETA,GAMMA");
        assertThat(service.describeOrder("Grace")).isEqualTo("request-2|GRACE|ALPHA,BETA,GAMMA");
        assertThat(service.describeOrder("metadata")).isEqualTo("request-3|METADATA|ALPHA,BETA,GAMMA");
    }

    @Test
    void nestedProvidersAllowLateSelectionOfInjectionCandidates() {
        AtomicInteger selectedRegion = new AtomicInteger();
        Provider<ShippingCalculator> domesticCalculator = () -> new FlatRateShippingCalculator("domestic", 6);
        Provider<ShippingCalculator> internationalCalculator = () ->
                new FlatRateShippingCalculator("international", 24);
        Provider<Provider<ShippingCalculator>> selectedCalculator = () -> selectedRegion.get() == 0
                ? domesticCalculator
                : internationalCalculator;
        CheckoutService service = new CheckoutService(selectedCalculator);

        assertThat(service.quote(4)).isEqualTo("domestic:10");

        selectedRegion.set(1);
        assertThat(service.quote(4)).isEqualTo("international:28");

        selectedRegion.set(0);
        assertThat(service.quote(1)).isEqualTo("domestic:7");
    }

    @Test
    void customQualifiersScopesAndSingletonAnnotatedTypesWorkWithUserComponents() {
        Provider<List<String>> auditSinkProvider = ArrayList::new;
        Provider<AuditMessageFactory> messageFactoryProvider = () -> new AuditMessageFactory("security");
        AuditService auditService = new AuditService(auditSinkProvider, messageFactoryProvider);

        auditService.record("login");
        auditService.record("logout");

        assertThat(auditService.events()).containsExactly("security:login", "security:logout");
        assertThat(auditService.channel()).isEqualTo("named-audit-channel");
    }

    @Test
    void providerInterfaceCanBeImplementedByClassesAnonymousClassesAndLambdas() {
        Provider<String> classProvider = new ConstantProvider("class-provider");
        Provider<String> anonymousProvider = new Provider<String>() {
            @Override
            public String get() {
                return classProvider.get() + ":anonymous-provider";
            }
        };
        Provider<String> lambdaProvider = () -> anonymousProvider.get() + ":lambda-provider";

        assertThat(classProvider.get()).isEqualTo("class-provider");
        assertThat(anonymousProvider.get()).isEqualTo("class-provider:anonymous-provider");
        assertThat(lambdaProvider.get()).isEqualTo("class-provider:anonymous-provider:lambda-provider");
    }

    @Test
    void namedQualifiersSelectSpecificProvidersWhenMultipleBindingsShareTheSameType() {
        Named defaultFormatter = namedAnnotation("");
        Named compactFormatter = namedAnnotation("compact");
        Named detailedFormatter = namedAnnotation("detailed");
        NamedFormatterRegistry registry = new NamedFormatterRegistry();
        registry.register(defaultFormatter, () -> new LabelFormatter("DEFAULT", Locale.ROOT));
        registry.register(compactFormatter, () -> new LabelFormatter("CMP", Locale.ROOT));
        registry.register(detailedFormatter, () -> new LabelFormatter("DETAIL", Locale.ROOT));
        ShippingLabelService service = new ShippingLabelService(registry);

        assertThat(service.label(defaultFormatter, "box")).isEqualTo("DEFAULT-BOX");
        assertThat(service.label(compactFormatter, "crate")).isEqualTo("CMP-CRATE");
        assertThat(service.label(detailedFormatter, "pallet")).isEqualTo("DETAIL-PALLET");
    }

    @Test
    void providersCanDeferLookupToResolveMutuallyDependentComponents() {
        AtomicInteger cartLookups = new AtomicInteger();
        AtomicReference<Cart> cartReference = new AtomicReference<>();
        Provider<Cart> cartProvider = () -> {
            cartLookups.incrementAndGet();
            return cartReference.get();
        };
        DiscountPolicy discountPolicy = new DiscountPolicy(cartProvider, 10);
        Cart cart = new Cart(List.of(25, 15, 10), discountPolicy);
        cartReference.set(cart);

        assertThat(cartLookups).hasValue(0);
        assertThat(cart.totalAfterDiscount()).isEqualTo(45);
        assertThat(cartLookups).hasValue(1);
    }

    private static Named namedAnnotation(String name) {
        return new Named() {
            @Override
            public String value() {
                return name;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Named.class;
            }
        };
    }

    private static Inject injectAnnotation() {
        return new Inject() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Inject.class;
            }
        };
    }

    private static Qualifier qualifierAnnotation() {
        return new Qualifier() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Qualifier.class;
            }
        };
    }

    private static Scope scopeAnnotation() {
        return new Scope() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Scope.class;
            }
        };
    }

    private static Singleton singletonAnnotation() {
        return new Singleton() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Singleton.class;
            }
        };
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({FIELD, PARAMETER})
    private @interface AuditChannel {
    }

    @Scope
    @Retention(RetentionPolicy.RUNTIME)
    @Target(TYPE)
    private @interface RequestScoped {
    }

    @Singleton
    private static final class AuditMessageFactory {
        private final String category;

        private AuditMessageFactory(String category) {
            this.category = category;
        }

        private String create(String event) {
            return category + ":" + event;
        }
    }

    @RequestScoped
    private static final class AuditService {
        private final List<String> events;
        private final Provider<AuditMessageFactory> messageFactoryProvider;

        @Inject
        @Named("audit-channel")
        @AuditChannel
        private String channel = "named-audit-channel";

        @Inject
        private AuditService(Provider<List<String>> eventSinkProvider,
                @AuditChannel Provider<AuditMessageFactory> messageFactoryProvider) {
            this.events = eventSinkProvider.get();
            this.messageFactoryProvider = messageFactoryProvider;
        }

        private void record(String event) {
            events.add(messageFactoryProvider.get().create(event));
        }

        private List<String> events() {
            return events;
        }

        private String channel() {
            return channel;
        }
    }

    private static final class OrderService {
        private final Provider<String> requestIdProvider;
        private final Provider<List<String>> itemProvider;
        private Provider<OrderFormatter> formatterProvider;

        @Inject
        private OrderService(@Named("request-id") Provider<String> requestIdProvider,
                @Named("items") Provider<List<String>> itemProvider) {
            this.requestIdProvider = requestIdProvider;
            this.itemProvider = itemProvider;
        }

        @Inject
        private void setFormatter(Provider<OrderFormatter> formatterProvider) {
            this.formatterProvider = formatterProvider;
        }

        private String describeOrder(String customer) {
            return formatterProvider.get().format(requestIdProvider.get(), customer, itemProvider.get());
        }
    }

    private static final class OrderFormatter {
        private final Locale locale;

        private OrderFormatter(Locale locale) {
            this.locale = locale;
        }

        private String format(String requestId, String customer, List<String> items) {
            String joinedItems = String.join(",", items).toUpperCase(locale);
            return requestId + "|" + customer.toUpperCase(locale) + "|" + joinedItems;
        }
    }

    private interface ShippingCalculator {
        String quote(int itemCount);
    }

    private static final class FlatRateShippingCalculator implements ShippingCalculator {
        private final String region;
        private final int baseRate;

        private FlatRateShippingCalculator(String region, int baseRate) {
            this.region = region;
            this.baseRate = baseRate;
        }

        @Override
        public String quote(int itemCount) {
            return region + ":" + (baseRate + itemCount);
        }
    }

    private static final class CheckoutService {
        private final Provider<Provider<ShippingCalculator>> shippingCalculatorProvider;

        private CheckoutService(Provider<Provider<ShippingCalculator>> shippingCalculatorProvider) {
            this.shippingCalculatorProvider = shippingCalculatorProvider;
        }

        private String quote(int itemCount) {
            return shippingCalculatorProvider.get().get().quote(itemCount);
        }
    }

    private static final class ConstantProvider implements Provider<String> {
        private final String value;

        private ConstantProvider(String value) {
            this.value = value;
        }

        @Override
        public String get() {
            return value;
        }
    }

    private static final class Cart {
        private final List<Integer> prices;
        private final DiscountPolicy discountPolicy;

        @Inject
        private Cart(List<Integer> prices, DiscountPolicy discountPolicy) {
            this.prices = prices;
            this.discountPolicy = discountPolicy;
        }

        private int subtotal() {
            return prices.stream().mapToInt(Integer::intValue).sum();
        }

        private int totalAfterDiscount() {
            return subtotal() - discountPolicy.discount();
        }
    }

    private static final class DiscountPolicy {
        private final Provider<Cart> cartProvider;
        private final int minimumSubtotal;

        @Inject
        private DiscountPolicy(Provider<Cart> cartProvider, int minimumSubtotal) {
            this.cartProvider = cartProvider;
            this.minimumSubtotal = minimumSubtotal;
        }

        private int discount() {
            Cart cart = cartProvider.get();
            return cart.subtotal() >= minimumSubtotal ? 5 : 0;
        }
    }

    private static final class NamedFormatterRegistry {
        private final Map<String, Provider<LabelFormatter>> formatterProviders = new HashMap<>();

        private void register(Named qualifier, Provider<LabelFormatter> formatterProvider) {
            formatterProviders.put(qualifier.value(), formatterProvider);
        }

        private LabelFormatter formatter(Named qualifier) {
            return formatterProviders.get(qualifier.value()).get();
        }
    }

    private static final class LabelFormatter {
        private final String prefix;
        private final Locale locale;

        private LabelFormatter(String prefix, Locale locale) {
            this.prefix = prefix;
            this.locale = locale;
        }

        private String format(String item) {
            return prefix + "-" + item.toUpperCase(locale);
        }
    }

    private static final class ShippingLabelService {
        private final NamedFormatterRegistry formatterRegistry;

        @Inject
        private ShippingLabelService(NamedFormatterRegistry formatterRegistry) {
            this.formatterRegistry = formatterRegistry;
        }

        private String label(@Named("formatter") Named formatterQualifier, String item) {
            return formatterRegistry.formatter(formatterQualifier).format(item);
        }
    }
}
