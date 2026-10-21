/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_retrofit2.retrofit;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.JsonEncodingException;
import com.squareup.moshi.JsonQualifier;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.ToJson;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * Moshi converter strictness modes and JSON qualifiers: `MoshiConverterFactory` forwarding
 * `@JsonQualifier` annotations from service methods and `@Body` parameters to Moshi, and its
 * `failOnUnknown()` and `asLenient()` modes, BOM skipping, and rejection of trailing content.
 *
 * A payment-gateway client exchanges money amounts as decimal strings that a qualified adapter
 * maps to whole cents, rejects charge payloads carrying fields it does not know when built
 * strict, and tolerates a legacy endpoint's malformed JSON when built lenient, while still
 * refusing a second document after the first, since leniency covers only the decoded value.
 */
public class PaymentGatewayMoshiStrictnessTest {

    private static final Moshi MOSHI = new Moshi.Builder().add(new CentsAdapter()).build();

    private MockWebServer server;

    @BeforeEach
    void startGateway() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopGateway() throws IOException {
        server.shutdown();
    }

    @Test
    void methodQualifierSelectsTheCentsAdapterForAResponseBody() throws Exception {
        GatewayApi api = gateway(MoshiConverterFactory.create(MOSHI));
        server.enqueue(json("\"125.40\""));

        Response<Long> response = api.balance().execute();

        assertTrue(response.isSuccessful());
        assertEquals(Long.valueOf(12540L), response.body());
        assertEquals("/v1/balance", server.takeRequest(5, TimeUnit.SECONDS).getPath());
    }

    @Test
    void qualifiersOnBodyParametersAndFieldsWriteDecimalAmounts() throws Exception {
        GatewayApi api = gateway(MoshiConverterFactory.create(MOSHI));
        server.enqueue(json("""
                {"id":"ch_7","currency":"EUR","amount":"19.99"}"""));
        server.enqueue(new MockResponse().setResponseCode(204));

        ChargeRequest request = new ChargeRequest();
        request.currency = "EUR";
        request.amount = 1999L;
        Charge charge = api.charge(request).execute().body();
        Response<Void> refund = api.refund(505L).execute();

        assertEquals("ch_7", charge.id);
        assertEquals("EUR", charge.currency);
        assertEquals(Long.valueOf(1999L), charge.amount);
        assertEquals(204, refund.code());
        assertNull(refund.body());

        RecordedRequest chargeRequest = server.takeRequest(5, TimeUnit.SECONDS);
        assertEquals("/v1/charges", chargeRequest.getPath());
        assertEquals("application/json; charset=UTF-8", chargeRequest.getHeader("Content-Type"));
        String chargeJson = chargeRequest.getBody().readUtf8();
        assertTrue(chargeJson.contains("\"amount\":\"19.99\""), chargeJson);
        assertTrue(chargeJson.contains("\"currency\":\"EUR\""), chargeJson);
        RecordedRequest refundRequest = server.takeRequest(5, TimeUnit.SECONDS);
        assertEquals("/v1/refunds", refundRequest.getPath());
        assertEquals("\"5.05\"", refundRequest.getBody().readUtf8());
    }

    @Test
    void failOnUnknownRejectsFieldsTheDefaultFactorySkips() throws Exception {
        String payload = """
                {"id":"ch_9","currency":"USD","amount":"3.50","riskScore":42}""";
        GatewayApi tolerant = gateway(MoshiConverterFactory.create(MOSHI));
        GatewayApi strict = gateway(MoshiConverterFactory.create(MOSHI).failOnUnknown());
        server.enqueue(json(payload));
        server.enqueue(json(payload));

        Charge charge = tolerant.charge(new ChargeRequest()).execute().body();
        JsonDataException error = assertThrows(
                JsonDataException.class, () -> strict.charge(new ChargeRequest()).execute());

        assertEquals("ch_9", charge.id);
        assertEquals(Long.valueOf(350L), charge.amount);
        assertTrue(error.getMessage().contains("riskScore"), error.getMessage());
    }

    @Test
    void lenientFactoryAcceptsMalformedJsonThatTheStrictFactoryRejects() throws Exception {
        String legacy = "{id:'ch_3', currency:GBP, amount:'0.75'}";
        GatewayApi strict = gateway(MoshiConverterFactory.create(MOSHI));
        GatewayApi lenient = gateway(MoshiConverterFactory.create(MOSHI).asLenient());
        server.enqueue(json(legacy));
        server.enqueue(json(legacy));

        assertThrows(JsonEncodingException.class, () -> strict.legacyCharge().execute());
        Charge charge = lenient.legacyCharge().execute().body();

        assertEquals("ch_3", charge.id);
        assertEquals("GBP", charge.currency);
        assertEquals(Long.valueOf(75L), charge.amount);
    }

    @Test
    void skipsAByteOrderMarkButRejectsTrailingDocuments() throws Exception {
        GatewayApi lenient = gateway(MoshiConverterFactory.create(MOSHI).asLenient());
        Buffer withBom = new Buffer().write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        withBom.writeUtf8("\"8.00\"");
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody(withBom));
        server.enqueue(json("\"1.00\" \"2.00\""));

        Long balance = lenient.balance().execute().body();
        JsonEncodingException error = assertThrows(
                JsonEncodingException.class, () -> lenient.balance().execute());

        assertEquals(Long.valueOf(800L), balance);
        assertTrue(error.getMessage().contains("malformed JSON"), error.getMessage());
    }

    private GatewayApi gateway(MoshiConverterFactory factory) {
        return new Retrofit.Builder()
                .baseUrl(server.url("/v1/"))
                .addConverterFactory(factory)
                .build()
                .create(GatewayApi.class);
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    /** Marks a `Long` of whole cents that travels as a decimal string such as `"19.99"`. */
    @Retention(RUNTIME)
    @JsonQualifier
    @interface Cents {
    }

    /** Maps qualified cent amounts to and from their two-decimal string form. */
    static final class CentsAdapter {

        @ToJson
        String toJson(@Cents Long cents) {
            return BigDecimal.valueOf(cents, 2).toPlainString();
        }

        @FromJson
        @Cents
        Long fromJson(String decimal) {
            return new BigDecimal(decimal).movePointRight(2).longValueExact();
        }
    }

    /** The charge the client asks the gateway to create. */
    static final class ChargeRequest {
        String currency;
        @Cents
        Long amount;
    }

    /** A charge as the gateway reports it. */
    static final class Charge {
        String id;
        String currency;
        @Cents
        Long amount;
    }

    /** The gateway endpoints the client calls. */
    interface GatewayApi {

        @GET("balance")
        @Cents
        Call<Long> balance();

        @POST("charges")
        Call<Charge> charge(@Body ChargeRequest request);

        @POST("refunds")
        Call<Void> refund(@Body @Cents Long amount);

        @GET("legacy/charge")
        Call<Charge> legacyCharge();
    }
}
