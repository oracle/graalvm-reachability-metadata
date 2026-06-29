/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.junit.jupiter.api.Test;
import org.mongodb.morphia.annotations.ConstructorArgs;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.mapping.DefaultCreator;
import org.mongodb.morphia.mapping.MappedField;
import org.mongodb.morphia.mapping.Mapper;
import org.mongodb.morphia.mapping.MapperOptions;
import org.mongodb.morphia.query.validation.ValidationFailure;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultCreatorTest {
    @Test
    public void createsInstancesUsingNoArgsConstructors() {
        final DefaultCreator creator = new DefaultCreator();

        final MapperOptions options = creator.createInstance(MapperOptions.class);

        assertThat(options).isNotNull();
    }

    @Test
    public void resolvesClassNamesWithoutLookupCaching() {
        final DefaultCreator creator = new DefaultCreator();
        final DBObject document = new BasicDBObject(Mapper.CLASS_NAME_FIELDNAME, MapperOptions.class.getName());

        final Object created = creator.createInstance(Object.class, document);

        assertThat(created).isInstanceOf(MapperOptions.class);
    }

    @Test
    public void resolvesClassNamesWithLookupCaching() {
        final MapperOptions options = new MapperOptions();
        options.setCacheClassLookups(true);
        final DefaultCreator creator = new DefaultCreator(options);
        final DBObject document = new BasicDBObject(Mapper.CLASS_NAME_FIELDNAME, DefaultCreator.class.getName());

        final Object created = creator.createInstance(Object.class, document);

        assertThat(created).isInstanceOf(DefaultCreator.class);
        assertThat(creator.getClassNameCache()).containsEntry(DefaultCreator.class.getName(), DefaultCreator.class);
    }

    @Test
    public void createsConstructorArgsFieldsFromStoredValues() {
        final Mapper mapper = new Mapper();
        final MappedField mappedField = mapper.getMappedClass(ConstructorArgsHolder.class)
                .getMappedFieldByJavaField("failure");
        final DefaultCreator creator = new DefaultCreator();
        final DBObject document = new BasicDBObject("message", "name is required");

        final Object created = creator.createInstance(mapper, mappedField, document);

        assertThat(created).isInstanceOf(ValidationFailure.class);
        assertThat(created).hasToString("Validation failed: 'name is required'");
    }

    @Test
    public void createsCollectionsUsingMappedConcreteConstructors() {
        final Mapper mapper = new Mapper();
        final MappedField mappedField = mapper.getMappedClass(CollectionHolder.class)
                .getMappedFieldByJavaField("names");
        final DefaultCreator creator = new DefaultCreator();

        final List<?> created = creator.createList(mappedField);

        assertThat(created).isInstanceOf(ArrayList.class);
        assertThat(created).isEmpty();
    }

    public static final class ConstructorArgsHolder {
        @Embedded
        @ConstructorArgs("message")
        private ValidationFailure failure;
    }

    public static final class CollectionHolder {
        @Embedded(concreteClass = ArrayList.class)
        private List<String> names;
    }
}
