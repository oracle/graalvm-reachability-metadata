/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import com.sun.security.auth.module.Krb5LoginModule;

import org.apache.kafka.common.security.kerberos.KerberosError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.LoginException;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaCommonSecurityKerberosKerberosErrorTest {

    @Test
    @Timeout(60)
    void fromExceptionReadsKerberosReturnCodeFromNestedCause() throws Exception {
        String previousJavaVendor = System.getProperty("java.vendor");
        System.setProperty("java.vendor", "IBM Corporation");
        try {
            LoginException loginException = createKerberosLoginException();

            assertThat(KerberosError.fromException(loginException))
                    .isNull();
        } finally {
            restoreProperty("java.vendor", previousJavaVendor);
        }
    }

    private static LoginException createKerberosLoginException() throws Exception {
        String previousKrb5Config = System.getProperty("java.security.krb5.conf");
        Path krb5Config = Files.createTempFile("kafka-kerberos-test", ".conf");
        Files.writeString(krb5Config, """
                [libdefaults]
                    default_realm = KAFKA.TEST
                    dns_lookup_kdc = false
                [realms]
                """);
        System.setProperty("java.security.krb5.conf", krb5Config.toString());
        try {
            Krb5LoginModule loginModule = new Krb5LoginModule();
            Map<String, String> options = new HashMap<>();
            options.put("principal", "client@KAFKA.TEST");
            options.put("useTicketCache", "false");
            options.put("doNotPrompt", "false");
            options.put("refreshKrb5Config", "true");
            loginModule.initialize(
                    new Subject(),
                    OrgApacheKafkaCommonSecurityKerberosKerberosErrorTest::handleCallbacks,
                    new HashMap<>(),
                    options);

            try {
                loginModule.login();
                throw new AssertionError("Kerberos login unexpectedly succeeded");
            } catch (LoginException e) {
                assertThat(e.getCause()).isNotNull();
                return e;
            }
        } finally {
            restoreProperty("java.security.krb5.conf", previousKrb5Config);
            Files.deleteIfExists(krb5Config);
        }
    }

    private static void handleCallbacks(Callback[] callbacks) {
        for (Callback callback : callbacks) {
            if (callback instanceof NameCallback nameCallback) {
                nameCallback.setName("client@KAFKA.TEST");
            } else if (callback instanceof PasswordCallback passwordCallback) {
                passwordCallback.setPassword("secret".toCharArray());
            }
        }
    }

    private static void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }
}
