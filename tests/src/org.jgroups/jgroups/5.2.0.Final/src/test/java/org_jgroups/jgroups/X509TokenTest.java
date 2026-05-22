/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.jgroups.auth.X509Token;
import org.jgroups.stack.Configurator;
import org.jgroups.util.StackType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class X509TokenTest {
    private static final String CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIDIzCCAgugAwIBAgIUPHWLAsg7YF68Gwl03XYY+8vuvUswDQYJKoZIhvcNAQEL
            BQAwITEfMB0GA1UEAwwWSkdyb3VwcyBYNTA5VG9rZW4gVGVzdDAeFw0yNjA1MjIy
            MzA5MzdaFw0zNjA1MTkyMzA5MzdaMCExHzAdBgNVBAMMFkpHcm91cHMgWDUwOVRv
            a2VuIFRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCz6rpONR8T
            kCJuQWoMCevQEMemdw2J1QzQv+GqCmg7eF6aNz+eT0pyTHzMI5kIAFiiDHS3EZ5U
            +u+jkrdia+QLsTvj3f4dDSAU/rC3nYhj/C55MCwE108Sz0AbRItMWdAL7WE7yhCR
            MetfKmddPzE4JKXzPour/bohyR3FNoT7XMtHVsKwWE8mEn8GO2R+JiCxq+sL6BeK
            3114VU7kof6yfrJpwGlbzujXWwqTPBZYtc0qs5yoQYhCaGjq60SUUnkjeRrMy5LT
            K1cIHXwniUWKI6JpM27Kmhhwiw1clQwAI4WR0mgf8mkKgp1SMJZkYSIViwFw3XZZ
            /M61Nts4kDeVAgMBAAGjUzBRMB0GA1UdDgQWBBRJ52PJe21eAcT6+uZa/Yqj/FHw
            YTAfBgNVHSMEGDAWgBRJ52PJe21eAcT6+uZa/Yqj/FHwYTAPBgNVHRMBAf8EBTAD
            AQH/MA0GCSqGSIb3DQEBCwUAA4IBAQBoBTS2lT+4/esKy5j7eun+mcqIy3e4sqmq
            rUhPuKB4+2i5ZxADrck/FcGR3EtuY8Y72vSSVPmC3Kp/A8ikJfIbWkaylc5pUdrM
            v1M4IpD8Lumfkp7bGnpeXXaqt0UrraIRLKKA/uIyP6E/8fjn5w95iD5DUHuYr3pe
            TAjmqCt6q2obuwMtSlnz9DNNYUAFTgloP9Fqe58SrkQrki2pt7EsgT6DgDt3Rxx5
            ZkzO/I7/9oxO8MvBzgnGO0PDywnz0mBsXY/xm4vSzIIxXYIFSyM7NP+S3sTi+vDZ
            oZPd08wTcsW/4jB8xETzZg4rL7YgNJjEGyr/CBulm2IMsZYNNQsz
            -----END CERTIFICATE-----
            """;

    private static final String PRIVATE_KEY = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCz6rpONR8TkCJu
            QWoMCevQEMemdw2J1QzQv+GqCmg7eF6aNz+eT0pyTHzMI5kIAFiiDHS3EZ5U+u+j
            krdia+QLsTvj3f4dDSAU/rC3nYhj/C55MCwE108Sz0AbRItMWdAL7WE7yhCRMetf
            KmddPzE4JKXzPour/bohyR3FNoT7XMtHVsKwWE8mEn8GO2R+JiCxq+sL6BeK3114
            VU7kof6yfrJpwGlbzujXWwqTPBZYtc0qs5yoQYhCaGjq60SUUnkjeRrMy5LTK1cI
            HXwniUWKI6JpM27Kmhhwiw1clQwAI4WR0mgf8mkKgp1SMJZkYSIViwFw3XZZ/M61
            Nts4kDeVAgMBAAECggEADYs2giuqjrjzdpRTDNv0VgHlXakpqj4RDGfdb4UACrYJ
            fRd6oQq8AnWykVV+sCUChxRj9uILJr+LKaeiSXTwwlaRuRodhodV6m6v0+q2eTxj
            LTlMnwKsT7CtVUAoMlOyfytb/fm8nImC+/N/u3vF00GjAngrYIOlruRqas2rXhai
            L5+Co76Im+kFu8GFybcRuKZAnz4oBvbe8QWoEFIGnQCdow4zFJgG/J7keQeXe7sg
            XA7wQEYdwdKs8gZ9dj9vGIMr8dQH+7/IV3kjrw2YWdZVserw0rj/wmVJUViqbZuV
            hIz6TzkgH/QhOF0SnPSOTOcbPqY2OvIPBW7CB65KcQKBgQDvYrexpUA4QC9zZW5v
            xDB1pmbUVJT2lOxQokH9VCJHm2v6WWs4S1WUqsCufw6qcNjPvaHIborio14/hatw
            6wMcvs2cjzko5/J4PveAaAsebp/Rgq2/yMSLKnSNL4leasXE/cPHmOz9TdVPvDb4
            pVwW39Z/r280W/YJhqQHpohTXQKBgQDAZ2Z3fLl5syGrW4OUvkYgibOZw9DYNFpD
            tzIcZ3mfHYwpH593IHNU0K+T8U3M3yPXWuSDSrA8ppE3wja/JwTuwI2vOY08CI1e
            XszuiDB6a6zv8ZFYre/jFF/wqrlxottl0MTSEnQO+rLLXi2PNymc8gTtzbR4rSQD
            Flowt2apmQKBgEenk/av0U5FIC75gJoh7qN4wLTz8Hby0t28A+axZWVrx2FznJ2I
            J0DN78kLrTclejCMwb1+IUJ/xjlbaJrvLcpP7rnbQS/WZgTKTEl6W0GruVj8Ncf/
            b518gtC0FvlVGofBsfWv99hGkQBCBW5eCPer3sfvmmjg0f+99qF3nrzdAoGADrnb
            Rdnt+DqVoR/n+kJ165eef25VVbbsEbK7yyDAVkVUrPEWq294ZE10osYdRkjt3VW/
            l6znwDyfcL0Uo5zA6+Ug8wBcpvOgnlBzLu6Nh1Emc1bx4O+vJJoynaDzgJcOY8SO
            y+VNrLfTZ21V31hBfxmm9ux/m5zk3LehP4lJ+kECgYEA3ldmoubZ6pt2VSI2WTlU
            IrgCLg2l4nApVXRwrKGcdYLkAnPL4B8ApyA2/8ick3r0NuxF3M+cilZZDI8XMTpO
            wbJn1qxYQPz/BAz51RMX+GkzxxfrDwCXuJWGSnd+o4pTKuVf0G0Y5ms5ZTNZzEyF
            wAorgnXmq5sBYrg3fjW/ev8=
            -----END PRIVATE KEY-----
            """;

    private static final String ALIAS = "jgroups-test";
    private static final String PASSWORD = "changeit";
    private static final String AUTH_VALUE = "shared-x509-secret";

    @Test
    void loadsCertificateFromConfiguredKeystorePathAndAuthenticatesPeerToken() throws Exception {
        Path keystorePath = createKeystore();
        X509Token coordinatorToken = configuredToken(keystorePath);
        X509Token joinerToken = configuredToken(keystorePath);

        coordinatorToken.setCertificate();
        joinerToken.setCertificate();

        assertThat(coordinatorToken.authenticate(joinerToken, null)).isTrue();
    }

    private static X509Token configuredToken(Path keystorePath) throws Exception {
        X509Token token = new X509Token();
        Map<String, String> properties = new HashMap<>();
        properties.put("keystore_type", "PKCS12");
        properties.put("keystore_path", keystorePath.toString());
        properties.put("keystore_password", PASSWORD);
        properties.put("cert_password", PASSWORD);
        properties.put("cert_alias", ALIAS);
        properties.put("auth_value", AUTH_VALUE);
        properties.put("cipher_type", "RSA");

        Configurator.initializeAttrs(token, properties, StackType.IPv4);

        assertThat(properties).isEmpty();
        return token;
    }

    private static Path createKeystore() throws Exception {
        Certificate certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(decodePem(CERTIFICATE, "CERTIFICATE")));
        PrivateKey privateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(decodePem(PRIVATE_KEY, "PRIVATE KEY")));

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        char[] password = PASSWORD.toCharArray();
        keyStore.load(null, password);
        keyStore.setKeyEntry(ALIAS, privateKey, password, new Certificate[] {certificate});

        Path keystorePath = Files.createTempFile("jgroups-x509-token-", ".p12");
        try (OutputStream output = Files.newOutputStream(keystorePath)) {
            keyStore.store(output, password);
        }
        return keystorePath;
    }

    private static byte[] decodePem(String pem, String type) {
        String base64 = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
