/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class ClassNameIdResolverTest {
    @Test
    public void deserializesClassNameTypeId() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = """
                {"@class":"%s","name":"Fido"}
                """.formatted(Dog.class.getName());

        Animal animal = mapper.readValue(json, Animal.class);

        assertThat(animal).isInstanceOf(Dog.class);
        assertThat(((Dog) animal).name).isEqualTo("Fido");
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
    public abstract static class Animal {
    }

    public static class Dog extends Animal {
        public String name;
    }
}
