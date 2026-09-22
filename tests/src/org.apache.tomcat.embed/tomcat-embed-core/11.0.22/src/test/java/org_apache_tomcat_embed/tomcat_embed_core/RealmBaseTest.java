/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.realm.MessageDigestCredentialHandler;
import org.apache.catalina.realm.RealmBase;
import org.apache.catalina.realm.X509SubjectDnRetriever;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RealmBaseTest {

    @Test
    void initializesConfiguredCertificateUsernameRetriever() throws Exception {
        MemoryRealm realm = new MemoryRealm();
        StandardEngine engine = new StandardEngine();
        engine.setName("realm-engine");
        engine.setRealm(realm);
        realm.setX509UsernameRetrieverClassName(X509SubjectDnRetriever.class.getName());

        try {
            realm.init();

            assertThat(realm.getState()).isEqualTo(LifecycleState.INITIALIZED);
            assertThat(realm.getX509UsernameRetrieverClassName())
                    .isEqualTo(X509SubjectDnRetriever.class.getName());
        } finally {
            if (realm.getState() != LifecycleState.DESTROYED) {
                realm.destroy();
            }
        }
    }

    @Test
    void commandLineUtilityCreatesDefaultAndConfiguredCredentialHandlers() throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            RealmBase.main(new String[] {"default-password"});
            RealmBase.main(new String[] {"-h", MessageDigestCredentialHandler.class.getName(),
                    "-a", "SHA-256", "configured-password"});
        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).contains("default-password:", "configured-password:");
    }
}
