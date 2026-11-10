/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import org.junit.jupiter.api.Test;
import org.springframework.ldap.core.DirContextProxy;
import org.springframework.ldap.core.support.SingleContextSource;

import javax.naming.directory.DirContext;

import static org.assertj.core.api.Assertions.assertThat;

public class SingleContextSourceTest {
    @Test
    void sharesOneConnectionBetweenReadAndWriteViews() throws Exception {
        try (LdapServer server = new LdapServer()) {
            DirContext target = server.contextSource().getReadWriteContext();
            SingleContextSource source = new SingleContextSource(target);
            try {
                DirContext read = source.getReadOnlyContext();
                DirContext write = source.getReadWriteContext();
                assertThat(((DirContextProxy) read).getTargetContext()).isSameAs(target);
                assertThat(((DirContextProxy) write).getTargetContext()).isSameAs(target);
                read.close();
                assertThat(write.getAttributes("cn=Ada").get("sn").get()).isEqualTo("Lovelace");
                write.close();
            } finally {
                source.destroy();
            }
        }
    }
}
