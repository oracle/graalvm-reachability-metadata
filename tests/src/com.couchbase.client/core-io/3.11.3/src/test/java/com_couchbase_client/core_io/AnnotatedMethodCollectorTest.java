/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonProperty;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotatedMethodCollectorTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(Profile.class, ProfileMixin.class);
        Profile profile = mapper.readValue("""
                {"display_name":"Ada"}
                """, Profile.class);
        assertEquals("Ada", profile.getName());
        assertEquals("Ada", mapper.readTree(mapper.writeValueAsString(profile)).get("display_name").asText());
    }

    @Test
    void exposesInheritedObjectMethodThroughMixin() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(Object.class, ObjectMixin.class);
        Profile profile = new Profile();
        profile.setName("Lin");
        assertEquals(profile.hashCode(), mapper.readTree(mapper.writeValueAsString(profile))
                .get("identity").intValue());
    }

    public abstract static class ObjectMixin {
        @JsonProperty("identity")
        public abstract int hashCode();
    }

    public static class Profile {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public abstract static class ProfileMixin {
        @JsonProperty("display_name")
        public abstract String getName();

        @JsonProperty("display_name")
        public abstract void setName(String name);
    }
}
