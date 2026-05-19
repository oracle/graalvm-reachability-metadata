/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerb_client;

import org.apache.kerby.KOptions;
import org.apache.kerby.kerberos.kerb.KrbException;
import org.apache.kerby.kerberos.kerb.client.ClientUtil;
import org.apache.kerby.kerberos.kerb.client.KrbClient;
import org.apache.kerby.kerberos.kerb.client.KrbConfig;
import org.apache.kerby.kerberos.kerb.client.KrbConfigKey;
import org.apache.kerby.kerberos.kerb.client.KrbContext;
import org.apache.kerby.kerberos.kerb.client.KrbKdcOption;
import org.apache.kerby.kerberos.kerb.client.KrbOption;
import org.apache.kerby.kerberos.kerb.client.KrbPkinitClient;
import org.apache.kerby.kerberos.kerb.client.KrbSetting;
import org.apache.kerby.kerberos.kerb.client.KrbTokenClient;
import org.apache.kerby.kerberos.kerb.client.PkinitOption;
import org.apache.kerby.kerberos.kerb.client.TokenOption;
import org.apache.kerby.kerberos.kerb.client.jaas.TokenCache;
import org.apache.kerby.kerberos.kerb.client.preauth.PreauthContext;
import org.apache.kerby.kerberos.kerb.client.preauth.UserResponser;
import org.apache.kerby.kerberos.kerb.client.preauth.pkinit.PkinitRequestOpts;
import org.apache.kerby.kerberos.kerb.client.preauth.token.TokenContext;
import org.apache.kerby.kerberos.kerb.transport.TransportPair;
import org.apache.kerby.kerberos.kerb.type.base.KrbToken;
import org.apache.kerby.kerberos.kerb.type.pa.PaData;
import org.apache.kerby.kerberos.kerb.type.pa.PaDataType;
import org.apache.kerby.kerberos.provider.token.JwtAuthToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Kerb_clientTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsClientConfigurationFromLibdefaultsSection() {
        KrbConfig config = configuredKrbConfig();

        assertThat(config.enableDebug()).isTrue();
        assertThat(config.getKdcHost()).isEqualTo("kdc.example.test");
        assertThat(config.getKdcRealm()).isEqualTo("EXAMPLE.TEST");
        assertThat(config.getKdcTcpPort()).isEqualTo(88);
        assertThat(config.getKdcUdpPort()).isEqualTo(750);
        assertThat(config.allowTcp()).isTrue();
        assertThat(config.allowUdp()).isTrue();
        assertThat(config.isPreauthRequired()).isFalse();
        assertThat(config.getTgsPrincipal()).isEqualTo("krbtgt/EXAMPLE.TEST@EXAMPLE.TEST");
        assertThat(config.getTicketLifetime()).isEqualTo("1234");
        assertThat(config.getRenewLifetime()).isEqualTo("5678");
    }

    @Test
    void loadsKrb5ConfFileAndResolvesRealmKdcEntries() throws Exception {
        Path krb5Conf = temporaryDirectory.resolve("krb5.conf");
        Files.writeString(krb5Conf, """
                [libdefaults]
                    %s = FILE.TEST
                    %s = config-host.example.test
                    %s = 10088
                    %s = 10089
                [realms]
                    FILE.TEST = {
                        kdc = first-kdc.example.test:10088
                        kdc = second-kdc.example.test:10089
                    }
                """.formatted(
                KrbConfigKey.DEFAULT_REALM.getPropertyKey(),
                KrbConfigKey.KDC_HOST.getPropertyKey(),
                KrbConfigKey.KDC_TCP_PORT.getPropertyKey(),
                KrbConfigKey.KDC_UDP_PORT.getPropertyKey()));

        KrbConfig config = ClientUtil.getConfig(temporaryDirectory.toFile());
        KrbSetting setting = new KrbSetting(config);
        List<String> kdcList = ClientUtil.getKDCList(config.getDefaultRealm(), setting);

        assertThat(config.getDefaultRealm()).isEqualTo("FILE.TEST");
        assertThat(config.getKdcRealm()).isEqualTo("FILE.TEST");
        assertThat(config.getKdcHost()).isEqualTo("config-host.example.test");
        assertThat(config.getKdcTcpPort()).isEqualTo(10088);
        assertThat(config.getKdcUdpPort()).isEqualTo(10089);
        assertThat(config.getRealmSectionItems("FILE.TEST", "kdc"))
                .containsExactlyInAnyOrder("first-kdc.example.test:10088", "second-kdc.example.test:10089");
        assertThat(kdcList)
                .containsExactlyInAnyOrder("first-kdc.example.test:10088", "second-kdc.example.test:10089");
    }

    @Test
    void settingUsesExplicitClientOptionsBeforeConfigurationDefaults() throws Exception {
        KrbConfig config = configuredKrbConfig();
        KOptions options = new KOptions();
        options.add(KrbOption.KDC_HOST, "override.example.test");
        options.add(KrbOption.KDC_REALM, "OVERRIDE.TEST");
        options.add(KrbOption.ALLOW_TCP, false);
        options.add(KrbOption.ALLOW_UDP, true);
        options.add(KrbOption.KDC_TCP_PORT, 10088);
        options.add(KrbOption.KDC_UDP_PORT, 10750);
        options.add(KrbOption.CONN_TIMEOUT, 25);

        KrbSetting setting = new KrbSetting(options, config);

        assertThat(setting.getKdcHost()).isEqualTo("override.example.test");
        assertThat(setting.getKdcRealm()).isEqualTo("OVERRIDE.TEST");
        assertThat(setting.allowTcp()).isFalse();
        assertThat(setting.allowUdp()).isTrue();
        assertThat(setting.getKdcTcpPort()).isEqualTo(10088);
        assertThat(setting.getKdcUdpPort()).isEqualTo(10750);
        assertThat(setting.checkGetKdcTcpPort()).isEqualTo(-1);
        assertThat(setting.checkGetKdcUdpPort()).isEqualTo(10750);
        assertThat(setting.getTimeout()).isEqualTo(25);
    }

    @Test
    void clientSettersValidatePortsAndUpdateSettings() throws Exception {
        KrbClient client = new KrbClient(new KrbConfig());

        client.setKdcHost("localhost");
        client.setKdcRealm("EXAMPLE.TEST");
        client.setKdcTcpPort(10088);
        client.setKdcUdpPort(10089);
        client.setTimeout(75);

        assertThat(client.getSetting().getKdcHost()).isEqualTo("localhost");
        assertThat(client.getSetting().getKdcRealm()).isEqualTo("EXAMPLE.TEST");
        assertThat(client.getSetting().allowTcp()).isTrue();
        assertThat(client.getSetting().allowUdp()).isTrue();
        assertThat(client.getSetting().checkGetKdcTcpPort()).isEqualTo(10088);
        assertThat(client.getSetting().checkGetKdcUdpPort()).isEqualTo(10089);
        assertThat(client.getSetting().getTimeout()).isEqualTo(75);
        assertThatThrownBy(() -> client.setKdcTcpPort(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.setKdcUdpPort(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pkinitAndTokenClientsShareBaseClientConfiguration() throws Exception {
        KrbClient client = new KrbClient(configuredKrbConfig());
        client.setKdcHost("shared.example.test");
        client.setKdcTcpPort(10088);

        KrbPkinitClient pkinitClient = new KrbPkinitClient(client);
        KrbTokenClient tokenClient = new KrbTokenClient(client);

        assertThat(pkinitClient.getSetting()).isSameAs(client.getSetting());
        assertThat(tokenClient.getSetting()).isSameAs(client.getSetting());
        assertThat(pkinitClient.getSetting().getKdcHost()).isEqualTo("shared.example.test");
        assertThat(tokenClient.getSetting().checkGetKdcTcpPort()).isEqualTo(10088);
    }

    @Test
    void clientInitializesPreauthPluginsAndFailsFastForUnavailableKdc() throws Exception {
        KrbClient client = new KrbClient(new KrbConfig());
        client.setKdcRealm("EXAMPLE.TEST");
        client.setKdcHost("127.0.0.1");
        client.setKdcTcpPort(1);
        client.setAllowUdp(false);
        client.setTimeout(50);
        client.init();

        assertThatThrownBy(() -> client.requestTgt("alice@EXAMPLE.TEST", "secret"))
                .isInstanceOf(KrbException.class);
        assertThatThrownBy(() -> client.requestTgt((KOptions) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Null requestOptions");
    }

    @Test
    void transportPairParsesExplicitHostPortsAndIpv6Addresses() throws Exception {
        KOptions options = new KOptions();
        options.add(KrbOption.ALLOW_TCP, true);
        options.add(KrbOption.ALLOW_UDP, true);
        options.add(KrbOption.KDC_TCP_PORT, 88);
        options.add(KrbOption.KDC_UDP_PORT, 750);
        KrbSetting setting = new KrbSetting(options, new KrbConfig());

        TransportPair namedHostPair = ClientUtil.getTransportPair(setting, "kdc.example.test:10088");
        TransportPair ipv6Pair = ClientUtil.getTransportPair(setting, "[::1]:10089");

        assertThat(namedHostPair.tcpAddress.getHostString()).isEqualTo("kdc.example.test");
        assertThat(namedHostPair.tcpAddress.getPort()).isEqualTo(10088);
        assertThat(namedHostPair.udpAddress.getPort()).isEqualTo(10088);
        assertThat(ipv6Pair.tcpAddress.getHostString()).isIn("::1", "0:0:0:0:0:0:0:1");
        assertThat(ipv6Pair.tcpAddress.getPort()).isEqualTo(10089);
        assertThatThrownBy(() -> ClientUtil.getTransportPair(setting, "[::1"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Illegal KDC");
    }

    @Test
    void contextInitializesPreauthHandlerAndComputesTicketValidity() {
        KrbConfig config = configuredKrbConfig();
        KrbContext context = new KrbContext();

        context.init(new KrbSetting(config));

        assertThat(context.getKrbSetting().getKrbConfig()).isSameAs(config);
        assertThat(context.getConfig()).isSameAs(config);
        assertThat(context.getTicketValidTime()).isEqualTo(1_234_000L);
        assertThat(context.generateNonce()).isBetween(0, Integer.MAX_VALUE);
        assertThat(context.getPreauthHandler()).isNotNull();
    }

    @Test
    void preauthContextTracksAllowedTypesInputDataAndUserResponses() throws Exception {
        PreauthContext context = new PreauthContext();
        PaData input = new PaData();
        PaData error = new PaData();

        assertThat(context.isPreauthRequired()).isTrue();
        assertThat(context.hasInputPaData()).isFalse();
        assertThat(context.isPaTypeAllowed(PaDataType.ENC_TIMESTAMP)).isTrue();
        context.setPreauthRequired(false);
        context.setAllowedPaType(PaDataType.ENC_TIMESTAMP);
        context.setInputPaData(input);
        context.setErrorPaData(error);

        assertThat(context.isPreauthRequired()).isFalse();
        assertThat(context.getInputPaData()).isSameAs(input);
        assertThat(context.getErrorPaData()).isSameAs(error);
        assertThat(context.getOutputPaData()).isNotNull();
        assertThat(context.hasInputPaData()).isFalse();
        assertThat(context.isPaTypeAllowed(PaDataType.ENC_TIMESTAMP)).isTrue();
        assertThat(context.isPaTypeAllowed(PaDataType.PK_AS_REQ)).isFalse();
        assertThat(context.checkAndPutTried(PaDataType.ENC_TIMESTAMP)).isFalse();
        assertThat(context.checkAndPutTried(PaDataType.ENC_TIMESTAMP)).isTrue();
        assertThat(context.getHandles()).isEmpty();

        UserResponser responser = context.getUserResponser();
        responser.askQuestion("otp", "Enter OTP");
        responser.setAnswer("otp", "123456");
        assertThat(responser.getChallenge("otp")).isEqualTo("Enter OTP");
        assertThat(responser.getAnswer("otp")).isEqualTo("123456");
        assertThat(responser.findQuestion("otp")).isNotNull();
        assertThatThrownBy(() -> responser.setAnswer("missing", "answer"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void optionEnumsResolveByPublicOptionNames() {
        assertOptionRoundTrip(KrbOption.KDC_HOST);
        assertOptionRoundTrip(KrbOption.USER_PASSWD);
        assertOptionRoundTrip(KrbOption.USE_TGT);
        assertOptionRoundTrip(KrbKdcOption.FORWARDABLE);
        assertOptionRoundTrip(KrbKdcOption.RENEWABLE_OK);
        assertOptionRoundTrip(TokenOption.USE_TOKEN);
        assertOptionRoundTrip(TokenOption.USER_ID_TOKEN);
        assertOptionRoundTrip(PkinitOption.USE_PKINIT);
        assertOptionRoundTrip(PkinitOption.X509_IDENTITY);

        assertThat(KrbOption.fromOptionName("missing")).isSameAs(KrbOption.NONE);
        assertThat(KrbKdcOption.fromOptionName("missing")).isSameAs(KrbKdcOption.NONE);
        assertThat(TokenOption.fromOptionName("missing")).isSameAs(TokenOption.NONE);
        assertThat(PkinitOption.fromOptionName("missing")).isSameAs(PkinitOption.NONE);
    }

    @Test
    void tokenCacheWritesAndReadsUtf8TokenFiles() {
        Path tokenCache = temporaryDirectory.resolve("token.cache");
        String token = "header.payload.signature";

        TokenCache.writeToken(token, tokenCache.toString());

        assertThat(TokenCache.readToken(tokenCache.toString())).isEqualTo(token);
        assertThatThrownBy(() -> TokenCache.readToken(temporaryDirectory.resolve("missing.cache").toString()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid token cache");
    }

    @Test
    void tokenClientRejectsMismatchedTokenCredentialTypes() {
        KrbTokenClient tokenClient = new KrbTokenClient(new KrbConfig());
        KrbToken identityToken = krbToken(true, false);
        KrbToken accessToken = krbToken(false, true);

        assertThatThrownBy(() -> tokenClient.requestTgt(accessToken, "armor.cache"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Identity token is expected");
        assertThatThrownBy(() -> tokenClient.requestSgt(
                identityToken, "HTTP/service.example.test@EXAMPLE.TEST", "armor.cache"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Access token is expected");
    }

    @Test
    void pkinitAndTokenRequestContextsExposeUsableDefaults() {
        PkinitRequestOpts pkinitRequestOpts = new PkinitRequestOpts();
        TokenContext tokenContext = new TokenContext();

        assertThat(pkinitRequestOpts.isRequireEku()).isTrue();
        assertThat(pkinitRequestOpts.isAcceptSecondaryEku()).isFalse();
        assertThat(pkinitRequestOpts.isUsingRsa()).isFalse();
        assertThat(pkinitRequestOpts.getDhSize()).isPositive();
        assertThat(tokenContext.isUsingIdToken()).isTrue();
        assertThat(tokenContext.getToken()).isNull();
    }

    private static KrbToken krbToken(boolean identityToken, boolean accessToken) {
        JwtAuthToken authToken = new JwtAuthToken();
        authToken.isIdToken(identityToken);
        authToken.isAcToken(accessToken);

        KrbToken token = new KrbToken();
        token.setInnerToken(authToken);
        return token;
    }

    private static KrbConfig configuredKrbConfig() {
        KrbConfig config = new KrbConfig();
        Map<String, Object> libdefaults = new LinkedHashMap<>();
        libdefaults.put(KrbConfigKey.KRB_DEBUG.getPropertyKey(), "true");
        libdefaults.put(KrbConfigKey.KDC_HOST.getPropertyKey(), "kdc.example.test");
        libdefaults.put(KrbConfigKey.KDC_REALM.getPropertyKey(), "EXAMPLE.TEST");
        libdefaults.put(KrbConfigKey.KDC_ALLOW_TCP.getPropertyKey(), "true");
        libdefaults.put(KrbConfigKey.KDC_ALLOW_UDP.getPropertyKey(), "false");
        libdefaults.put(KrbConfigKey.KDC_TCP_PORT.getPropertyKey(), "88");
        libdefaults.put(KrbConfigKey.KDC_UDP_PORT.getPropertyKey(), "750");
        libdefaults.put(KrbConfigKey.TGS_PRINCIPAL.getPropertyKey(), "krbtgt/EXAMPLE.TEST@EXAMPLE.TEST");
        libdefaults.put(KrbConfigKey.PREAUTH_REQUIRED.getPropertyKey(), "false");
        libdefaults.put(KrbConfigKey.TICKET_LIFETIME.getPropertyKey(), "1234");
        libdefaults.put(KrbConfigKey.RENEW_LIFETIME.getPropertyKey(), "5678");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("libdefaults", libdefaults);
        config.addMapConfig(root);
        return config;
    }

    private static void assertOptionRoundTrip(KrbOption option) {
        assertThat(KrbOption.fromOptionName(option.getOptionInfo().getName())).isSameAs(option);
        assertThat(option.getOptionInfo().getDescription()).isNotBlank();
    }

    private static void assertOptionRoundTrip(KrbKdcOption option) {
        assertThat(KrbKdcOption.fromOptionName(option.getOptionInfo().getName())).isSameAs(option);
        assertThat(option.getOptionInfo().getDescription()).isNotBlank();
    }

    private static void assertOptionRoundTrip(TokenOption option) {
        assertThat(TokenOption.fromOptionName(option.getOptionInfo().getName())).isSameAs(option);
        assertThat(option.getOptionInfo().getDescription()).isNotBlank();
    }

    private static void assertOptionRoundTrip(PkinitOption option) {
        assertThat(PkinitOption.fromOptionName(option.getOptionInfo().getName())).isSameAs(option);
        assertThat(option.getOptionInfo().getDescription()).isNotBlank();
    }
}
