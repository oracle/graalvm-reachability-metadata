/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_jackson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jackson's arbitrary-precision numeric claims codec for financial settlement reconciliation.
public class PreciseSettlementClaimsTest {
    @Test
    @Timeout(60)
    public void reconcilesDecimalTransfersWithoutRoundingOrTruncatingLedgerIdentifiers() {
        ObjectMapper mapper = new ObjectMapper()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
        JacksonSerializer<Map<String, ?>> serializer = new JacksonSerializer<>(mapper);
        JacksonDeserializer<Map<String, ?>> deserializer = new JacksonDeserializer<>(mapper);
        BigInteger ledgerSequence = new BigInteger("18446744073709551617");
        BigDecimal expectedNet = new BigDecimal("123456789012345.66");
        List<Map<String, Object>> transfers = List.of(
                Map.of("reference", "clearing-704", "amount", new BigDecimal("123456789012345.67")),
                Map.of("reference", "interest-704", "amount", new BigDecimal("0.09")),
                Map.of("reference", "fee-704", "amount", new BigDecimal("-0.10")));
        Claims settlement = Jwts.claims()
                .issuer("clearing-service")
                .subject("treasury-account-42")
                .add("ledger_sequence", ledgerSequence)
                .add("currency", "USD")
                .add("transfers", transfers)
                .add("net_amount", expectedNet)
                .build();

        byte[] exported = serializer.serialize(settlement);
        String json = new String(exported, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"ledger_sequence\":18446744073709551617"));
        assertTrue(json.contains("\"net_amount\":123456789012345.66"));

        Claims received = Jwts.claims().add(deserializer.deserialize(exported)).build();
        BigInteger receivedSequence = received.get("ledger_sequence", BigInteger.class);
        BigDecimal declaredNet = received.get("net_amount", BigDecimal.class);
        List<?> receivedTransfers = received.get("transfers", List.class);
        BigDecimal reconciledNet = new BigDecimal("0.00");
        for (Object transfer : receivedTransfers) {
            Map<?, ?> entry = assertInstanceOf(Map.class, transfer);
            BigDecimal amount = assertInstanceOf(BigDecimal.class, entry.get("amount"));
            reconciledNet = reconciledNet.add(amount);
        }

        assertEquals("clearing-service", received.getIssuer());
        assertEquals("treasury-account-42", received.getSubject());
        assertEquals("USD", received.get("currency", String.class));
        assertEquals(ledgerSequence, receivedSequence);
        assertEquals(new BigInteger("18446744073709551618"), receivedSequence.add(BigInteger.ONE));
        assertEquals(transfers, receivedTransfers);
        assertEquals(expectedNet, declaredNet);
        assertEquals(declaredNet, reconciledNet);
        assertEquals("123456789012345.66", reconciledNet.toPlainString());
    }
}
