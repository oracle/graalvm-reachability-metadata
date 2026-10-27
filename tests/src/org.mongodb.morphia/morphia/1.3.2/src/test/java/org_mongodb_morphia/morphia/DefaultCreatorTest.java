/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import java.util.ArrayList;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.junit.jupiter.api.Test;
import org.mongodb.morphia.annotations.ConstructorArgs;
import org.mongodb.morphia.mapping.DefaultCreator;
import org.mongodb.morphia.mapping.MappedClass;
import org.mongodb.morphia.mapping.MappedField;
import org.mongodb.morphia.mapping.Mapper;
import org.mongodb.morphia.mapping.MapperOptions;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultCreatorTest {
    @Test
    void createsInstanceUsingDiscriminatorClassNameWithoutLookupCache() {
        DefaultCreator creator = new DefaultCreator();
        DBObject dbObject = dbObjectWithClassName(DefaultCreatorDiscriminatedPojo.class);

        DefaultCreatorNoArgsPojo instance = creator.createInstance(DefaultCreatorNoArgsPojo.class, dbObject);

        assertThat(instance).isInstanceOf(DefaultCreatorDiscriminatedPojo.class);
        assertThat(((DefaultCreatorDiscriminatedPojo) instance).value()).isEqualTo("created");
    }

    @Test
    void createsInstanceUsingDiscriminatorClassNameWithLookupCache() {
        MapperOptions options = new MapperOptions();
        options.setCacheClassLookups(true);
        DefaultCreator creator = new DefaultCreator(options);
        DBObject dbObject = dbObjectWithClassName(DefaultCreatorDiscriminatedPojo.class);

        DefaultCreatorNoArgsPojo instance = creator.createInstance(DefaultCreatorNoArgsPojo.class, dbObject);

        assertThat(instance).isInstanceOf(DefaultCreatorDiscriminatedPojo.class);
        assertThat(creator.getClassNameCache()).containsEntry(DefaultCreatorDiscriminatedPojo.class.getName(),
                DefaultCreatorDiscriminatedPojo.class);
    }

    @Test
    void createsInstanceUsingConstructorArgsMappedField() {
        Mapper mapper = new Mapper();
        MappedClass mappedClass = mapper.getMappedClass(DefaultCreatorConstructorArgHolder.class);
        MappedField mappedField = mappedClass.getMappedFieldByJavaField("value");
        DefaultCreator creator = new DefaultCreator();
        DBObject dbObject = new BasicDBObject("name", "Ada").append("score", 42);

        Object instance = creator.createInstance(mapper, mappedField, dbObject);

        assertThat(instance).isInstanceOf(DefaultCreatorConstructorArgPojo.class);
        DefaultCreatorConstructorArgPojo pojo = (DefaultCreatorConstructorArgPojo) instance;
        assertThat(pojo.name()).isEqualTo("Ada");
        assertThat(pojo.score()).isEqualTo(42);
    }

    @Test
    void createsCollectionUsingMappedFieldConstructor() {
        Mapper mapper = new Mapper();
        MappedClass mappedClass = mapper.getMappedClass(DefaultCreatorCollectionHolder.class);
        MappedField mappedField = mappedClass.getMappedFieldByJavaField("values");
        DefaultCreator creator = new DefaultCreator();

        Object instance = creator.createList(mappedField);

        assertThat(instance).isInstanceOf(DefaultCreatorCustomList.class);
    }

    private static DBObject dbObjectWithClassName(final Class<?> type) {
        return new BasicDBObject(Mapper.CLASS_NAME_FIELDNAME, type.getName());
    }
}

class DefaultCreatorNoArgsPojo {
    DefaultCreatorNoArgsPojo() {
    }
}

class DefaultCreatorDiscriminatedPojo extends DefaultCreatorNoArgsPojo {
    private DefaultCreatorDiscriminatedPojo() {
    }

    String value() {
        return "created";
    }
}

class DefaultCreatorConstructorArgHolder {
    @ConstructorArgs({"name", "score"})
    DefaultCreatorConstructorArgPojo value;
}

class DefaultCreatorConstructorArgPojo {
    private final String name;
    private final Integer score;

    private DefaultCreatorConstructorArgPojo(final String name, final Integer score) {
        this.name = name;
        this.score = score;
    }

    String name() {
        return name;
    }

    Integer score() {
        return score;
    }
}

class DefaultCreatorCollectionHolder {
    DefaultCreatorCustomList values;
}

class DefaultCreatorCustomList extends ArrayList<String> {
    private DefaultCreatorCustomList() {
    }
}
