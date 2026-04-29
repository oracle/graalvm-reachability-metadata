/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_money.money_api;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.money.CurrencyContext;
import javax.money.CurrencyContextBuilder;
import javax.money.CurrencyQuery;
import javax.money.CurrencyQueryBuilder;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.MonetaryAmountFactory;
import javax.money.MonetaryAmountFactoryQuery;
import javax.money.MonetaryAmountFactoryQueryBuilder;
import javax.money.MonetaryContext;
import javax.money.MonetaryContextBuilder;
import javax.money.MonetaryException;
import javax.money.MonetaryOperator;
import javax.money.MonetaryQuery;
import javax.money.MonetaryRounding;
import javax.money.NumberValue;
import javax.money.RoundingContext;
import javax.money.RoundingContextBuilder;
import javax.money.RoundingQuery;
import javax.money.RoundingQueryBuilder;
import javax.money.UnknownCurrencyException;
import javax.money.convert.ConversionContext;
import javax.money.convert.ConversionContextBuilder;
import javax.money.convert.ConversionQuery;
import javax.money.convert.ConversionQueryBuilder;
import javax.money.convert.CurrencyConversion;
import javax.money.convert.ExchangeRate;
import javax.money.convert.ExchangeRateProvider;
import javax.money.convert.MonetaryConversions;
import javax.money.convert.ProviderContext;
import javax.money.convert.ProviderContextBuilder;
import javax.money.convert.RateType;
import javax.money.format.AmountFormatContext;
import javax.money.format.AmountFormatContextBuilder;
import javax.money.format.AmountFormatQuery;
import javax.money.format.AmountFormatQueryBuilder;
import javax.money.format.MonetaryAmountFormat;
import javax.money.format.MonetaryFormats;
import javax.money.format.MonetaryParseException;
import javax.money.spi.Bootstrap;
import javax.money.spi.CurrencyProviderSpi;
import javax.money.spi.MonetaryAmountsSingletonQuerySpi;
import javax.money.spi.MonetaryAmountsSingletonSpi;
import javax.money.spi.MonetaryConversionsSingletonSpi;
import javax.money.spi.MonetaryCurrenciesSingletonSpi;
import javax.money.spi.MonetaryFormatsSingletonSpi;
import javax.money.spi.MonetaryRoundingsSingletonSpi;
import javax.money.spi.RoundingProviderSpi;
import javax.money.spi.ServiceProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Money_apiTest {

    private static final CurrencyUnit USD = new TestCurrency("USD", 840, 2, "ISO-4217");
    private static final CurrencyUnit EUR = new TestCurrency("EUR", 978, 2, "ISO-4217");
    private static final CurrencyUnit JPY = new TestCurrency("JPY", 392, 0, "ISO-4217");

    @BeforeAll
    static void installDeterministicProvider() {
        Bootstrap.init(new TestServiceProvider());
    }

    @Test
    void currencyContextsAndQueriesCarryTypedAttributesAndFilters() {
        CurrencyContext context = CurrencyContextBuilder.of("ISO-4217")
                .set("territory", "United States")
                .set("minorUnit", 2)
                .set(Locale.US)
                .build();

        assertThat(context.getProviderName()).isEqualTo("ISO-4217");
        assertThat(context.getText("territory")).isEqualTo("United States");
        assertThat(context.getInt("minorUnit")).isEqualTo(2);
        assertThat(context.get(Locale.class)).isEqualTo(Locale.US);
        assertThat(context.getKeys(String.class)).contains("territory", "provider");

        CurrencyContext copied = CurrencyContextBuilder.of(context)
                .setProviderName("test-provider")
                .removeAttributes("territory")
                .build();
        assertThat(copied.getProviderName()).isEqualTo("test-provider");
        assertThat(copied.getText("territory")).isNull();
        assertThat(copied.getInt("minorUnit")).isEqualTo(2);

        CurrencyQuery query = CurrencyQueryBuilder.of()
                .setProviderNames("test-provider")
                .setTargetType(CurrencyUnit.class)
                .setCountries(Locale.US, Locale.GERMANY)
                .setCurrencyCodes("USD", "EUR")
                .setNumericCodes(840, 978)
                .build();

        assertThat(query.getProviderNames()).containsExactly("test-provider");
        assertThat(query.getTargetType()).isEqualTo(CurrencyUnit.class);
        assertThat(query.getCountries()).containsExactly(Locale.US, Locale.GERMANY);
        assertThat(query.getCurrencyCodes()).containsExactly("USD", "EUR");
        assertThat(query.getNumericCodes()).containsExactly(840, 978);

        CurrencyQuery copiedQuery = query.toBuilder()
                .setCurrencyCodes("JPY")
                .setNumericCodes(392)
                .build();
        assertThat(copiedQuery.getCurrencyCodes()).containsExactly("JPY");
        assertThat(copiedQuery.getNumericCodes()).containsExactly(392);
        assertThat(copiedQuery.getCountries()).containsExactly(Locale.US, Locale.GERMANY);
    }

    @Test
    void monetaryContextsFactoryQueriesAndFactoryDefaultsRoundTripState() {
        MonetaryContext context = MonetaryContextBuilder.of()
                .setAmountType(SimpleAmount.class)
                .setPrecision(12)
                .setMaxScale(4)
                .setFixedScale(true)
                .set("usage", "ledger")
                .build();

        assertThat(context.getAmountType()).isEqualTo(SimpleAmount.class);
        assertThat(context.getPrecision()).isEqualTo(12);
        assertThat(context.getMaxScale()).isEqualTo(4);
        assertThat(context.isFixedScale()).isTrue();
        assertThat(context.getText("usage")).isEqualTo("ledger");

        MonetaryAmountFactoryQuery query = MonetaryAmountFactoryQueryBuilder.of()
                .setProviderNames("amount-provider")
                .setTargetType(SimpleAmount.class)
                .setPrecision(8)
                .setMaxScale(2)
                .setFixedScale(false)
                .build();

        assertThat(query.getProviderNames()).containsExactly("amount-provider");
        assertThat(query.getTargetType()).isEqualTo(SimpleAmount.class);
        assertThat(query.getPrecision()).isEqualTo(8);
        assertThat(query.getMaxScale()).isEqualTo(2);
        assertThat(query.isFixedScale()).isFalse();

        MonetaryContext contextFromQuery = MonetaryContext.from(query, SimpleAmount.class);
        assertThat(contextFromQuery.getAmountType()).isEqualTo(SimpleAmount.class);
        assertThat(contextFromQuery.getPrecision()).isEqualTo(8);
        assertThat(contextFromQuery.getMaxScale()).isEqualTo(2);
        assertThat(contextFromQuery.isFixedScale()).isFalse();

        SimpleAmount original = new SimpleAmount(new BigDecimal("123.45"), USD, context);
        SimpleAmount copied = new SimpleAmountFactory()
                .setContext(context)
                .setAmount(original)
                .create();

        assertThat(copied.getCurrency()).isEqualTo(USD);
        assertThat(copied.getNumber().numberValue(BigDecimal.class)).isEqualByComparingTo("123.45");
        assertThat(copied.getContext()).isEqualTo(context);
        assertThat(new SimpleAmountFactory().getMaximalMonetaryContext())
                .isEqualTo(new SimpleAmountFactory().getDefaultMonetaryContext());
    }

    @Test
    void staticAmountFactoryFacadeResolvesRegisteredFactoriesByTypeAndQuery() {
        assertThat(Monetary.getDefaultAmountType()).isEqualTo(SimpleAmount.class);
        assertThat(Monetary.getAmountTypes()).containsExactly(SimpleAmount.class);
        assertThat(Monetary.getAmountFactories())
                .singleElement()
                .extracting(MonetaryAmountFactory::getAmountType)
                .isEqualTo(SimpleAmount.class);

        MonetaryAmountFactory<SimpleAmount> typedFactory = Monetary.getAmountFactory(SimpleAmount.class);
        SimpleAmount usdAmount = typedFactory
                .setCurrency(USD)
                .setNumber(new BigDecimal("5.25"))
                .create();
        assertThat(usdAmount.getCurrency()).isEqualTo(USD);
        assertThat(usdAmount.getNumber().numberValue(BigDecimal.class)).isEqualByComparingTo("5.25");

        MonetaryAmountFactoryQuery query = MonetaryAmountFactoryQueryBuilder.of()
                .setTargetType(SimpleAmount.class)
                .setPrecision(18)
                .setMaxScale(2)
                .build();
        MonetaryContext queryContext = MonetaryContext.from(query, SimpleAmount.class);

        assertThat(Monetary.isAvailable(query)).isTrue();
        assertThat(Monetary.getAmountFactories(query))
                .singleElement()
                .extracting(MonetaryAmountFactory::getAmountType)
                .isEqualTo(SimpleAmount.class);

        MonetaryAmountFactory<?> queriedFactory = Monetary.getAmountFactory(query);
        MonetaryAmount eurAmount = queriedFactory
                .setContext(queryContext)
                .setCurrency(EUR)
                .setNumber(15L)
                .create();
        assertThat(eurAmount.getCurrency()).isEqualTo(EUR);
        assertThat(eurAmount.getNumber().numberValue(BigDecimal.class)).isEqualByComparingTo("15");
        assertThat(eurAmount.getContext().getPrecision()).isEqualTo(18);
        assertThat(eurAmount.getContext().getMaxScale()).isEqualTo(2);
    }

    @Test
    void monetaryAmountDefaultMethodsDelegateToQueriesOperatorsAndSignum() {
        MonetaryContext context = MonetaryContextBuilder.of(SimpleAmount.class)
                .setPrecision(16)
                .setMaxScale(6)
                .build();
        SimpleAmount amount = new SimpleAmount(new BigDecimal("10.50"), USD, context);

        MonetaryQuery<String> describe = value -> value.getCurrency().getCurrencyCode() + " "
                + value.getNumber().numberValue(BigDecimal.class).toPlainString();
        MonetaryOperator addTax = value -> value.multiply(new BigDecimal("1.20"));

        assertThat(amount.query(describe)).isEqualTo("USD 10.50");
        assertThat(amount.with(addTax).getNumber().numberValue(BigDecimal.class)).isEqualByComparingTo("12.600");
        assertThat(amount.isPositive()).isTrue();
        assertThat(amount.isPositiveOrZero()).isTrue();
        assertThat(amount.isNegative()).isFalse();
        assertThat(amount.isZero()).isFalse();

        SimpleAmount zero = new SimpleAmount(BigDecimal.ZERO, USD, context);
        SimpleAmount negative = new SimpleAmount(new BigDecimal("-0.01"), USD, context);
        assertThat(zero.isZero()).isTrue();
        assertThat(zero.isNegativeOrZero()).isTrue();
        assertThat(zero.isPositiveOrZero()).isTrue();
        assertThat(negative.isNegative()).isTrue();
        assertThat(negative.abs().getNumber().numberValue(BigDecimal.class)).isEqualByComparingTo("0.01");
        assertThat(amount.compareTo(new SimpleAmount(new BigDecimal("9.99"), USD, context))).isPositive();
    }

    @Test
    void conversionRoundingAndFormattingBuildersExposeSpecializedAttributes() {
        ProviderContext providerContext = ProviderContextBuilder.of("test-fx", RateType.REALTIME, RateType.HISTORIC)
                .set("source", "in-memory")
                .build();
        ConversionContext conversionContext = ConversionContextBuilder.create(providerContext, RateType.REALTIME)
                .set("quoteId", "Q-1")
                .build();
        ConversionQuery conversionQuery = ConversionQueryBuilder.of()
                .setProviderNames("test-fx")
                .setBaseCurrency(USD)
                .setTermCurrency(EUR)
                .setRateTypes(RateType.REALTIME, RateType.HISTORIC)
                .build();

        assertThat(providerContext.getProviderName()).isEqualTo("test-fx");
        assertThat(providerContext.getRateTypes()).containsExactlyInAnyOrder(RateType.REALTIME, RateType.HISTORIC);
        assertThat(conversionContext.getProviderName()).isEqualTo("test-fx");
        assertThat(conversionContext.getRateType()).isEqualTo(RateType.REALTIME);
        assertThat(conversionContext.getText("quoteId")).isEqualTo("Q-1");
        assertThat(conversionQuery.getBaseCurrency()).isEqualTo(USD);
        assertThat(conversionQuery.getCurrency()).isEqualTo(EUR);
        assertThat(conversionQuery.getRateTypes()).containsExactlyInAnyOrder(RateType.REALTIME, RateType.HISTORIC);

        RoundingContext roundingContext = RoundingContextBuilder.of("test-rounding", "nearest-cent")
                .setCurrency(USD)
                .build();
        RoundingQuery roundingQuery = RoundingQueryBuilder.of()
                .setProviderNames("test-rounding")
                .setCurrency(USD)
                .setRoundingName("nearest-cent")
                .setScale(2)
                .build();

        assertThat(roundingContext.getProviderName()).isEqualTo("test-rounding");
        assertThat(roundingContext.getRoundingName()).isEqualTo("nearest-cent");
        assertThat(roundingContext.getCurrency()).isEqualTo(USD);
        assertThat(roundingQuery.getProviderNames()).containsExactly("test-rounding");
        assertThat(roundingQuery.getCurrency()).isEqualTo(USD);
        assertThat(roundingQuery.getRoundingName()).isEqualTo("nearest-cent");
        assertThat(roundingQuery.getScale()).isEqualTo(2);

        AmountFormatContext formatContext = AmountFormatContextBuilder.of(Locale.US)
                .setFormatName("plain")
                .setMonetaryAmountFactory(new SimpleAmountFactory())
                .build();
        AmountFormatQuery formatQuery = AmountFormatQueryBuilder.of(formatContext.getLocale())
                .setProviderNames("test-format")
                .setFormatName("plain")
                .setMonetaryAmountFactory(new SimpleAmountFactory().setCurrency(USD))
                .build();

        assertThat(formatContext.getLocale()).isEqualTo(Locale.US);
        assertThat(formatContext.getFormatName()).isEqualTo("plain");
        assertThat(formatContext.getParseFactory()).isInstanceOf(SimpleAmountFactory.class);
        assertThat(formatQuery.getProviderNames()).containsExactly("test-format");
        assertThat(formatQuery.getLocale()).isEqualTo(Locale.US);
        assertThat(formatQuery.getFormatName()).isEqualTo("plain");
        assertThat(formatQuery.getMonetaryAmountFactory()).isInstanceOf(SimpleAmountFactory.class);
    }

    @Test
    void exchangeRateProviderConvenienceMethodsResolveCurrenciesAndReverseRates() {
        ExchangeRateProvider provider = MonetaryConversions.getExchangeRateProvider("test-fx");

        ExchangeRate usdToEur = provider.getExchangeRate("USD", "EUR");
        assertThat(usdToEur.getBaseCurrency()).isEqualTo(USD);
        assertThat(usdToEur.getCurrency()).isEqualTo(EUR);
        assertThat(usdToEur.getFactor().numberValue(BigDecimal.class)).isEqualByComparingTo("0.90");
        assertThat(provider.isAvailable(USD, EUR)).isTrue();
        assertThat(provider.isAvailable(ConversionQueryBuilder.of()
                .setProviderNames("other-fx")
                .build())).isFalse();

        ExchangeRate eurToUsd = provider.getReversed(usdToEur);
        assertThat(eurToUsd.getBaseCurrency()).isEqualTo(EUR);
        assertThat(eurToUsd.getCurrency()).isEqualTo(USD);
        assertThat(eurToUsd.getFactor().numberValue(BigDecimal.class)).isEqualByComparingTo("1.11");

        CurrencyConversion jpyConversion = provider.getCurrencyConversion("JPY");
        assertThat(jpyConversion.getCurrency()).isEqualTo(JPY);
    }

    @Test
    void staticFacadesUseInstalledCurrencyRoundingFormatAndConversionServices() {
        assertThat(Monetary.getCurrencyProviderNames()).containsExactly("test-currency");
        assertThat(Monetary.getDefaultCurrencyProviderChain()).containsExactly("test-currency");
        assertThat(Monetary.getCurrency("USD")).isEqualTo(USD);
        assertThat(Monetary.getCurrency(Locale.JAPAN)).isEqualTo(JPY);
        assertThat(Monetary.isCurrencyAvailable("EUR")).isTrue();
        assertThat(Monetary.getCurrencies(CurrencyQueryBuilder.of().setCurrencyCodes("USD", "EUR").build()))
                .containsExactlyInAnyOrder(USD, EUR);
        assertThatThrownBy(() -> Monetary.getCurrency("XXX"))
                .isInstanceOf(UnknownCurrencyException.class)
                .hasMessageContaining("XXX");

        MonetaryRounding rounding = Monetary.getRounding(RoundingQueryBuilder.of()
                .setRoundingName("nearest-cent")
                .setScale(2)
                .build());
        SimpleAmount rounded = (SimpleAmount) rounding.apply(new SimpleAmount(new BigDecimal("10.129"), USD));
        assertThat(rounded.getNumber().numberValue(BigDecimal.class)).isEqualByComparingTo("10.13");
        assertThat(Monetary.isRoundingAvailable("nearest-cent")).isTrue();
        assertThat(Monetary.getRoundingNames()).containsExactly("nearest-cent");

        MonetaryAmountFormat amountFormat = MonetaryFormats.getAmountFormat(Locale.US);
        SimpleAmount amount = new SimpleAmount(new BigDecimal("12.34"), USD);
        assertThat(amountFormat.format(amount)).isEqualTo("USD 12.34");
        assertThat(amount.query(amountFormat)).isEqualTo("USD 12.34");
        assertThat(amountFormat.parse("EUR 7.50")).isEqualTo(new SimpleAmount(new BigDecimal("7.50"), EUR));
        assertThat(MonetaryFormats.isAvailable(Locale.GERMANY)).isTrue();
        assertThat(MonetaryFormats.getAvailableLocales()).contains(Locale.US, Locale.GERMANY);

        CurrencyConversion conversion = MonetaryConversions.getConversion(EUR);
        MonetaryAmount converted = conversion.apply(new SimpleAmount(new BigDecimal("10.00"), USD));
        assertThat(converted.getCurrency()).isEqualTo(EUR);
        assertThat(converted.getNumber().numberValue(BigDecimal.class)).isEqualByComparingTo("9.00");
        assertThat(conversion.getExchangeRate(amount).getFactor().numberValue(BigDecimal.class))
                .isEqualByComparingTo("0.90");
        ConversionQuery eurConversionQuery = ConversionQueryBuilder.of().setTermCurrency(EUR).build();
        assertThat(MonetaryConversions.isConversionAvailable(eurConversionQuery)).isTrue();
        assertThat(MonetaryConversions.getConversionProviderNames()).containsExactly("test-fx");
    }

    private static final class TestServiceProvider implements ServiceProvider {
        private final List<Object> services = List.of(
                new TestCurrencySingleton(),
                new TestCurrencyProvider(),
                new TestAmountsSingleton(),
                new TestRoundingsSingleton(),
                new TestRoundingProvider(),
                new TestFormatsSingleton(),
                new TestConversionsSingleton());

        @Override
        public int getPriority() {
            return 100;
        }

        @Override
        public <T> List<T> getServices(Class<T> serviceType) {
            return services.stream()
                    .filter(serviceType::isInstance)
                    .map(serviceType::cast)
                    .collect(Collectors.toList());
        }
    }

    private static final class TestCurrencySingleton implements MonetaryCurrenciesSingletonSpi {
        @Override
        public List<String> getDefaultProviderChain() {
            return List.of("test-currency");
        }

        @Override
        public Set<String> getProviderNames() {
            return Set.of("test-currency");
        }

        @Override
        public Set<CurrencyUnit> getCurrencies(CurrencyQuery query) {
            return filterCurrencies(query);
        }
    }

    private static final class TestCurrencyProvider implements CurrencyProviderSpi {
        @Override
        public String getProviderName() {
            return "test-currency";
        }

        @Override
        public Set<CurrencyUnit> getCurrencies(CurrencyQuery query) {
            return filterCurrencies(query);
        }
    }

    private static final class TestAmountsSingleton
            implements MonetaryAmountsSingletonSpi, MonetaryAmountsSingletonQuerySpi {
        @Override
        @SuppressWarnings("unchecked")
        public <T extends MonetaryAmount> MonetaryAmountFactory<T> getAmountFactory(Class<T> amountType) {
            if (SimpleAmount.class.equals(amountType)) {
                return (MonetaryAmountFactory<T>) new SimpleAmountFactory();
            }
            return null;
        }

        @Override
        public Class<? extends MonetaryAmount> getDefaultAmountType() {
            return SimpleAmount.class;
        }

        @Override
        public Collection<Class<? extends MonetaryAmount>> getAmountTypes() {
            return List.of(SimpleAmount.class);
        }

        @Override
        public Collection<MonetaryAmountFactory<? extends MonetaryAmount>> getAmountFactories(
                MonetaryAmountFactoryQuery query) {
            Class<?> targetType = query.getTargetType();
            if (targetType == null || targetType.isAssignableFrom(SimpleAmount.class)) {
                return List.of(new SimpleAmountFactory());
            }
            return List.of();
        }
    }

    private static Set<CurrencyUnit> filterCurrencies(CurrencyQuery query) {
        Set<String> requestedCodes = new LinkedHashSet<>(query.getCurrencyCodes());
        Set<Integer> requestedNumericCodes = new LinkedHashSet<>(query.getNumericCodes());
        Set<String> countryCodes = query.getCountries().stream()
                .map(Money_apiTest::currencyCodeForCountry)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return List.of(USD, EUR, JPY).stream()
                .filter(currency -> requestedCodes.isEmpty() || requestedCodes.contains(currency.getCurrencyCode()))
                .filter(currency -> requestedNumericCodes.isEmpty()
                        || requestedNumericCodes.contains(currency.getNumericCode()))
                .filter(currency -> countryCodes.isEmpty() || countryCodes.contains(currency.getCurrencyCode()))
                .sorted(Comparator.comparing(CurrencyUnit::getCurrencyCode))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String currencyCodeForCountry(Locale locale) {
        if (Locale.US.equals(locale)) {
            return "USD";
        }
        if (Locale.GERMANY.equals(locale)) {
            return "EUR";
        }
        if (Locale.JAPAN.equals(locale)) {
            return "JPY";
        }
        return null;
    }

    private static final class TestRoundingsSingleton implements MonetaryRoundingsSingletonSpi {
        @Override
        public Set<String> getRoundingNames(String... providers) {
            return Set.of("nearest-cent");
        }

        @Override
        public Set<String> getProviderNames() {
            return Set.of("test-rounding");
        }

        @Override
        public List<String> getDefaultProviderChain() {
            return List.of("test-rounding");
        }

        @Override
        public Collection<MonetaryRounding> getRoundings(RoundingQuery query) {
            String roundingName = query.getRoundingName();
            if (roundingName == null || "nearest-cent".equals(roundingName)) {
                return List.of(new ScaleRounding(query.getScale() == null ? 2 : query.getScale()));
            }
            return List.of();
        }

        @Override
        public MonetaryRounding getDefaultRounding() {
            return new ScaleRounding(2);
        }
    }

    private static final class TestRoundingProvider implements RoundingProviderSpi {
        @Override
        public MonetaryRounding getRounding(RoundingQuery query) {
            return new ScaleRounding(query.getScale() == null ? 2 : query.getScale());
        }

        @Override
        public Set<String> getRoundingNames() {
            return Set.of("nearest-cent");
        }

        @Override
        public String getProviderName() {
            return "test-rounding";
        }
    }

    private static final class ScaleRounding implements MonetaryRounding {
        private final int scale;
        private final RoundingContext context;

        private ScaleRounding(int scale) {
            this.scale = scale;
            this.context = RoundingContextBuilder.of("test-rounding", "nearest-cent")
                    .set("scale", scale)
                    .build();
        }

        @Override
        public RoundingContext getRoundingContext() {
            return context;
        }

        @Override
        public MonetaryAmount apply(MonetaryAmount amount) {
            return ((SimpleAmount) amount).roundToScale(scale);
        }
    }

    private static final class TestFormatsSingleton implements MonetaryFormatsSingletonSpi {
        @Override
        public Set<Locale> getAvailableLocales(String... providers) {
            return Set.of(Locale.US, Locale.GERMANY);
        }

        @Override
        public Collection<MonetaryAmountFormat> getAmountFormats(AmountFormatQuery query) {
            if (query.getLocale() == null || getAvailableLocales().contains(query.getLocale())) {
                return List.of(new PlainAmountFormat(query.getLocale() == null ? Locale.US : query.getLocale()));
            }
            return List.of();
        }

        @Override
        public Set<String> getProviderNames() {
            return Set.of("test-format");
        }

        @Override
        public List<String> getDefaultProviderChain() {
            return List.of("test-format");
        }
    }

    private static final class PlainAmountFormat implements MonetaryAmountFormat {
        private final AmountFormatContext context;

        private PlainAmountFormat(Locale locale) {
            this.context = AmountFormatContextBuilder.of(locale)
                    .setFormatName("plain")
                    .setMonetaryAmountFactory(new SimpleAmountFactory())
                    .build();
        }

        @Override
        public AmountFormatContext getContext() {
            return context;
        }

        @Override
        public void print(Appendable appendable, MonetaryAmount amount) throws IOException {
            appendable.append(amount.getCurrency().getCurrencyCode())
                    .append(' ')
                    .append(amount.getNumber().numberValue(BigDecimal.class).toPlainString());
        }

        @Override
        public MonetaryAmount parse(CharSequence text) throws MonetaryParseException {
            String[] parts = text.toString().split(" ");
            if (parts.length != 2) {
                throw new MonetaryParseException("Expected '<currency> <amount>'", text, 0);
            }
            return new SimpleAmount(new BigDecimal(parts[1]), Monetary.getCurrency(parts[0]));
        }

        @Override
        public String queryFrom(MonetaryAmount amount) {
            return format(amount);
        }
    }

    private static final class TestConversionsSingleton implements MonetaryConversionsSingletonSpi {
        @Override
        public Collection<String> getProviderNames() {
            return List.of("test-fx");
        }

        @Override
        public List<String> getDefaultProviderChain() {
            return List.of("test-fx");
        }

        @Override
        public ExchangeRateProvider getExchangeRateProvider(ConversionQuery query) {
            return new TestExchangeRateProvider(query);
        }
    }

    private static final class TestExchangeRateProvider implements ExchangeRateProvider {
        private final ConversionQuery defaultQuery;
        private final ProviderContext context = ProviderContext.of("test-fx", RateType.REALTIME);

        private TestExchangeRateProvider(ConversionQuery defaultQuery) {
            this.defaultQuery = defaultQuery;
        }

        @Override
        public ProviderContext getContext() {
            return context;
        }

        @Override
        public ExchangeRate getExchangeRate(ConversionQuery query) {
            CurrencyUnit baseCurrency = query.getBaseCurrency() == null
                    ? defaultQuery.getBaseCurrency()
                    : query.getBaseCurrency();
            CurrencyUnit termCurrency = query.getCurrency() == null ? defaultQuery.getCurrency() : query.getCurrency();
            if (baseCurrency == null) {
                baseCurrency = USD;
            }
            if (termCurrency == null) {
                termCurrency = EUR;
            }
            return new SimpleExchangeRate(baseCurrency, termCurrency, rate(baseCurrency, termCurrency));
        }

        @Override
        public CurrencyConversion getCurrencyConversion(ConversionQuery query) {
            CurrencyUnit termCurrency = query.getCurrency() == null ? defaultQuery.getCurrency() : query.getCurrency();
            return new SimpleCurrencyConversion(this, termCurrency == null ? EUR : termCurrency);
        }

        private static BigDecimal rate(CurrencyUnit baseCurrency, CurrencyUnit termCurrency) {
            if (baseCurrency.equals(termCurrency)) {
                return BigDecimal.ONE;
            }
            if (baseCurrency.equals(USD) && termCurrency.equals(EUR)) {
                return new BigDecimal("0.90");
            }
            if (baseCurrency.equals(EUR) && termCurrency.equals(USD)) {
                return new BigDecimal("1.11");
            }
            return BigDecimal.ONE;
        }
    }

    private static final class SimpleCurrencyConversion implements CurrencyConversion {
        private final TestExchangeRateProvider provider;
        private final CurrencyUnit termCurrency;
        private final ConversionContext context = ConversionContext.of("test-fx", RateType.REALTIME);

        private SimpleCurrencyConversion(TestExchangeRateProvider provider, CurrencyUnit termCurrency) {
            this.provider = provider;
            this.termCurrency = termCurrency;
        }

        @Override
        public ConversionContext getContext() {
            return context;
        }

        @Override
        public ExchangeRate getExchangeRate(MonetaryAmount amount) {
            ConversionQuery query = ConversionQueryBuilder.of()
                    .setBaseCurrency(amount.getCurrency())
                    .setTermCurrency(termCurrency)
                    .build();
            return provider.getExchangeRate(query);
        }

        @Override
        public ExchangeRateProvider getExchangeRateProvider() {
            return provider;
        }

        @Override
        public CurrencyUnit getCurrency() {
            return termCurrency;
        }

        @Override
        public MonetaryAmount apply(MonetaryAmount amount) {
            BigDecimal factor = getExchangeRate(amount).getFactor().numberValue(BigDecimal.class);
            SimpleAmount converted = (SimpleAmount) ((SimpleAmount) amount).multiply(factor);
            return converted.withCurrency(termCurrency);
        }
    }

    private static final class SimpleExchangeRate implements ExchangeRate {
        private final CurrencyUnit baseCurrency;
        private final CurrencyUnit termCurrency;
        private final BigDecimal factor;
        private final ConversionContext context = ConversionContext.of("test-fx", RateType.REALTIME);

        private SimpleExchangeRate(CurrencyUnit baseCurrency, CurrencyUnit termCurrency, BigDecimal factor) {
            this.baseCurrency = baseCurrency;
            this.termCurrency = termCurrency;
            this.factor = factor;
        }

        @Override
        public ConversionContext getContext() {
            return context;
        }

        @Override
        public CurrencyUnit getBaseCurrency() {
            return baseCurrency;
        }

        @Override
        public CurrencyUnit getCurrency() {
            return termCurrency;
        }

        @Override
        public NumberValue getFactor() {
            return new DecimalNumberValue(factor);
        }

        @Override
        public List<ExchangeRate> getExchangeRateChain() {
            return List.of();
        }
    }

    private static final class TestCurrency implements CurrencyUnit {
        private final String code;
        private final int numericCode;
        private final int fractionDigits;
        private final CurrencyContext context;

        private TestCurrency(String code, int numericCode, int fractionDigits, String providerName) {
            this.code = code;
            this.numericCode = numericCode;
            this.fractionDigits = fractionDigits;
            this.context = CurrencyContextBuilder.of(providerName)
                    .set("numericCode", numericCode)
                    .set("fractionDigits", fractionDigits)
                    .build();
        }

        @Override
        public String getCurrencyCode() {
            return code;
        }

        @Override
        public int getNumericCode() {
            return numericCode;
        }

        @Override
        public int getDefaultFractionDigits() {
            return fractionDigits;
        }

        @Override
        public CurrencyContext getContext() {
            return context;
        }

        @Override
        public int compareTo(CurrencyUnit other) {
            return code.compareTo(other.getCurrencyCode());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CurrencyUnit && code.equals(((CurrencyUnit) other).getCurrencyCode());
        }

        @Override
        public int hashCode() {
            return code.hashCode();
        }

        @Override
        public String toString() {
            return code;
        }
    }

    private static final class SimpleAmount implements MonetaryAmount {
        private final BigDecimal number;
        private final CurrencyUnit currency;
        private final MonetaryContext context;

        private SimpleAmount(BigDecimal number, CurrencyUnit currency) {
            this(number, currency, MonetaryContextBuilder.of(SimpleAmount.class).build());
        }

        private SimpleAmount(BigDecimal number, CurrencyUnit currency, MonetaryContext context) {
            this.number = number;
            this.currency = currency;
            this.context = context;
        }

        @Override
        public CurrencyUnit getCurrency() {
            return currency;
        }

        @Override
        public NumberValue getNumber() {
            return new DecimalNumberValue(number);
        }

        @Override
        public MonetaryContext getContext() {
            return context;
        }

        @Override
        public MonetaryAmountFactory<? extends MonetaryAmount> getFactory() {
            return new SimpleAmountFactory().setContext(context).setCurrency(currency).setNumber(number);
        }

        @Override
        public boolean isGreaterThan(MonetaryAmount amount) {
            return compareTo(amount) > 0;
        }

        @Override
        public boolean isGreaterThanOrEqualTo(MonetaryAmount amount) {
            return compareTo(amount) >= 0;
        }

        @Override
        public boolean isLessThan(MonetaryAmount amount) {
            return compareTo(amount) < 0;
        }

        @Override
        public boolean isLessThanOrEqualTo(MonetaryAmount amount) {
            return compareTo(amount) <= 0;
        }

        @Override
        public boolean isEqualTo(MonetaryAmount amount) {
            return compareTo(amount) == 0;
        }

        @Override
        public int signum() {
            return number.signum();
        }

        @Override
        public MonetaryAmount add(MonetaryAmount amount) {
            requireSameCurrency(amount);
            return new SimpleAmount(number.add(amount.getNumber().numberValue(BigDecimal.class)), currency, context);
        }

        @Override
        public MonetaryAmount subtract(MonetaryAmount amount) {
            requireSameCurrency(amount);
            BigDecimal subtrahend = amount.getNumber().numberValue(BigDecimal.class);
            return new SimpleAmount(number.subtract(subtrahend), currency, context);
        }

        @Override
        public MonetaryAmount multiply(long multiplicand) {
            return multiply(BigDecimal.valueOf(multiplicand));
        }

        @Override
        public MonetaryAmount multiply(double multiplicand) {
            return multiply(BigDecimal.valueOf(multiplicand));
        }

        @Override
        public MonetaryAmount multiply(Number multiplicand) {
            return multiply(toBigDecimal(multiplicand));
        }

        @Override
        public MonetaryAmount divide(long divisor) {
            return divide(BigDecimal.valueOf(divisor));
        }

        @Override
        public MonetaryAmount divide(double divisor) {
            return divide(BigDecimal.valueOf(divisor));
        }

        @Override
        public MonetaryAmount divide(Number divisor) {
            return divide(toBigDecimal(divisor));
        }

        @Override
        public MonetaryAmount remainder(long divisor) {
            return remainder(BigDecimal.valueOf(divisor));
        }

        @Override
        public MonetaryAmount remainder(double divisor) {
            return remainder(BigDecimal.valueOf(divisor));
        }

        @Override
        public MonetaryAmount remainder(Number divisor) {
            return new SimpleAmount(number.remainder(toBigDecimal(divisor)), currency, context);
        }

        @Override
        public MonetaryAmount[] divideAndRemainder(long divisor) {
            return divideAndRemainder(BigDecimal.valueOf(divisor));
        }

        @Override
        public MonetaryAmount[] divideAndRemainder(double divisor) {
            return divideAndRemainder(BigDecimal.valueOf(divisor));
        }

        @Override
        public MonetaryAmount[] divideAndRemainder(Number divisor) {
            BigDecimal[] values = number.divideAndRemainder(toBigDecimal(divisor));
            return new MonetaryAmount[] {
                    new SimpleAmount(values[0], currency, context),
                    new SimpleAmount(values[1], currency, context)
            };
        }

        @Override
        public MonetaryAmount divideToIntegralValue(long divisor) {
            return divideToIntegralValue(BigDecimal.valueOf(divisor));
        }

        @Override
        public MonetaryAmount divideToIntegralValue(double divisor) {
            return divideToIntegralValue(BigDecimal.valueOf(divisor));
        }

        @Override
        public MonetaryAmount divideToIntegralValue(Number divisor) {
            return new SimpleAmount(number.divideToIntegralValue(toBigDecimal(divisor)), currency, context);
        }

        @Override
        public MonetaryAmount scaleByPowerOfTen(int power) {
            return new SimpleAmount(number.scaleByPowerOfTen(power), currency, context);
        }

        @Override
        public MonetaryAmount abs() {
            return new SimpleAmount(number.abs(), currency, context);
        }

        @Override
        public MonetaryAmount negate() {
            return new SimpleAmount(number.negate(), currency, context);
        }

        @Override
        public MonetaryAmount plus() {
            return this;
        }

        @Override
        public MonetaryAmount stripTrailingZeros() {
            return new SimpleAmount(number.stripTrailingZeros(), currency, context);
        }

        @Override
        public int compareTo(MonetaryAmount amount) {
            requireSameCurrency(amount);
            return number.compareTo(amount.getNumber().numberValue(BigDecimal.class));
        }

        private SimpleAmount roundToScale(int scale) {
            return new SimpleAmount(number.setScale(scale, RoundingMode.HALF_UP), currency, context);
        }

        private SimpleAmount withCurrency(CurrencyUnit newCurrency) {
            return new SimpleAmount(number, newCurrency, context);
        }

        private MonetaryAmount multiply(BigDecimal multiplicand) {
            return new SimpleAmount(number.multiply(multiplicand), currency, context);
        }

        private MonetaryAmount divide(BigDecimal divisor) {
            return new SimpleAmount(number.divide(divisor, MathContext.DECIMAL64), currency, context);
        }

        private void requireSameCurrency(MonetaryAmount amount) {
            if (!currency.equals(amount.getCurrency())) {
                throw new MonetaryException("Currency mismatch: " + currency + " != " + amount.getCurrency());
            }
        }

        private static BigDecimal toBigDecimal(Number number) {
            if (number instanceof BigDecimal) {
                return (BigDecimal) number;
            }
            if (number instanceof BigInteger) {
                return new BigDecimal((BigInteger) number);
            }
            if (number instanceof NumberValue) {
                return ((NumberValue) number).numberValue(BigDecimal.class);
            }
            return new BigDecimal(number.toString());
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof MonetaryAmount)) {
                return false;
            }
            MonetaryAmount amount = (MonetaryAmount) other;
            return currency.equals(amount.getCurrency())
                    && number.compareTo(amount.getNumber().numberValue(BigDecimal.class)) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(currency, number.stripTrailingZeros());
        }

        @Override
        public String toString() {
            return currency + " " + number.toPlainString();
        }
    }

    private static final class SimpleAmountFactory implements MonetaryAmountFactory<SimpleAmount> {
        private CurrencyUnit currency = USD;
        private BigDecimal number = BigDecimal.ZERO;
        private MonetaryContext context = MonetaryContextBuilder.of(SimpleAmount.class).build();

        @Override
        public Class<? extends MonetaryAmount> getAmountType() {
            return SimpleAmount.class;
        }

        @Override
        public MonetaryAmountFactory<SimpleAmount> setCurrency(CurrencyUnit currency) {
            this.currency = currency;
            return this;
        }

        @Override
        public MonetaryAmountFactory<SimpleAmount> setNumber(double number) {
            this.number = BigDecimal.valueOf(number);
            return this;
        }

        @Override
        public MonetaryAmountFactory<SimpleAmount> setNumber(long number) {
            this.number = BigDecimal.valueOf(number);
            return this;
        }

        @Override
        public MonetaryAmountFactory<SimpleAmount> setNumber(Number number) {
            this.number = SimpleAmount.toBigDecimal(number);
            return this;
        }

        @Override
        public NumberValue getMaxNumber() {
            return new DecimalNumberValue(new BigDecimal("999999999.99"));
        }

        @Override
        public NumberValue getMinNumber() {
            return new DecimalNumberValue(new BigDecimal("-999999999.99"));
        }

        @Override
        public MonetaryAmountFactory<SimpleAmount> setContext(MonetaryContext context) {
            this.context = context;
            return this;
        }

        @Override
        public SimpleAmount create() {
            return new SimpleAmount(number, currency, context);
        }

        @Override
        public MonetaryContext getDefaultMonetaryContext() {
            return context;
        }
    }

    private static final class DecimalNumberValue extends NumberValue {
        private final BigDecimal value;

        private DecimalNumberValue(BigDecimal value) {
            this.value = value;
        }

        @Override
        public Class<?> getNumberType() {
            return BigDecimal.class;
        }

        @Override
        public int getPrecision() {
            return value.precision();
        }

        @Override
        public int getScale() {
            return value.scale();
        }

        @Override
        public int intValueExact() {
            return value.intValueExact();
        }

        @Override
        public long longValueExact() {
            return value.longValueExact();
        }

        @Override
        public double doubleValueExact() {
            return value.doubleValue();
        }

        @Override
        public <T extends Number> T numberValue(Class<T> numberType) {
            return convert(numberType, false);
        }

        @Override
        public NumberValue round(MathContext mathContext) {
            return new DecimalNumberValue(value.round(mathContext));
        }

        @Override
        public <T extends Number> T numberValueExact(Class<T> numberType) {
            return convert(numberType, true);
        }

        @Override
        public long getAmountFractionNumerator() {
            return value.remainder(BigDecimal.ONE).movePointRight(Math.max(value.scale(), 0)).abs().longValue();
        }

        @Override
        public long getAmountFractionDenominator() {
            if (value.scale() <= 0) {
                return 1L;
            }
            return BigDecimal.TEN.pow(value.scale()).longValue();
        }

        @Override
        public int intValue() {
            return value.intValue();
        }

        @Override
        public long longValue() {
            return value.longValue();
        }

        @Override
        public float floatValue() {
            return value.floatValue();
        }

        @Override
        public double doubleValue() {
            return value.doubleValue();
        }

        @Override
        public int compareTo(NumberValue other) {
            return value.compareTo(other.numberValue(BigDecimal.class));
        }

        @Override
        public String toString() {
            return value.toPlainString();
        }

        private <T extends Number> T convert(Class<T> numberType, boolean exact) {
            Number converted;
            if (BigDecimal.class.equals(numberType)) {
                converted = value;
            } else if (BigInteger.class.equals(numberType)) {
                converted = exact ? value.toBigIntegerExact() : value.toBigInteger();
            } else if (Long.class.equals(numberType)) {
                converted = exact ? value.longValueExact() : value.longValue();
            } else if (Integer.class.equals(numberType)) {
                converted = exact ? value.intValueExact() : value.intValue();
            } else if (Double.class.equals(numberType)) {
                converted = value.doubleValue();
            } else if (Float.class.equals(numberType)) {
                converted = value.floatValue();
            } else {
                throw new ArithmeticException("Unsupported number type: " + numberType.getName());
            }
            return numberType.cast(converted);
        }
    }
}
