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
    void configuresTlsNamedGroupsThroughCompatibilityApi() {
        SSLParameters parameters = new SSLParameters();
        String[] groups = {"secp256r1"};

        JreCompat.getInstance().setNamedGroupsMethod(parameters, groups);

        assertThat(parameters.getNamedGroups()).containsExactly(groups);
    }
}
