/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.BasicDBObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mongodb.morphia.annotations.ConstructorArgs;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.mapping.DefaultCreator;
import org.mongodb.morphia.mapping.MappedClass;
import org.mongodb.morphia.mapping.MappedField;
import org.mongodb.morphia.mapping.Mapper;
import org.mongodb.morphia.mapping.MapperOptions;

public class DefaultCreatorTest {
    @Test
    void createInstanceUsesPrivateNoArgsConstructor() {
        DefaultCreator creator = new DefaultCreator();

        PrivateNoArgsFixture fixture = creator.createInstance(PrivateNoArgsFixture.class);

        assertThat(fixture.value).isEqualTo("created");
    }

    @Test
    void createInstanceResolvesClassNameFromDBObject() {
        DefaultCreator creator = new DefaultCreator();
        BasicDBObject dbObject = new BasicDBObject(Mapper.CLASS_NAME_FIELDNAME, ClassNameFixture.class.getName());

        Object fixture = creator.createInstance(Object.class, dbObject);

        assertThat(fixture).isInstanceOf(ClassNameFixture.class);
    }

    @Test
    void createInstanceCachesResolvedClassNamesWhenEnabled() {
        MapperOptions options = new MapperOptions();
        options.setCacheClassLookups(true);
        DefaultCreator creator = new DefaultCreator(options);
        String className = CachedClassNameFixture.class.getName();
        BasicDBObject dbObject = new BasicDBObject(Mapper.CLASS_NAME_FIELDNAME, className);

        Object fixture = creator.createInstance(Object.class, dbObject);

        assertThat(fixture).isInstanceOf(CachedClassNameFixture.class);
        assertThat(creator.getClassNameCache()).containsEntry(className, CachedClassNameFixture.class);
    }

    @Test
    void createInstanceUsesConstructorArgsAnnotationForValueObjects() {
        DefaultCreator creator = new DefaultCreator();
        MappedField mappedField = mappedField(ConstructorArgsHolder.class, "value");
        BasicDBObject dbObject = new BasicDBObject("name", "morphia");

        Object value = creator.createInstance(new Mapper(), mappedField, dbObject);

        assertThat(value).isInstanceOfSatisfying(ConstructorArgValue.class,
                constructed -> assertThat(constructed.name).isEqualTo("morphia"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createListUsesMappedFieldConstructorWhenAvailable() {
        DefaultCreator creator = new DefaultCreator();
        MappedField mappedField = mappedField(CollectionHolder.class, "values");

        List<String> values = creator.createList(mappedField);

        assertThat(values).isInstanceOf(CustomStringList.class)
                .containsExactly("constructed");
    }

    private static MappedField mappedField(Class<?> holderType, String fieldName) {
        MappedClass mappedClass = new Mapper().addMappedClass(holderType);
        MappedField mappedField = mappedClass.getMappedFieldByJavaField(fieldName);
        assertThat(mappedField).isNotNull();
        return mappedField;
    }

    public static class PrivateNoArgsFixture {
        private final String value;

        private PrivateNoArgsFixture() {
            value = "created";
        }
    }

    public static class ClassNameFixture {
        private ClassNameFixture() {
        }
    }

    public static class CachedClassNameFixture {
        private CachedClassNameFixture() {
        }
    }

    @Embedded
    public static class ConstructorArgsHolder {
        @ConstructorArgs("name")
        private ConstructorArgValue value;
    }

    public static class ConstructorArgValue {
        private final String name;

        private ConstructorArgValue(String name) {
            this.name = name;
        }
    }

    @Embedded
    public static class CollectionHolder {
        private CustomStringList values;
    }

    public static class CustomStringList extends ArrayList<String> {
        private static final long serialVersionUID = 1L;

        private CustomStringList() {
            add("constructed");
        }
    }
}
