/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import org.junit.jupiter.api.Test;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.odm.annotations.Attribute;
import org.springframework.ldap.odm.annotations.Entry;
import org.springframework.ldap.odm.annotations.Id;
import org.springframework.ldap.odm.core.impl.DefaultObjectDirectoryMapper;
import org.springframework.ldap.support.LdapUtils;

import javax.naming.Name;
import java.util.List;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

public class AttributeMetaDataTest {
    @Test
    void mapsConcreteCollectionsAndBinaryArrayValues() {
        DefaultObjectDirectoryMapper mapper = new DefaultObjectDirectoryMapper();
        DirContextAdapter entry = new DirContextAdapter(LdapUtils.newLdapName("cn=archive"));
        entry.setAttributeValues("objectclass", new String[]{"top", "applicationProcess"});
        entry.setAttributeValues("description", new String[]{"second", "first"});
        byte[] photo = new byte[]{1, 3, 5};
        entry.setAttributeValues("jpegPhoto", new Object[]{photo});
        Archive archive = mapper.mapFromLdapDataEntry(entry, Archive.class);
        assertThat(archive.labels).isInstanceOf(TreeSet.class).containsExactly("first", "second");
        assertThat(archive.photos).hasSize(1);
        assertThat(archive.photos.get(0)).containsExactly(photo);
        DirContextAdapter written = new DirContextAdapter(archive.id);
        mapper.mapToLdapDataEntry(archive, written);
        assertThat(written.getStringAttributes("description")).containsExactly("first", "second");
        assertThat((byte[]) written.getObjectAttribute("jpegPhoto")).containsExactly(photo);
    }

    @Entry(objectClasses = {"top", "applicationProcess"})
    public static final class Archive {
        @Id
        private Name id;
        @Attribute(name = "description")
        private TreeSet<String> labels;
        @Attribute(name = "jpegPhoto", type = Attribute.Type.BINARY)
        private List<byte[]> photos;

        public Archive() {
        }
    }
}
