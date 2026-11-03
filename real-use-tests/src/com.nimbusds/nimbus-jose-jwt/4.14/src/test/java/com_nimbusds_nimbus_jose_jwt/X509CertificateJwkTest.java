/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_nimbusds_nimbus_jose_jwt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.X509CertUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.cert.X509Certificate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises X.509 certificate-backed JWK publication for an API signing key.
public class X509CertificateJwkTest {
    private static final String CERTIFICATE_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIDTzCCAjegAwIBAgIUX2kOd6JMJlUVgLNn8JaB0vQsDp8wDQYJKoZIhvcNAQEL
            BQAwNzEfMB0GA1UEAwwWb3JkZXJzLmV4YW1wbGUuaW52YWxpZDEUMBIGA1UECgwL
            RXhhbXBsZSBBUEkwHhcNMjYxMTAzMjMwNDM4WhcNMzYxMDMxMjMwNDM4WjA3MR8w
            HQYDVQQDDBZvcmRlcnMuZXhhbXBsZS5pbnZhbGlkMRQwEgYDVQQKDAtFeGFtcGxl
            IEFQSTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALGohaOnNRhev1rm
            X8kYtivyh0whElLSXfdbUiLXDn6hbGG05KodT8X7dRMLjFjLq/HUycAG5uEy2+PL
            3siGUdFoHWMAIIEe1c1ZQHxPqhshtzwvMolbV/fXD0myFfpE8N1UTEXQ61n2iOBl
            xmqmwPA/9TsnTYy3mRg4V8saODq+j+qjw+JQHgJnbxeQqAH+iGJVipXNC3xbPXhp
            kgzfDniIHaNfOkBpuGyv48cpmRd20lxLnkD8wgpcL9LyT7pV4JD7twLOPLi7zqJ8
            LWjBlsjHIj6XIP/a2MFXkdV72mSkZkyoXI/oPZahLYa+r3d7hgIqkWTqoyD4feUV
            SKgWcA8CAwEAAaNTMFEwHQYDVR0OBBYEFJ7N1YzaoMLkpzJ3pIM9K4MctvYWMB8G
            A1UdIwQYMBaAFJ7N1YzaoMLkpzJ3pIM9K4MctvYWMA8GA1UdEwEB/wQFMAMBAf8w
            DQYJKoZIhvcNAQELBQADggEBAFExgv+tACSrjBFN/MKyeHmw2qRnNE0/Ri/f/0ci
            DqclGeDWHf3C9BsCAMHiu7aj2Alt3Pz/XI+wK67aV/Atkxc5RxbrfZA6/Ehf/MDL
            AwSE6hDrQ9wzqC8t+SBs3ppEr3+RNdx7PxIXe0GgkrnxOxM4OPIb81TvtT0PBW6U
            dNz0dzM+9FjUTj1Nlgtd5aAAgaUFtQ7ExZj2mhOJR0kOsmKL4rzasy8sKyeH3k2t
            QH56dPlU0lnrK3e/sKv3yLDqtgF/ZGnlkZ5DS2alEZXHa5XiV0obdui7Ik0mwMBr
            XGyHtyzSjagPTWC5299VSlhgS0Vb0W3dwY1GqjAKdnuToa8=
            -----END CERTIFICATE-----
            """;

    private static final String PRIVATE_KEY_PEM = """
            -----BEGIN PRIVATE KEY-----
            MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCxqIWjpzUYXr9a
            5l/JGLYr8odMIRJS0l33W1Ii1w5+oWxhtOSqHU/F+3UTC4xYy6vx1MnABubhMtvj
            y97IhlHRaB1jACCBHtXNWUB8T6obIbc8LzKJW1f31w9JshX6RPDdVExF0OtZ9ojg
            ZcZqpsDwP/U7J02Mt5kYOFfLGjg6vo/qo8PiUB4CZ28XkKgB/ohiVYqVzQt8Wz14
            aZIM3w54iB2jXzpAabhsr+PHKZkXdtJcS55A/MIKXC/S8k+6VeCQ+7cCzjy4u86i
            fC1owZbIxyI+lyD/2tjBV5HVe9pkpGZMqFyP6D2WoS2Gvq93e4YCKpFk6qMg+H3l
            FUioFnAPAgMBAAECggEAEFaKK7m11U7pVCe5iVnfbv032hV0hO9ZuuTIEfIrIBQi
            Tn3/RXG6o19zrdOzDXt1jaDfCOzZkL5HoJRgIZrQrWxgv3kEhDtcuCzMFGKTJzmb
            i0pf4GfpGB7MBh/9i22JpSEafpUu/6//eHuDms1JEw8es1jy9czwpFySHliD8Urr
            utbD24LoHv25LZON4EKcQcQLnLYx3yfIp6V9mpYeaS3Toc89nmluCfJ9amYU6MjA
            mFiHSBdqIXUCHDf2ij+EctP7PM7dD9krZR7UAm905bHALj7kw0bvTvxhxNj8BE18
            y5n3UhzlS4O/HAQNWEPVh9O8rX0b/w+yhrB+1FprOQKBgQDo3JPFKiaxrCzWru8D
            NiazkVF6KIZwWBfrixMzKA0Bw/Sj7WK5Nvk7R0psRIkb2evQhGgaC2QCLr8vcswX
            YPRfm4Mi+s1ckHkWIDYKQ74EAd2kWq46ctYaufPGmJUonsSDDLitMzWa/S+oVJY2
            GV0FX+w9FhSynlt69rQSQSJocwKBgQDDT7VoWCJ5CpYqV4Zwr8ngm9LciDanJbjF
            3fR/FkYXx2H0/YVBzjn5Pll5K+nJ9Pd5POPqH9HTverAA/lrLvIEtApZ+eWyYYH/
            ul/qFrjuAg2PDPAe/lsnthrU096hPN6b+NelrKCmkxzh7hr+ePTB+btQOICrpm7m
            ioTT0qke9QKBgQDOos5Oa/UiPRMc/ucCBo+ZUR7oNAX4s3OmhSdogeXo5ya0QPcW
            PH416DFWmgrBOERWKOjoE6uPdEQbqtcI9/Ri0vEvnTeXKG0LAVJzEWIQ1EcGjaGG
            JFlyJgj49nWGMj1XvXYz2/l5q6sayNTy9Nbi6hgMrQd46aVsHHf2qu4JmQKBgQCX
            Cz606mwGhcxqBHRBE4GhRYDF8kWp8D88LpGV8yjT3OsBp0p2AMHO4owZbH+KIvt7
            9D4iYudHxHkWdDxgytZ6Eq5qtRsRX91EYuMoSthLoKTRu2S742Oli2ymqQ9kTImv
            v7suMhmvmNfeVeDkabCHjtVwjbHcjYL4oallk2YdPQKBgE0pKx36ou51VAR3Vi5m
            cMnOVY9jilc3EQtdogjhy/xPKwKGB76EitMasXQ2NWqGhnFtIjm9awhixXV7jiet
            vaV1uRU0X5kgNeP4bBW65qXUe8IlRNRt9vZm1UmNA796kVTCEwc/Ro0P44Y5sRS9
            UxihmN4q0ccJjfuxVru182FC
            -----END PRIVATE KEY-----
            """;

    @Test
    @Timeout(60)
    public void publishesCertificateBackedJwkForTokenVerification() throws Exception {
        X509Certificate certificate = X509CertUtils.parse(CERTIFICATE_PEM);
        RSAPublicKey publicKey = (RSAPublicKey) certificate.getPublicKey();
        RSAPrivateKey privateKey = loadPrivateKey();
        RSAKey signingKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID("orders-certificate")
                .x509CertChain(List.of(Base64.encode(certificate.getEncoded())))
                .build();

        RSAKey publishedKey = RSAKey.parse(signingKey.toPublicJWK().toJSONString());
        assertEquals("orders-certificate", publishedKey.getKeyID());
        assertEquals(1, publishedKey.getX509CertChain().size());
        assertArrayEquals(
                certificate.getEncoded(), publishedKey.getX509CertChain().get(0).decode());
        assertTrue(certificate.getSubjectX500Principal().getName()
                .contains("CN=orders.example.invalid"));

        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(publishedKey.getKeyID())
                        .build(),
                new JWTClaimsSet.Builder()
                        .issuer("https://accounts.example.invalid")
                        .subject("customer-42")
                        .claim("scope", "orders:read")
                        .build());
        token.sign(new RSASSASigner(signingKey));

        SignedJWT receivedToken = SignedJWT.parse(token.serialize());
        assertTrue(receivedToken.verify(new RSASSAVerifier(publishedKey)));
        assertEquals("customer-42", receivedToken.getJWTClaimsSet().getSubject());
        assertEquals("orders:read", receivedToken.getJWTClaimsSet().getStringClaim("scope"));
    }

    private static RSAPrivateKey loadPrivateKey() throws Exception {
        String encodedKey = PRIVATE_KEY_PEM
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        PrivateKey privateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(
                        java.util.Base64.getDecoder().decode(encodedKey)));
        return (RSAPrivateKey) privateKey;
    }
}
