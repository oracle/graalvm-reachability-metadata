/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerb_server;

import org.apache.kerby.kerberos.kerb.KrbCodec;
import org.apache.kerby.kerberos.kerb.KrbException;
import org.apache.kerby.kerberos.kerb.KrbRuntime;
import org.apache.kerby.kerberos.kerb.identity.backend.BackendConfig;
import org.apache.kerby.kerberos.kerb.provider.TokenDecoder;
import org.apache.kerby.kerberos.kerb.provider.TokenEncoder;
import org.apache.kerby.kerberos.kerb.provider.TokenFactory;
import org.apache.kerby.kerberos.kerb.provider.TokenProvider;
import org.apache.kerby.kerberos.kerb.server.KdcConfig;
import org.apache.kerby.kerberos.kerb.server.KdcContext;
import org.apache.kerby.kerberos.kerb.server.KdcSetting;
import org.apache.kerby.kerberos.kerb.server.preauth.PreauthContext;
import org.apache.kerby.kerberos.kerb.server.preauth.PreauthHandler;
import org.apache.kerby.kerberos.kerb.server.preauth.token.TokenPreauth;
import org.apache.kerby.kerberos.kerb.server.request.KdcRequest;
import org.apache.kerby.kerberos.kerb.type.base.AuthToken;
import org.apache.kerby.kerberos.kerb.type.base.KrbTokenBase;
import org.apache.kerby.kerberos.kerb.type.base.PrincipalName;
import org.apache.kerby.kerberos.kerb.type.base.TokenFormat;
import org.apache.kerby.kerberos.kerb.type.kdc.AsReq;
import org.apache.kerby.kerberos.kerb.type.kdc.KdcReq;
import org.apache.kerby.kerberos.kerb.type.kdc.KdcReqBody;
import org.apache.kerby.kerberos.kerb.type.pa.PaDataEntry;
import org.apache.kerby.kerberos.kerb.type.pa.PaDataType;
import org.apache.kerby.kerberos.kerb.type.pa.token.PaTokenRequest;
import org.apache.kerby.kerberos.kerb.type.pa.token.TokenInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TokenPreauthTest {
    private static final String ISSUER = "issuer-under-test";
    private static final String REALM = "EXAMPLE.COM";
    private static final String SERVER_PRINCIPAL = "HTTP/localhost@" + REALM;
    private static final byte[] TOKEN_BYTES = "opaque-token".getBytes(StandardCharsets.UTF_8);

    @AfterEach
    void clearTokenProvider() {
        KrbRuntime.setTokenProvider(null);
    }

    @Test
    void verifiesHttpsTokenWhenVerifyKeyIsConfiguredAsClasspathResourceName() throws Exception {
        RecordingTokenDecoder tokenDecoder = new RecordingTokenDecoder(new SimpleAuthToken(SERVER_PRINCIPAL));
        KrbRuntime.setTokenProvider(new FixedTokenProvider(tokenDecoder));
        TokenPreauth tokenPreauth = new TokenPreauth();
        PaDataEntry paDataEntry = new PaDataEntry(PaDataType.TOKEN_REQUEST, encodedTokenRequest());
        TestKdcRequest kdcRequest = new TestKdcRequest(kdcRequest(), kdcContext());
        kdcRequest.setHttps(true);

        boolean verified = tokenPreauth.verify(kdcRequest, null, paDataEntry);

        assertThat(verified).isTrue();
        assertThat(tokenDecoder.decodedContent).containsExactly(TOKEN_BYTES);
    }

    private static byte[] encodedTokenRequest() throws KrbException {
        TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.setTokenVendor(ISSUER);
        KrbTokenBase token = new KrbTokenBase();
        token.setTokenFormat(TokenFormat.JWT);
        token.setTokenValue(TOKEN_BYTES);
        PaTokenRequest tokenRequest = new PaTokenRequest();
        tokenRequest.setTokenInfo(tokenInfo);
        tokenRequest.setToken(token);
        return KrbCodec.encode(tokenRequest);
    }

    private static KdcReq kdcRequest() {
        KdcReqBody body = new KdcReqBody();
        body.setRealm(REALM);
        body.setSname(new PrincipalName("HTTP/localhost"));
        AsReq request = new AsReq();
        request.setReqBody(body);
        return request;
    }

    private static KdcContext kdcContext() {
        KdcContext kdcContext = new KdcContext(new KdcSetting(new TokenKdcConfig(), new BackendConfig()));
        kdcContext.setPreauthHandler(new NoopPreauthHandler());
        return kdcContext;
    }

    private static final class TokenKdcConfig extends KdcConfig {
        @Override
        public boolean isAllowTokenPreauth() {
            return true;
        }

        @Override
        public String getVerifyKeyConfig() {
            return "token-preauth-key-is-a-classpath-resource-name.pem";
        }

        @Override
        public String getDecryptionKeyConfig() {
            return null;
        }

        @Override
        public List<String> getIssuers() {
            return Collections.singletonList(ISSUER);
        }
    }

    private static final class NoopPreauthHandler extends PreauthHandler {
        @Override
        public PreauthContext preparePreauthContext(KdcRequest kdcRequest) {
            return new PreauthContext();
        }
    }

    private static final class TestKdcRequest extends KdcRequest {
        private TestKdcRequest(KdcReq kdcReq, KdcContext kdcContext) {
            super(kdcReq, kdcContext);
        }

        @Override
        protected void makeReply() throws KrbException {
        }

        @Override
        protected void checkClient() throws KrbException {
        }

        @Override
        protected void issueTicket() throws KrbException {
        }
    }

    private static final class FixedTokenProvider implements TokenProvider {
        private final TokenDecoder tokenDecoder;

        private FixedTokenProvider(TokenDecoder tokenDecoder) {
            this.tokenDecoder = tokenDecoder;
        }

        @Override
        public TokenEncoder createTokenEncoder() {
            return null;
        }

        @Override
        public TokenDecoder createTokenDecoder() {
            return tokenDecoder;
        }

        @Override
        public TokenFactory createTokenFactory() {
            return null;
        }
    }

    private static final class RecordingTokenDecoder implements TokenDecoder {
        private final AuthToken token;
        private byte[] decodedContent;

        private RecordingTokenDecoder(AuthToken token) {
            this.token = token;
        }

        @Override
        public AuthToken decodeFromBytes(byte[] content) throws IOException {
            decodedContent = content;
            return token;
        }

        @Override
        public AuthToken decodeFromString(String content) throws IOException {
            decodedContent = content.getBytes(StandardCharsets.UTF_8);
            return token;
        }

        @Override
        public void setVerifyKey(PublicKey key) {
        }

        @Override
        public void setVerifyKey(byte[] key) {
        }

        @Override
        public void setDecryptionKey(PrivateKey key) {
        }

        @Override
        public void setDecryptionKey(byte[] key) {
        }

        @Override
        public boolean isSigned() {
            return false;
        }
    }

    private static final class SimpleAuthToken implements AuthToken {
        private final List<String> audiences;

        private SimpleAuthToken(String audience) {
            this.audiences = Collections.singletonList(audience);
        }

        @Override
        public String getSubject() {
            return "subject";
        }

        @Override
        public void setSubject(String sub) {
        }

        @Override
        public String getIssuer() {
            return ISSUER;
        }

        @Override
        public void setIssuer(String issuer) {
        }

        @Override
        public List<String> getAudiences() {
            return audiences;
        }

        @Override
        public void setAudiences(List<String> audiences) {
        }

        @Override
        public boolean isIdToken() {
            return false;
        }

        @Override
        public void isIdToken(boolean isIdToken) {
        }

        @Override
        public boolean isAcToken() {
            return true;
        }

        @Override
        public void isAcToken(boolean isAcToken) {
        }

        @Override
        public boolean isBearerToken() {
            return true;
        }

        @Override
        public boolean isHolderOfKeyToken() {
            return false;
        }

        @Override
        public Date getExpiredTime() {
            return null;
        }

        @Override
        public void setExpirationTime(Date exp) {
        }

        @Override
        public Date getNotBeforeTime() {
            return null;
        }

        @Override
        public void setNotBeforeTime(Date nbt) {
        }

        @Override
        public Date getIssueTime() {
            return null;
        }

        @Override
        public void setIssueTime(Date iat) {
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Collections.emptyMap();
        }

        @Override
        public void addAttribute(String name, Object value) {
        }
    }
}
