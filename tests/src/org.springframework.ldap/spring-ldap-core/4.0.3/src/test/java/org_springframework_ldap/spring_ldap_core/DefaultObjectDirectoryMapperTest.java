/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.odm.annotations.Attribute;
import org.springframework.ldap.odm.annotations.DnAttribute;
import org.springframework.ldap.odm.annotations.Entry;
import org.springframework.ldap.odm.annotations.Id;
import org.springframework.ldap.odm.annotations.Transient;
import org.springframework.ldap.odm.core.ObjectDirectoryMapper;
import org.springframework.ldap.support.LdapUtils;

import javax.naming.Name;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultObjectDirectoryMapperTest {
    @Test
    void configuredMapperRoundTripsAnAnnotatedDirectoryEntry() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(beans);
        reader.loadBeanDefinitions(new ByteArrayResource("""
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://www.springframework.org/schema/beans
                         https://www.springframework.org/schema/beans/spring-beans.xsd">
                  <bean id="mapper" class="org.springframework.ldap.odm.core.impl.DefaultObjectDirectoryMapper"/>
                </beans>
                """.getBytes(StandardCharsets.UTF_8)));
        ObjectDirectoryMapper mapper = beans.getBean("mapper", ObjectDirectoryMapper.class);
        Person original = new Person();
        original.commonName = "Ada";
        original.surname = "Lovelace";
        original.descriptions = List.of("mathematician", "programmer");
        Name dn = LdapUtils.newLdapName("cn=Ada,ou=people");
        mapper.setId(original, dn);
        assertThat(mapper.getId(original)).isEqualTo(dn);
        assertThat(mapper.getCalculatedId(original)).isEqualTo(dn);
        assertThat(mapper.attributeFor(Person.class, "commonName")).isEqualTo("cn");

        DirContextAdapter entry = new DirContextAdapter(dn);
        mapper.mapToLdapDataEntry(original, entry);
        assertThat(entry.getStringAttribute("cn")).isEqualTo("Ada");
        assertThat(entry.getStringAttributes("description")).containsExactly("mathematician", "programmer");
        Person restored = mapper.mapFromLdapDataEntry(entry, Person.class);
        assertThat(restored.id).isEqualTo(dn);
        assertThat(restored.commonName).isEqualTo("Ada");
        assertThat(restored.surname).isEqualTo("Lovelace");
        assertThat(restored.descriptions).containsExactly("mathematician", "programmer");
        assertThat(restored.localNote).isNull();
    }

    @Entry(objectClasses = {"top", "person"}, base = "ou=people")
    public static final class Person {
        @Id
        private Name id;
        @Attribute(name = "cn")
        @DnAttribute(value = "cn", index = 1)
        private String commonName;
        @Attribute(name = "sn")
        private String surname;
        @Attribute(name = "description")
        private List<String> descriptions;
        @Transient
        private String localNote;

        public Person() {
        }
    }
}
