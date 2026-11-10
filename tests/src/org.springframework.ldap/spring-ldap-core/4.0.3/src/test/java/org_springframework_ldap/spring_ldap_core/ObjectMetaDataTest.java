/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import org.junit.jupiter.api.Test;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.odm.annotations.Attribute;
import org.springframework.ldap.odm.annotations.Entry;
import org.springframework.ldap.odm.annotations.Id;
import org.springframework.ldap.odm.annotations.Transient;
import org.springframework.ldap.odm.core.impl.DefaultObjectDirectoryMapper;

import javax.naming.Name;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectMetaDataTest {
    @Test
    void discoversManagedFieldsAndBuildsTheObjectClassFilter() {
        DefaultObjectDirectoryMapper mapper = new DefaultObjectDirectoryMapper();
        assertThat(mapper.manageClass(Team.class)).contains("ou", "objectclass").doesNotContain("scratch");
        assertThat(mapper.attributeFor(Team.class, "label")).isEqualTo("ou");
        assertThat(mapper.filterFor(Team.class, new EqualsFilter("ou", "engineering")).encode())
                .contains("(objectclass=organizationalUnit)", "(ou=engineering)");
    }

    @Entry(objectClasses = {"top", "organizationalUnit"})
    public static final class Team {
        @Id
        private Name id;
        @Attribute(name = "ou")
        private String label;
        @Transient
        private String scratch;

        public Team() {
        }
    }
}
