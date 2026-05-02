/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongo_java_driver;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonInt32;
import org.bson.BsonString;
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
    void decodesWithAnnotatedNoArgumentConstructor() {
        final NoArgumentConstructorPojo decoded = decode(NoArgumentConstructorPojo.class, new BsonDocument());

        assertThat(decoded.getSource()).isEqualTo("constructor");
    }

    @Test
    void decodesWithAnnotatedConstructorParameters() {
        final BsonDocument document = new BsonDocument()
                .append("name", new BsonString("alpha"))
                .append("count", new BsonInt32(7));

        final ConstructorParametersPojo decoded = decode(ConstructorParametersPojo.class, document);

        assertThat(decoded.getName()).isEqualTo("alpha");
        assertThat(decoded.getCount()).isEqualTo(7);
    }

    @Test
    void decodesWithAnnotatedNoArgumentStaticFactory() {
        final NoArgumentFactoryPojo decoded = decode(NoArgumentFactoryPojo.class, new BsonDocument());

        assertThat(decoded.getSource()).isEqualTo("factory");
    }

    @Test
    void decodesWithAnnotatedStaticFactoryParameters() {
        final BsonDocument document = new BsonDocument()
                .append("value", new BsonString("bravo"))
                .append("quantity", new BsonInt32(11));

        final FactoryParametersPojo decoded = decode(FactoryParametersPojo.class, document);

        assertThat(decoded.getValue()).isEqualTo("bravo");
        assertThat(decoded.getQuantity()).isEqualTo(11);
    }

    private static <T> T decode(final Class<T> type, final BsonDocument document) {
        final PojoCodecProvider pojoCodecProvider = PojoCodecProvider.builder().register(type).build();
        final CodecRegistry registry = fromProviders(new ValueCodecProvider(), pojoCodecProvider);
        final Codec<T> codec = registry.get(type);

        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    public static final class NoArgumentConstructorPojo {
        private final String source;

        @BsonCreator
        public NoArgumentConstructorPojo() {
            this.source = "constructor";
        }

        public String getSource() {
            return source;
        }
    }

    public static final class ConstructorParametersPojo {
        private String name;
        private int count;

        @BsonCreator
        public ConstructorParametersPojo(@BsonProperty("name") final String name,
                                         @BsonProperty("count") final int count) {
            this.name = name;
            this.count = count;
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

    public static final class FactoryParametersPojo {
        private String value;
        private Integer quantity;

        private FactoryParametersPojo(final String value, final Integer quantity) {
            this.value = value;
            this.quantity = quantity;
        }

        @BsonCreator
        public static FactoryParametersPojo from(@BsonProperty("value") final String value,
                                                 @BsonProperty("quantity") final Integer quantity) {
            return new FactoryParametersPojo(value, quantity);
        }

        public String getValue() {
            return value;
        }

        public void setValue(final String value) {
            this.value = value;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(final Integer quantity) {
            this.quantity = quantity;
        }
    }
}
