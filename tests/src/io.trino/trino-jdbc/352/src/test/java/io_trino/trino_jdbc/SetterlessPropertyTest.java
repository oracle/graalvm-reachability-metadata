/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SetterlessPropertyTest {
    @Test
    void populatesGetterOnlyCollection() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Playlist playlist = mapper.readValue("{\"songs\":[\"intro\",\"finale\"]}", Playlist.class);
        assertEquals(List.of("intro", "finale"), playlist.getSongs());
    }

    public static class Playlist {
        private final List<String> entries = new ArrayList<>();

        public List<String> getSongs() {
            return entries;
        }
    }
}
