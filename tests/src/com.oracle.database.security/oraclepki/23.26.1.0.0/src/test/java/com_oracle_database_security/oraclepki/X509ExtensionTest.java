/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import oracle.security.pki.internal.cert.X509Extension;
import oracle.security.pki.internal.cert.ext.BasicConstraintsExtension;
import org.junit.jupiter.api.Test;

public class X509ExtensionTest {
    @Test
    void reconstructsRegisteredExtensionSubtype() throws Exception {
        BasicConstraintsExtension original = new BasicConstraintsExtension(true);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        original.output(encoded);

        X509Extension decoded =
                X509Extension.a(new ByteArrayInputStream(encoded.toByteArray()));

        assertThat(decoded).isInstanceOf(BasicConstraintsExtension.class);
        assertThat(((BasicConstraintsExtension) decoded).e()).isTrue();
    }
}
