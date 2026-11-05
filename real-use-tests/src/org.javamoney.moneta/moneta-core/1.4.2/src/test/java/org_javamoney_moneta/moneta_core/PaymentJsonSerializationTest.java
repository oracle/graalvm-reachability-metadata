/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javamoney_moneta.moneta_core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.javamoney.moneta.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Moneta's JSON serialization subsystem for a payment API response.
 */
public class PaymentJsonSerializationTest {
    @Test
    @Timeout(60)
    public void serializesPaymentAmountForApiResponse() throws Exception {
        CurrencyUnit usd = Monetary.getCurrency("USD");
        Money payment = Money.of(new BigDecimal("49.95"), usd);
        ObjectMapper mapper = new ObjectMapper();

        String responseJson = mapper.writeValueAsString(payment);
        JsonNode response = mapper.readTree(responseJson);

        assertThat(response.get("currency").get("currencyCode").asText()).isEqualTo("USD");
        assertThat(response.get("number").decimalValue()).isEqualByComparingTo("49.95");
        assertThat(response.get("positive").asBoolean()).isTrue();
        assertThat(response.get("negative").asBoolean()).isFalse();
    }
}
