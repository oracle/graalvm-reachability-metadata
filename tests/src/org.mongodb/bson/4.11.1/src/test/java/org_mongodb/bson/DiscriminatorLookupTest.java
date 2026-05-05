/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

public class DiscriminatorLookupTest {
    @Test
    void decodesPojoWithFullyQualifiedDiscriminatorClassName() {
        final BsonDocument document = new BsonDocument()
                .append("_t", new BsonString(SubPojo.class.getName()))
                .append("name", new BsonString("fixture"))
                .append("detail", new BsonString("loaded-by-discriminator"));
        final PojoCodecProvider pojoCodecProvider = PojoCodecProvider.builder()
                .automatic(true)
                .register(BasePojo.class)
                .build();
        final CodecRegistry registry = fromProviders(new ValueCodecProvider(), pojoCodecProvider);
        final Codec<BasePojo> codec = registry.get(BasePojo.class);

        final BasePojo decoded = codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());

        assertThat(decoded).isInstanceOf(SubPojo.class);
        assertThat(decoded.getName()).isEqualTo("fixture");
        assertThat(((SubPojo) decoded).getDetail()).isEqualTo("loaded-by-discriminator");
    }

    @BsonDiscriminator
    public static class BasePojo {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }

    public static final class SubPojo extends BasePojo {
        private String detail;

        public String getDetail() {
            return detail;
        }

        public void setDetail(final String detail) {
            this.detail = detail;
        }
    }
}
