/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.ClassModel;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

public class PropertyReflectionUtilsTest {
    @Test
    void classModelIncludesDefaultGetterDeclaredOnImplementedInterface() {
        final ClassModel<DefaultMethodPojo> classModel = ClassModel.builder(DefaultMethodPojo.class).build();

        assertThat(classModel.getPropertyModel("label")).isNotNull();
    }

    @Test
    void pojoCodecEncodesValueReadThroughDefaultInterfaceGetter() {
        final BsonDocument document = new BsonDocument();
        final Codec<DefaultMethodPojo> codec = codecFor(DefaultMethodPojo.class);

        codec.encode(new BsonDocumentWriter(document), new DefaultMethodPojo(), EncoderContext.builder().build());

        assertThat(document.getString("label").getValue()).isEqualTo("interface-default");
    }

    private static <T> Codec<T> codecFor(final Class<T> type) {
        final PojoCodecProvider pojoCodecProvider = PojoCodecProvider.builder().register(type).build();
        final CodecRegistry registry = fromProviders(new ValueCodecProvider(), pojoCodecProvider);
        return registry.get(type);
    }

    public interface DefaultLabel {
        default String getLabel() {
            return "interface-default";
        }
    }

    public static final class DefaultMethodPojo implements DefaultLabel {
        public DefaultMethodPojo() {
        }
    }
}
