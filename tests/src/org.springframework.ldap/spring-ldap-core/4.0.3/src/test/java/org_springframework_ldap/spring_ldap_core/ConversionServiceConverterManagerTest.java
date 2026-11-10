/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.odm.annotations.Entry;
import org.springframework.ldap.odm.annotations.Id;
import org.springframework.ldap.odm.core.impl.DefaultObjectDirectoryMapper;
import org.springframework.ldap.odm.typeconversion.impl.ConversionServiceConverterManager;
import org.springframework.ldap.support.LdapUtils;

import javax.naming.Name;

import static org.assertj.core.api.Assertions.assertThat;

public class ConversionServiceConverterManagerTest {
    @Test
    void configuresAnIdentifierOnlyEntryWithTheDefaultManager() {
        DefaultObjectDirectoryMapper mapper = new DefaultObjectDirectoryMapper();
        mapper.setConverterManager(new ConversionServiceConverterManager());
        Identifier entry = new Identifier();
        Name dn = LdapUtils.newLdapName("ou=engineering");
        mapper.setId(entry, dn);
        DirContextAdapter attributes = new DirContextAdapter(dn);
        mapper.mapToLdapDataEntry(entry, attributes);
        assertThat(mapper.getId(entry)).isEqualTo(dn);
        assertThat(attributes.getStringAttributes("objectclass")).containsExactly("organizationalUnit");
    }

    @Test
    void delegatesValueConversionToTheConfiguredService() {
        ConversionServiceConverterManager manager = new ConversionServiceConverterManager(new DefaultConversionService());
        assertThat(manager.canConvert(String.class, "", Integer.class)).isTrue();
        assertThat(manager.convert("42", "", Integer.class)).isEqualTo(42);
    }

    @Entry(objectClasses = "organizationalUnit")
    public static final class Identifier {
        @Id
        private Name id;

        public Identifier() {
        }
    }
}
