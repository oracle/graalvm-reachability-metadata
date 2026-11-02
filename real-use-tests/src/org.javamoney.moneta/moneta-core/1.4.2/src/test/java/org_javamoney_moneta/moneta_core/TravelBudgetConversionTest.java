/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javamoney_moneta.moneta_core;

import org.javamoney.moneta.Money;
import org.javamoney.moneta.convert.ExchangeRateBuilder;
import org.javamoney.moneta.spi.AbstractRateProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.NumberValue;
import javax.money.convert.ConversionContext;
import javax.money.convert.ConversionQuery;
import javax.money.convert.ExchangeRate;
import javax.money.convert.ProviderContext;
import javax.money.convert.RateType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Moneta's currency-conversion subsystem for an itinerary budget with application rates.
 */
public class TravelBudgetConversionTest {
    @Test
    @Timeout(60)
    public void convertsItineraryBudgetUsingPublishedRate() {
        CurrencyUnit usd = Monetary.getCurrency("USD");
        CurrencyUnit eur = Monetary.getCurrency("EUR");
        TravelBudget budget = new TravelBudget(new PublishedRates(usd, eur));

        MonetaryAmount converted = budget.inEuros(Money.of(new BigDecimal("125.00"), usd));
        ExchangeRate rate = budget.rateFrom(usd, eur);

        assertThat(converted).isEqualTo(Money.of(new BigDecimal("115.00"), eur));
        assertThat(converted.getCurrency()).isEqualTo(eur);
        assertThat(rate.getBaseCurrency()).isEqualTo(usd);
        assertThat(rate.getCurrency()).isEqualTo(eur);
        assertThat(rate.getFactor().numberValue(BigDecimal.class))
                .isEqualByComparingTo("0.92");
        assertThat(rate.getContext().getProviderName()).isEqualTo("travel-budget");
        assertThat(rate.getContext().getRateType()).isEqualTo(RateType.REALTIME);
    }

    private static final class TravelBudget {
        private final PublishedRates rates;
        private final CurrencyUnit destinationCurrency;

        private TravelBudget(PublishedRates rates) {
            this.rates = rates;
            this.destinationCurrency = rates.destinationCurrency();
        }

        private MonetaryAmount inEuros(MonetaryAmount homeAmount) {
            return rates.getCurrencyConversion(destinationCurrency).apply(homeAmount);
        }

        private ExchangeRate rateFrom(CurrencyUnit homeCurrency, CurrencyUnit destination) {
            return rates.getExchangeRate(homeCurrency, destination);
        }
    }

    private static final class PublishedRates extends AbstractRateProvider {
        private final CurrencyUnit homeCurrency;
        private final CurrencyUnit destinationCurrency;

        private PublishedRates(CurrencyUnit homeCurrency, CurrencyUnit destinationCurrency) {
            super(ProviderContext.of("travel-budget", RateType.REALTIME));
            this.homeCurrency = homeCurrency;
            this.destinationCurrency = destinationCurrency;
        }

        private CurrencyUnit destinationCurrency() {
            return destinationCurrency;
        }

        @Override
        public ExchangeRate getExchangeRate(ConversionQuery query) {
            CurrencyUnit base = query.getBaseCurrency();
            CurrencyUnit term = query.getCurrency();
            if (!homeCurrency.equals(base) || !destinationCurrency.equals(term)) {
                throw new IllegalArgumentException("No published itinerary rate");
            }
            ConversionContext context = ConversionContext.of("travel-budget", RateType.REALTIME);
            NumberValue factor = Money.of(new BigDecimal("0.92"), term).getNumber();
            return new ExchangeRateBuilder(context)
                    .setBase(base)
                    .setTerm(term)
                    .setFactor(factor)
                    .build();
        }
    }
}
