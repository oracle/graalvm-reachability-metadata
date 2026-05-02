/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongo_java_driver;

import com.mongodb.MongoClient;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.json.JsonReader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class CreatorExecutableTest {
    @Test
    void decodesPojoWithPublicNoArgumentConstructor() {
        final DefaultConstructedPojo pojo = decode(DefaultConstructedPojo.class, "{ 'name': 'default', 'count': 7 }");

        assertThat(pojo.getName()).isEqualTo("default");
        assertThat(pojo.getCount()).isEqualTo(7);
    }

    @Test
    void decodesPojoWithNoArgumentCreatorMethod() {
        final NoArgumentFactoryPojo pojo = decode(NoArgumentFactoryPojo.class, "{ 'name': 'factory', 'count': 11 }");

        assertThat(pojo.getName()).isEqualTo("factory");
        assertThat(pojo.getCount()).isEqualTo(11);
        assertThat(pojo.isCreatedByFactory()).isTrue();
    }

    @Test
    void decodesPojoWithCreatorConstructorParameters() {
        final ConstructorCreatedPojo pojo = decode(
                ConstructorCreatedPojo.class, "{ 'name': 'constructor', 'count': 13 }");

        assertThat(pojo.getName()).isEqualTo("constructor");
        assertThat(pojo.getCount()).isEqualTo(13);
    }

    @Test
    void decodesPojoWithCreatorMethodParameters() {
        final MethodCreatedPojo pojo = decode(MethodCreatedPojo.class, "{ 'name': 'method', 'count': 17 }");

        assertThat(pojo.getName()).isEqualTo("method");
        assertThat(pojo.getCount()).isEqualTo(17);
        assertThat(pojo.isCreatedByFactory()).isTrue();
    }

    private static <T> T decode(final Class<T> pojoClass, final String json) {
        final CodecRegistry registry = fromRegistries(
                MongoClient.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().register(pojoClass).build()));
        final Codec<T> codec = registry.get(pojoClass);
        return codec.decode(new JsonReader(json), DecoderContext.builder().build());
    }

    public static class DefaultConstructedPojo {
        private String name;
        private int count;

        public DefaultConstructedPojo() {
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(final int count) {
            this.count = count;
        }
    }

    public static class NoArgumentFactoryPojo {
        private String name;
        private int count;
        private boolean createdByFactory;

        public NoArgumentFactoryPojo() {
        }

        @BsonCreator
        public static NoArgumentFactoryPojo create() {
            final NoArgumentFactoryPojo pojo = new NoArgumentFactoryPojo();
            pojo.createdByFactory = true;
            return pojo;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(final int count) {
            this.count = count;
        }

        public boolean isCreatedByFactory() {
            return createdByFactory;
        }
    }

    public static class ConstructorCreatedPojo {
        private final String name;
        private final int count;

        @BsonCreator
        public ConstructorCreatedPojo(@BsonProperty("name") final String name, @BsonProperty("count") final int count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }
    }

    public static class MethodCreatedPojo {
        private final String name;
        private final int count;
        private final boolean createdByFactory;

        public MethodCreatedPojo() {
            this(null, 0, false);
        }

        private MethodCreatedPojo(final String name, final int count, final boolean createdByFactory) {
            this.name = name;
            this.count = count;
            this.createdByFactory = createdByFactory;
        }

        @BsonCreator
        public static MethodCreatedPojo create(@BsonProperty("name") final String name,
                                               @BsonProperty("count") final int count) {
            return new MethodCreatedPojo(name, count, true);
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }

        public boolean isCreatedByFactory() {
            return createdByFactory;
        }
    }
}
