/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.BasicDBObject;
import org.junit.jupiter.api.Test;
import org.mongodb.morphia.mapping.DefaultCreator;
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
}
