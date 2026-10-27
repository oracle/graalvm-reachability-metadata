/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerb_server;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.kerby.kerberos.kerb.KrbCodec;
import org.apache.kerby.kerberos.kerb.KrbRuntime;
import org.apache.kerby.kerberos.kerb.identity.backend.BackendConfig;
import org.apache.kerby.kerberos.kerb.server.KdcConfig;
import org.apache.kerby.kerberos.kerb.server.KdcContext;
import org.apache.kerby.kerberos.kerb.server.KdcSetting;
import org.apache.kerby.kerberos.kerb.server.preauth.PreauthHandler;
import org.apache.kerby.kerberos.kerb.server.preauth.token.TokenPreauth;
import org.apache.kerby.kerberos.kerb.server.request.AsRequest;
import org.apache.kerby.kerberos.kerb.type.base.AuthToken;
import org.apache.kerby.kerberos.kerb.type.base.KrbTokenBase;
import org.apache.kerby.kerberos.kerb.type.base.PrincipalName;
import org.apache.kerby.kerberos.kerb.type.base.TokenFormat;
import org.apache.kerby.kerberos.kerb.type.kdc.AsReq;
import org.apache.kerby.kerberos.kerb.type.kdc.KdcReqBody;
import org.apache.kerby.kerberos.kerb.type.pa.PaDataEntry;
import org.apache.kerby.kerberos.kerb.type.pa.PaDataType;
import org.apache.kerby.kerberos.kerb.type.pa.token.PaTokenRequest;
import org.apache.kerby.kerberos.kerb.type.pa.token.TokenInfo;
import org.apache.kerby.kerberos.provider.token.JwtTokenProvider;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TokenPreauthTest {
    static final String ISSUER = "kerby-token-preauth-issuer";
    static final String VERIFY_KEY_RESOURCE = "kerby-token-preauth-issuer-public.pem";
    private static final String REALM = "EXAMPLE.COM";
    private static final String CLIENT_PRINCIPAL = "client@" + REALM;
    private static final String SERVICE_NAME = "HTTP/localhost";
    private static final String SERVICE_PRINCIPAL = SERVICE_NAME + "@" + REALM;

    @Test
    void verifiesHttpsTokenRequestWithClasspathVerifyKey() throws Exception {
        KrbRuntime.setTokenProvider(new JwtTokenProvider());
        try {
            TokenPreauth preauth = new TokenPreauth();
            TokenAsRequest request = tokenKdcRequest();
            PaDataEntry paData = new PaDataEntry(PaDataType.TOKEN_REQUEST, encodedTokenRequest());

            assertThat(preauth.verify(request, null, paData)).isTrue();
            assertThat(request.token().getSubject()).isEqualTo(CLIENT_PRINCIPAL);
        } finally {
            KrbRuntime.setTokenProvider(null);
        }
    }

    private static TokenAsRequest tokenKdcRequest() {
        PreauthHandler preauthHandler = new PreauthHandler();
        preauthHandler.init();

        KdcConfig config = new TokenPreauthKdcConfig();
        KdcContext context = new KdcContext(new KdcSetting(config, new BackendConfig()));
        context.setPreauthHandler(preauthHandler);

        TokenAsRequest request = new TokenAsRequest(asReq(), context);
        request.setHttps(true);
        return request;
    }

    private static byte[] encodedTokenRequest() throws Exception {
        KrbTokenBase token = new KrbTokenBase();
        token.setTokenFormat(TokenFormat.JWT);
        token.setTokenValue(serializedJwt().getBytes(StandardCharsets.UTF_8));

        TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.setTokenVendor(ISSUER);

        PaTokenRequest tokenRequest = new PaTokenRequest();
        tokenRequest.setToken(token);
        tokenRequest.setTokenInfo(tokenInfo);
        return KrbCodec.encode(tokenRequest);
    }

    private static AsReq asReq() {
        KdcReqBody body = new KdcReqBody();
        body.setSname(new PrincipalName(SERVICE_NAME));
        body.setRealm(REALM);

        AsReq asReq = new AsReq();
        asReq.setReqBody(body);
        return asReq;
    }

    private static String serializedJwt() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet();
        claims.setSubject(CLIENT_PRINCIPAL);
        claims.setAudience(SERVICE_PRINCIPAL);

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signingKey()));
        return jwt.serialize();
    }

    private static RSAPrivateKey signingKey() throws Exception {
        String pem = """
                -----BEGIN PRIVATE KEY-----
                MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCYLDBrmzID2iM5
                Cv/mKZcyGJ7F5yFoknWqHDPo2ssBGsGoQBNRCCbmHB4OqRytC3gVog+CCE96ALgT
                AHeyVxSm05GWjSHBqWwYBJMSXbszV3l/hlanHhdoX58AQosuTOd0LfN57PAK8mMI
                90sEUrkWCA/zku16RhTHGyNeshYVztW9hEnFpXtkFE6DE98VjuTXLlRzUrE+pgW4
                YmoUhk0wUuez5YJBGqaxHls4fw7ysG1m1oP5kDreby4EPMkdTnTHuOLDuSCblk+l
                rmja7cj2KggFVvUOEoJkTVFuGjdm/l++4iqwKf+2lDzvTKxpiDBUmcFPu0ps2JOW
                VN9VTBIrAgMBAAECggEACO0H9rqMOlNIdKHzX7NPK0DsHeyloNt9FmTVcKerd7Lz
                lWRoyFHY7O93neGH4pNFJckImCvXnu7oMokjpuzaXmy+N0gA/mSV3WognFeuJt/5
                1aPyH5cGxXhdEb1Vf6TtgKWUs1wAD+Gm3dGaRLR7fHzYPq/UHojPfYcB0fNSk1B5
                gm58dQeN4P9uu1LjNUdORU9gY3+/tmmk3Bvrg1HJAL6yx6Qq4VJeVOlxqg9hrei3
                qJAakxG1P1LKB3In3lk2vYBvmBW5GgCkIxMaklFTj2ymCaXIDVzMrbwHGiqFMYHJ
                QqYjH0ElPdNfXkf2OSYH/StHJu0Xd9AqKCLRLrkfOQKBgQDWSnAlYfdfIv3zWWee
                2Nc2cr8CwkYv8BxX5OLf9eZh9CO6OCi+aGTWvWZVh9DQdaga0OMCGQgX2aOJszp3
                UYCzwTRJVfXs+mbT6C8BAS5qACxbGDLod6RGlAj1qLXbpO3B5CbjjLN648PsPNE2
                W5I8rhILmQvGV+60sq599J4UKQKBgQC1yo/Tk3X6Nb7bo75Z9Cl3MF7S4caT9vQZ
                1ieJue+wSTOBMS1lnbxDAix1HQmsYhRqhNR/nMlVdj9qOv+XJI63a+hGD/FzPt1o
                VfXsQa+eTwwKS05mqHch4kRejhxKWlhxbd4cZGLDRM3+Yysl48Rw1awavvXbl9RO
                owhrYA9eMwKBgBsw/uH/eGA+FHNYmOlzInvqO4qiFD3o/e+5P4Cjt0Qespninmjq
                3kDRb7bSsRCCnX4pm2ScwIYmO7YxY+3YDbjBf1z+52HWtE2XXL+H4tfYhchZXbxZ
                0iKSjkB90/W3C+RaiwS8ydmCJU5IuVNoczn37JpEJVZZuhR7x+B7tcNhAoGAPmgV
                S8qZe5WruXbSx8qb2YDPbJF5PB5Q2fW8iPk9oUTf23/oV7P4hwBeBq47PeRFofOL
                h2tpal83kd5DE73HCIyQpkye1LNGCqVH0R0TjsMQHRCRD4jRrN4iHlumtpehOP9A
                enqztNkP5j5g95bjPuD7PgtEF2hdx8kMAOBvJiUCgYEAlc3DhiFCgUN72J972/6z
                86RQRGe+cZ/KwAtksFDwgUKkqotsLpvXVuQT4f86JMjzS0gdslZOiJqXc18mixEd
                PAO8sGvMPMpho3hLkUUUsW7hzMr++IY08hCWY0n1xcPM2JrioBzU6V68UcCEwAyZ
                DBi4TvlRE8jKTr7bJN0guuk=
                -----END PRIVATE KEY-----
                """;
        String key = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] encoded = Base64.getDecoder().decode(key);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }
}

class TokenAsRequest extends AsRequest {
    TokenAsRequest(AsReq asReq, KdcContext kdcContext) {
        super(asReq, kdcContext);
    }

    AuthToken token() {
        return getToken();
    }
}

class TokenPreauthKdcConfig extends KdcConfig {
    @Override
    public String getVerifyKeyConfig() {
        return TokenPreauthTest.VERIFY_KEY_RESOURCE;
    }

    @Override
    public List<String> getIssuers() {
        return Collections.singletonList(TokenPreauthTest.ISSUER);
    }
}
