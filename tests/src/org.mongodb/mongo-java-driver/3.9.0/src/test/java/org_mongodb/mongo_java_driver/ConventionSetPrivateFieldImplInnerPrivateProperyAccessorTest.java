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
import org.bson.codecs.pojo.Convention;
import org.bson.codecs.pojo.Conventions;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.json.JsonReader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class ConventionSetPrivateFieldImplInnerPrivateProperyAccessorTest {
    @Test
    void decodesPrivateFieldWithoutSetterWhenConventionIsEnabled() {
        final PrivateFieldOnlyPojo pojo = decode(PrivateFieldOnlyPojo.class, "{ 'name': 'decoded-private-field' }");

        assertThat(pojo.getName()).isEqualTo("decoded-private-field");
    }

    private static <T> T decode(final Class<T> pojoClass, final String json) {
        final CodecRegistry registry = fromRegistries(
                MongoClient.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder()
                        .conventions(privateFieldConventions())
                        .register(pojoClass)
                        .build()));
        final Codec<T> codec = registry.get(pojoClass);
        return codec.decode(new JsonReader(json), DecoderContext.builder().build());
    }

    private static List<Convention> privateFieldConventions() {
        return asList(
                Conventions.CLASS_AND_PROPERTY_CONVENTION,
                Conventions.ANNOTATION_CONVENTION,
                Conventions.SET_PRIVATE_FIELDS_CONVENTION);
    }

    public static class PrivateFieldOnlyPojo {
        private String name;

        public PrivateFieldOnlyPojo() {
        }

        public String getName() {
            return name;
        }
    }
}
