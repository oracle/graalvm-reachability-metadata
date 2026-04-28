/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

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

public class CreatorExecutableTest {
    private static final CodecRegistry CODEC_REGISTRY = fromRegistries(
            fromProviders(new ValueCodecProvider()),
            fromProviders(PojoCodecProvider.builder().automatic(true).build()));

    @Test
    void decodesWithNoArgumentConstructor() {
        NoArgumentConstructorPojo decoded = decode(document("constructor-name", 11), NoArgumentConstructorPojo.class);

        assertThat(decoded.getName()).isEqualTo("constructor-name");
        assertThat(decoded.getScore()).isEqualTo(11);
    }

    @Test
    void decodesWithNoArgumentFactoryMethod() {
        NoArgumentFactoryPojo decoded = decode(document("factory-name", 22), NoArgumentFactoryPojo.class);

        assertThat(decoded.getName()).isEqualTo("factory-name");
        assertThat(decoded.getScore()).isEqualTo(22);
        assertThat(decoded.getCreationPath()).isEqualTo("factory");
    }

    @Test
    void decodesWithAnnotatedConstructorArguments() {
        ConstructorWithArgumentsPojo decoded = decode(
                document("argument-constructor-name", 33), ConstructorWithArgumentsPojo.class);

        assertThat(decoded.getName()).isEqualTo("argument-constructor-name");
        assertThat(decoded.getScore()).isEqualTo(33);
    }

    @Test
    void decodesWithAnnotatedFactoryMethodArguments() {
        FactoryWithArgumentsPojo decoded = decode(
                document("argument-factory-name", 44), FactoryWithArgumentsPojo.class);

        assertThat(decoded.getName()).isEqualTo("argument-factory-name");
        assertThat(decoded.getScore()).isEqualTo(44);
        assertThat(decoded.getCreationPath()).isEqualTo("factory-with-arguments");
    }

    private static BsonDocument document(String name, int score) {
        BsonDocument document = new BsonDocument();
        document.append("name", new BsonString(name));
        document.append("score", new BsonInt32(score));
        return document;
    }

    private static <T> T decode(BsonDocument document, Class<T> type) {
        Codec<T> codec = CODEC_REGISTRY.get(type);
        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    public static class NoArgumentConstructorPojo {
        private String name;
        private int score;

        public NoArgumentConstructorPojo() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }
    }

    public static class NoArgumentFactoryPojo {
        private String creationPath;
        private String name;
        private int score;

        private NoArgumentFactoryPojo(String creationPath) {
            this.creationPath = creationPath;
        }

        @BsonCreator
        public static NoArgumentFactoryPojo create() {
            return new NoArgumentFactoryPojo("factory");
        }

        public String getCreationPath() {
            return creationPath;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }
    }

    public static class ConstructorWithArgumentsPojo {
        private final String name;
        private final int score;

        @BsonCreator
        public ConstructorWithArgumentsPojo(@BsonProperty("name") String name, @BsonProperty("score") int score) {
            this.name = name;
            this.score = score;
        }

        public String getName() {
            return name;
        }

        public int getScore() {
            return score;
        }
    }

    public static class FactoryWithArgumentsPojo {
        private final String creationPath;
        private final String name;
        private final int score;

        private FactoryWithArgumentsPojo(String creationPath, String name, int score) {
            this.creationPath = creationPath;
            this.name = name;
            this.score = score;
        }

        @BsonCreator
        public static FactoryWithArgumentsPojo create(
                @BsonProperty("name") String name, @BsonProperty("score") int score) {
            return new FactoryWithArgumentsPojo("factory-with-arguments", name, score);
        }

        public String getCreationPath() {
            return creationPath;
        }

        public String getName() {
            return name;
        }

        public int getScore() {
            return score;
        }
    }
}
