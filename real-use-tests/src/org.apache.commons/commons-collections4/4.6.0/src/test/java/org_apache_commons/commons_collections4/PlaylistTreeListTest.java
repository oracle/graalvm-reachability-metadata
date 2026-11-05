/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.list.TreeList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the list subsystem for maintaining an editable music playback queue.
 */
public class PlaylistTreeListTest {
    @Test
    @Timeout(60)
    public void editsQueuePositionsAndRendersTheCurrentPlaylist() {
        TreeList<String> playlist = new TreeList<>(List.of("intro", "first-song", "last-song"));

        playlist.add(2, "middle-song");
        playlist.set(0, "updated-intro");
        playlist.remove(playlist.indexOf("last-song"));
        playlist.addAll(List.of("encore", "outro"));

        assertEquals(5, playlist.size());
        assertEquals("middle-song", playlist.get(2));
        assertTrue(playlist.contains("encore"));
        assertFalse(playlist.contains("last-song"));
        assertEquals(List.of("updated-intro", "first-song", "middle-song", "encore", "outro"),
                List.copyOf(playlist));
    }
}
