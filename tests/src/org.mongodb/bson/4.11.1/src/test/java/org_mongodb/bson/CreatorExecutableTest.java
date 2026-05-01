/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

public class CreatorExecutableTest {
    @Test
    void decodesPojoWithDefaultConstructor() {
        final DefaultConstructorPojo pojo = decode(
                documentWithNameAndQuantity("default-constructor", 1), DefaultConstructorPojo.class);

        assertThat(pojo.getName()).isEqualTo("default-constructor");
        assertThat(pojo.getQuantity()).isEqualTo(1);
    }

    @Test
    void decodesPojoWithNoArgumentCreatorFactory() {
        final NoArgumentFactoryPojo pojo = decode(new BsonDocument(), NoArgumentFactoryPojo.class);

        assertThat(pojo.getSource()).isEqualTo("factory");
    }

    @Test
    void decodesPojoWithCreatorConstructorParameters() {
        final ConstructorParametersPojo pojo = decode(
                documentWithNameAndQuantity("constructor", 2), ConstructorParametersPojo.class);

        assertThat(pojo.getName()).isEqualTo("constructor");
        assertThat(pojo.getQuantity()).isEqualTo(2);
    }

    @Test
    void decodesPojoWithCreatorFactoryParameters() {
        final FactoryParametersPojo pojo = decode(
                documentWithNameAndQuantity("factory", 3), FactoryParametersPojo.class);

        assertThat(pojo.getName()).isEqualTo("factory");
        assertThat(pojo.getQuantity()).isEqualTo(3);
    }

    private static BsonDocument documentWithNameAndQuantity(final String name, final int quantity) {
        return new BsonDocument()
                .append("name", new BsonString(name))
                .append("quantity", new BsonInt32(quantity));
    }

    private static <T> T decode(final BsonDocument document, final Class<T> pojoClass) {
        final CodecRegistry registry = fromProviders(
                new ValueCodecProvider(),
                new BsonValueCodecProvider(),
                PojoCodecProvider.builder().register(pojoClass).build());
        final Codec<T> codec = registry.get(pojoClass);
        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    public static final class DefaultConstructorPojo {
        private String name;
        private Integer quantity;

        public DefaultConstructorPojo() {
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(final Integer quantity) {
            this.quantity = quantity;
        }
    }

    public static final class NoArgumentFactoryPojo {
        private final String source;

        private NoArgumentFactoryPojo(final String source) {
            this.source = source;
        }

        @BsonCreator
        public static NoArgumentFactoryPojo create() {
            return new NoArgumentFactoryPojo("factory");
        }

        public String getSource() {
            return source;
        }
    }

    public static final class ConstructorParametersPojo {
        private final String name;
        private final Integer quantity;

        @BsonCreator
        public ConstructorParametersPojo(
                @BsonProperty("name") final String name,
                @BsonProperty("quantity") final Integer quantity) {
            this.name = name;
            this.quantity = quantity;
        }

        public String getName() {
            return name;
        }

        public Integer getQuantity() {
            return quantity;
        }
    }

    public static final class FactoryParametersPojo {
        private final String name;
        private final Integer quantity;

        private FactoryParametersPojo(final String name, final Integer quantity) {
            this.name = name;
            this.quantity = quantity;
        }

        @BsonCreator
        public static FactoryParametersPojo create(
                @BsonProperty("name") final String name,
                @BsonProperty("quantity") final Integer quantity) {
            return new FactoryParametersPojo(name, quantity);
        }

        public String getName() {
            return name;
        }

        public Integer getQuantity() {
            return quantity;
        }
    }
}
