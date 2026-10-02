/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import javax.net.ssl.SSLParameters;

import org.apache.tomcat.util.compat.JreCompat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jre20CompatTest {

    @Test
    void configuresTlsNamedGroups() {
        SSLParameters parameters = new SSLParameters();
        String[] namedGroups = {"secp256r1", "x25519"};

        JreCompat.getInstance().setNamedGroupsMethod(parameters, namedGroups);

        assertThat(parameters.getNamedGroups()).containsExactly(namedGroups);
    }
}
