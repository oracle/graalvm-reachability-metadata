/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc_provider_common;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static oracle.jdbc.provider.parameter.Parameter.CommonAttribute.REQUIRED;
import static oracle.jdbc.provider.parameter.Parameter.CommonAttribute.SENSITIVE;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.net.ssl.SSLContext;
import oracle.jdbc.AccessToken;
import oracle.jdbc.provider.cache.CacheController;
import oracle.jdbc.provider.cache.CachedResourceFactory;
import oracle.jdbc.provider.factory.Resource;
import oracle.jdbc.provider.factory.ResourceFactory;
import oracle.jdbc.provider.oauth.AccessTokenCacheFactory;
import oracle.jdbc.provider.parameter.Parameter;
import oracle.jdbc.provider.parameter.ParameterSet;
import oracle.jdbc.provider.parameter.ParameterSetParser;
import oracle.jdbc.provider.parameter.UriParameters;
import oracle.jdbc.provider.resource.ResourceParameter;
import oracle.jdbc.provider.util.FileUtils;
import oracle.jdbc.provider.util.JsonWebTokenParser;
import oracle.jdbc.provider.util.ParameterUtils;
import oracle.jdbc.provider.util.PemData;
import oracle.jdbc.provider.util.TNSNames;
import oracle.jdbc.provider.util.TlsUtils;
import oracle.jdbc.provider.util.Wallet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Ojdbc_provider_commonTest {
    private static final char[] STORE_PASSWORD = "wallet-password".toCharArray();

    @Test
    void parsesTypedParametersAndProtectsSensitiveValues() {
        Parameter<String> password = Parameter.create(SENSITIVE, REQUIRED);
        Parameter<Integer> retries = Parameter.create();
        Parameter<String> region = Parameter.create();
        ParameterSetParser parser =
                ParameterSetParser.builder()
                        .addParameter("password", password)
                        .addParameter("retries", retries, 3, Integer::parseInt)
                        .addParameter("region", region, "us-phoenix-1")
                        .build();

        ParameterSet parameters =
                parser.parseUrl(
                        "ojdbc-resource:configuration?PASSWORD=s3cr3t&retries=7");

        assertThat(parameters.getRequired(password)).isEqualTo("s3cr3t");
        assertThat(parameters.getRequired(retries)).isEqualTo(7);
        assertThat(parameters.getRequired(region)).isEqualTo("us-phoenix-1");
        assertThat(parameters.getName(password)).isEqualTo("password");
        assertThat(parameters.toString()).doesNotContain("s3cr3t").contains("retries=7");
        assertThat(parameters.filterParameters(new String[] {"password", "region"}))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("password", "s3cr3t", "region", "us-phoenix-1"));

        ParameterSet copy =
                parameters.copyBuilder().add("region", region, "eu-frankfurt-1").build();
        assertThat(copy.getRequired(region)).isEqualTo("eu-frankfurt-1");
        assertThat(parameters.getRequired(region)).isEqualTo("us-phoenix-1");
    }

    @Test
    void resolvesParametersBeforeSystemPropertyFallbacks() {
        String propertyName = "ojdbc.provider.test.service";
        String previousValue = System.getProperty(propertyName);
        Parameter<String> service = Parameter.create();
        ParameterSet configured =
                ParameterSet.builder().add("service", service, "orders").build();

        try {
            System.setProperty(propertyName, "inventory");

            assertThat(
                            ParameterUtils.getParameterWithFallback(
                                    service, propertyName, "UNUSED_SERVICE_ENV", configured))
                    .isEqualTo("orders");
            assertThat(
                            ParameterUtils.getParameterWithFallback(
                                    service,
                                    propertyName,
                                    "UNUSED_SERVICE_ENV",
                                    ParameterSet.empty()))
                    .isEqualTo("inventory");
        } finally {
            if (previousValue == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previousValue);
            }
        }
    }

    @Test
    void serializesAndParsesUriQueryParameters() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("scope", "read-write");
        input.put("redirect", "localhost-callback");

        String query = UriParameters.toString(input);
        Map<String, String> parsed = UriParameters.parse("ojdbc-resource:item?" + query);
        Map<String, String> decoded =
                UriParameters.parse(
                        "ojdbc-resource:item?scope=read%2Fwrite%20%2B%20audit");

        assertThat(parsed).containsExactlyInAnyOrderEntriesOf(input);
        assertThat(decoded).containsEntry("scope", "read/write + audit");
    }

    @Test
    void cachesValidResourcesAndRefreshesAfterGlobalClear() {
        AtomicInteger requests = new AtomicInteger();
        ResourceFactory<String> delegate =
                parameters ->
                        Resource.createPermanentResource(
                                parameters.getRequired(ParameterHolder.NAME)
                                        + "-"
                                        + requests.incrementAndGet(),
                                true);
        ResourceFactory<String> cached = CachedResourceFactory.create(delegate);
        ParameterSet parameters =
                ParameterSet.builder().add("name", ParameterHolder.NAME, "orders").build();

        Resource<String> first = cached.request(parameters);
        Resource<String> second = cached.request(parameters.copyBuilder().build());
        CacheController.clearAllCaches();
        Resource<String> refreshed = cached.request(parameters);

        assertThat(second).isSameAs(first);
        assertThat(first.getContent()).isEqualTo("orders-1");
        assertThat(first.isSensitive()).isTrue();
        assertThat(refreshed.getContent()).isEqualTo("orders-2");
        assertThat(requests).hasValue(2);
    }

    @Test
    void replacesExpiredCachedResources() {
        AtomicInteger requests = new AtomicInteger();
        ResourceFactory<Integer> cached =
                CachedResourceFactory.create(
                        parameters ->
                                Resource.createExpiringResource(
                                        requests.incrementAndGet(),
                                        OffsetDateTime.now().minusMinutes(1),
                                        false));

        Resource<Integer> first = cached.request(ParameterSet.empty());
        Resource<Integer> second = cached.request(ParameterSet.empty());

        assertThat(first.isValid()).isFalse();
        assertThat(second.getContent()).isEqualTo(2);
        assertThat(requests).hasValue(2);
    }

    @Test
    void cachesJdbcAccessTokensByParameterSet() {
        AtomicInteger requests = new AtomicInteger();
        ResourceFactory<AccessToken> tokenFactory =
                parameters -> {
                    requests.incrementAndGet();
                    String jwt =
                            createJwt(
                                    "{\"sub\":\"database-user\",\"exp\":"
                                            + OffsetDateTime.now(ZoneOffset.UTC)
                                                    .plusHours(1)
                                                    .toEpochSecond()
                                            + "}");
                    return Resource.createPermanentResource(
                            AccessToken.createJsonWebToken(jwt.toCharArray()), true);
                };
        ParameterSet parameters =
                ParameterSet.builder()
                        .add("factory", AccessTokenCacheFactory.FACTORY, tokenFactory)
                        .build();

        Resource<Supplier<? extends AccessToken>> cache =
                AccessTokenCacheFactory.getInstance().request(parameters);
        AccessToken first = cache.getContent().get();
        AccessToken second = cache.getContent().get();

        assertThat(cache.isSensitive()).isFalse();
        assertThat(first).isSameAs(second);
        assertThat(requests).hasValue(1);
    }

    @Test
    void parsesJwtClaimsFromTextAndFile(@TempDir Path temporaryDirectory) throws Exception {
        long expiration = 2_000_000_000L;
        String jwt =
                createJwt(
                        "{\"sub\":\"service-account\",\"admin\":true,\"exp\":"
                                + expiration
                                + "}");
        Path jwtFile = temporaryDirectory.resolve("token.jwt");
        Files.writeString(jwtFile, jwt, UTF_8);

        Map<String, String> claims = JsonWebTokenParser.parseClaims(jwt);

        assertThat(claims)
                .containsEntry("sub", "service-account")
                .containsEntry("admin", "true")
                .containsEntry("exp", Long.toString(expiration));
        assertThat(JsonWebTokenParser.parseClaims(jwtFile)).isEqualTo(claims);
        assertThat(JsonWebTokenParser.parseExp(jwt))
                .isEqualTo(
                        OffsetDateTime.ofInstant(
                                Instant.ofEpochSecond(expiration), ZoneOffset.UTC));
    }

    @Test
    void encodesAndDecodesPrivateKeyPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair keyPair = generator.generateKeyPair();

        PemData encoded = PemData.encodePrivateKey(keyPair.getPrivate());
        List<PemData> decoded;
        try (InputStream input = encoded.createInputStream()) {
            decoded = PemData.decode(input);
        }

        assertThat(decoded).hasSize(1);
        assertThat(decoded.get(0).label()).isEqualTo(PemData.Label.PRIVATE_KEY);
        assertThat(decoded.get(0).data()).isEqualTo(keyPair.getPrivate().getEncoded());
    }

    @Test
    void loadsKeyStoreAndCreatesTlsContext() throws Exception {
        byte[] keyStoreBytes = createJks(STORE_PASSWORD);
        KeyStore keyStore =
                TlsUtils.loadKeyStore(
                        new ByteArrayInputStream(keyStoreBytes), STORE_PASSWORD, "JKS", null);
        SSLContext context = TlsUtils.createSSLContext(keyStore, keyStore, STORE_PASSWORD);

        assertThat(keyStore.size()).isZero();
        assertThat(context.getProtocol()).isEqualTo("TLSv1.2");
    }

    @Test
    void unzipsWalletAndExposesConnectionProfiles() throws Exception {
        byte[] walletZip = createWalletZip();
        Wallet wallet;
        try (ZipInputStream zipInput =
                new ZipInputStream(new ByteArrayInputStream(walletZip), UTF_8)) {
            wallet = Wallet.unzip(zipInput, STORE_PASSWORD);
        }

        assertThat(wallet.getHighConnectionString()).contains("SERVICE_NAME=db_high");
        assertThat(wallet.getMediumConnectionString()).contains("SERVICE_NAME=db_medium");
        assertThat(wallet.getLowConnectionString()).contains("SERVICE_NAME=db_low");
        assertThat(wallet.getTransactionProcessingConnectionString())
                .contains("SERVICE_NAME=db_tp");
        assertThat(wallet.getTransactionProcessingUrgentConnectionString())
                .contains("SERVICE_NAME=db_tpurgent");
        assertThat(wallet.getSSLContext().getProtocol()).isEqualTo("TLSv1.2");
        assertThat(wallet.getExpirationDate())
                .isEqualTo(OffsetDateTime.parse("2034-06-17T12:30:45Z"));
    }

    @Test
    void parsesTnsAliasesAndWalletExpiration() throws Exception {
        String tnsNames =
                """
                orders_high =
                  (DESCRIPTION=(CONNECT_DATA=(SERVICE_NAME=orders_high)))
                orders_low = (DESCRIPTION=(CONNECT_DATA=(SERVICE_NAME=orders_low)))
                """;
        TNSNames parsed = TNSNames.read(new ByteArrayInputStream(tnsNames.getBytes(UTF_8)));
        String readme =
                "The SSL certificates provided in this wallet will expire on "
                        + "2031-02-03 04:05:06.123 UTC.";

        assertThat(parsed.getConnectionString(TNSNames.ConsumerGroup.HIGH))
                .contains("SERVICE_NAME=orders_high");
        assertThat(parsed.getConnectionStringByAlias("orders_low"))
                .contains("SERVICE_NAME=orders_low");
        assertThat(
                        Wallet.parseExpirationDateFromReadme(
                                new ByteArrayInputStream(readme.getBytes(UTF_8))))
                .isEqualTo(OffsetDateTime.parse("2031-02-03T04:05:06.123Z"));
    }

    @Test
    void handlesSecretEncodingAndResourceParameterAttributes() {
        byte[] secret = "not-base64!".getBytes(UTF_8);
        char[] encoded = FileUtils.toBase64EncodedCharArray("not-base64!");
        ResourceParameter parameter =
                new ResourceParameter(
                        "password", Parameter.<String>create(SENSITIVE, REQUIRED), "change-me");

        assertThat(FileUtils.isBase64Encoded(secret)).isFalse();
        assertThat(FileUtils.decodeIfBase64(new String(encoded).getBytes(US_ASCII)))
                .isEqualTo(secret);
        assertThat(parameter.name()).isEqualTo("password");
        assertThat(parameter.isSensitive()).isTrue();
        assertThat(parameter.isRequired()).isTrue();
        assertThat(parameter.defaultValue()).hasToString("change-me");
    }

    private static String createJwt(String payload) {
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString("{\"alg\":\"none\"}".getBytes(UTF_8))
                + "."
                + encoder.encodeToString(payload.getBytes(UTF_8))
                + "."
                + encoder.encodeToString("{}".getBytes(UTF_8));
    }

    private static byte[] createJks(char[] password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        keyStore.store(output, password);
        return output.toByteArray();
    }

    private static byte[] createWalletZip() throws Exception {
        byte[] keyStore = createJks(STORE_PASSWORD);
        byte[] trustStore = createJks(STORE_PASSWORD);
        String tnsNames =
                """
                db_high = (DESCRIPTION=(CONNECT_DATA=(SERVICE_NAME=db_high)))
                db_medium = (DESCRIPTION=(CONNECT_DATA=(SERVICE_NAME=db_medium)))
                db_low = (DESCRIPTION=(CONNECT_DATA=(SERVICE_NAME=db_low)))
                db_tp = (DESCRIPTION=(CONNECT_DATA=(SERVICE_NAME=db_tp)))
                db_tpurgent = (DESCRIPTION=(CONNECT_DATA=(SERVICE_NAME=db_tpurgent)))
                """;
        String readme =
                "The SSL certificates provided in this wallet will expire on "
                        + "2034-06-17 12:30:45 UTC.";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutput = new ZipOutputStream(output, UTF_8)) {
            addZipEntry(zipOutput, "tnsnames.ora", tnsNames.getBytes(UTF_8));
            addZipEntry(zipOutput, "keystore.jks", keyStore);
            addZipEntry(zipOutput, "truststore.jks", trustStore);
            addZipEntry(zipOutput, "README", readme.getBytes(UTF_8));
        }
        return output.toByteArray();
    }

    private static void addZipEntry(ZipOutputStream output, String name, byte[] contents)
            throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(contents);
        output.closeEntry();
    }

    private static final class ParameterHolder {
        private static final Parameter<String> NAME = Parameter.create(REQUIRED);
    }
}
