/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_serialization_jackson_3;

import akka.actor.Address;
import akka.actor.AddressFromURIString$;
import akka.serialization.jackson.AkkaJacksonModule;
import akka.serialization.jackson.JacksonMigration;
import akka.serialization.jackson.VersionExtractor$;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import scala.Option;
import scala.Tuple2;
import scala.concurrent.duration.Duration$;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AkkaSerializationJackson3Test {

    @Test
    void akkaJacksonModuleRoundTripsAddressesWithHostsAndPorts() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new AkkaJacksonModule());

        Address address = AddressFromURIString$.MODULE$.apply("akka://inventory@127.0.0.1:25520");
        String json = mapper.writeValueAsString(address);
        Address restored = mapper.readValue(json, Address.class);

        assertThat(restored).isEqualTo(address);
        assertThat(json).contains("inventory");
        assertThat(json).contains("127.0.0.1");
    }

    @Test
    void akkaJacksonModuleRoundTripsAddressesWithoutHosts() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new AkkaJacksonModule());

        Address address = new Address("akka", "cluster");
        String json = mapper.writeValueAsString(address);
        Address restored = mapper.readValue(json, Address.class);

        assertThat(restored).isEqualTo(address);
        assertThat(json).contains("cluster");
    }

    @Test
    void akkaJacksonModuleRoundTripsFiniteDurations() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new AkkaJacksonModule());

        FiniteDuration duration = Duration$.MODULE$.create(250L, TimeUnit.MILLISECONDS);
        String json = mapper.writeValueAsString(duration);
        FiniteDuration restored = mapper.readValue(json, FiniteDuration.class);

        assertThat(restored).isEqualTo(duration);
    }

    @Test
    void jacksonMigrationCanRewriteLegacyFieldNames() throws Exception {
        JsonNode json = new ObjectMapper().readTree("{\"oldName\":\"Ada Lovelace\"}");
        JsonNode restored = new RenameOldNameMigration().transform(1, json);

        assertThat(restored.get("newName").asText()).isEqualTo("Ada Lovelace");
        assertThat(restored.has("oldName")).isFalse();
    }

    @Test
    void versionExtractorReturnsMajorAndMinorComponents() {
        Version version = new Version(2, 13, 2, null, "com.typesafe.akka", "akka-serialization-jackson_3");
        Option<Tuple2<Object, Object>> extracted = VersionExtractor$.MODULE$.unapply(version);

        assertThat(extracted.isDefined()).isTrue();
        Tuple2<Object, Object> tuple = extracted.get();
        assertThat(tuple._1()).isEqualTo(2);
        assertThat(tuple._2()).isEqualTo(13);
    }

    static final class RenameOldNameMigration extends JacksonMigration {
        @Override
        public int currentVersion() {
            return 2;
        }

        @Override
        public JsonNode transform(int fromVersion, JsonNode json) {
            if (fromVersion == 1) {
                ObjectNode objectNode = json.deepCopy();
                JsonNode oldName = objectNode.remove("oldName");
                objectNode.put("newName", oldName.asText());
                return objectNode;
            }
            return json;
        }
    }
}
