/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mapstruct.mapstruct;

import org.junit.jupiter.api.Test;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

public class MappersTest {
    @Test
    void getMapperInstantiatesGeneratedMapperImplementation() {
        PersonMapper mapper = Mappers.getMapper(PersonMapper.class);

        PersonDto dto = new PersonDto();
        dto.setGivenName("Ada");
        dto.setFamilyName("Lovelace");
        dto.setAge(36);

        Person person = mapper.toPerson(dto);

        assertThat(person.getFirstName()).isEqualTo("Ada");
        assertThat(person.getLastName()).isEqualTo("Lovelace");
        assertThat(person.getAge()).isEqualTo(36);
    }

    @Test
    void getMapperClassLoadsGeneratedMapperImplementationClass() {
        Class<? extends PersonMapper> mapperClass = Mappers.getMapperClass(PersonMapper.class);

        assertThat(PersonMapper.class.isAssignableFrom(mapperClass)).isTrue();
        assertThat(mapperClass.getName()).endsWith("PersonMapperImpl");
    }
}

@Mapper
interface PersonMapper {
    @Mapping(source = "givenName", target = "firstName")
    @Mapping(source = "familyName", target = "lastName")
    Person toPerson(PersonDto person);
}

class PersonDto {
    private String givenName;
    private String familyName;
    private int age;

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}

class Person {
    private String firstName;
    private String lastName;
    private int age;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
