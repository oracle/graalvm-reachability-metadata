/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdiff_tests;

import jdiff.DiffMyers;
import jdiff.DiffMyers.change;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DiffMyersTest {
    @Test
    void producesNoEditsForEqualInputs() {
        assertNull(new DiffMyers(new String[]{"alpha", "beta"}, new String[]{"alpha", "beta"}).diff_2(false));
        assertNull(new DiffMyers(new String[0], new String[0]).diff_2(false));
    }

    @Test
    void describesAnInsertionBetweenUnchangedLines() {
        change edit = new DiffMyers(new String[]{"start", "end"},
                new String[]{"start", "middle", "end"}).diff_2(false);

        assertEdit(edit, 1, 1, 0, 1);
        assertNull(edit.link);
    }

    @Test
    void describesADeletionBetweenUnchangedLines() {
        change edit = new DiffMyers(new String[]{"start", "middle", "end"},
                new String[]{"start", "end"}).diff_2(false);

        assertEdit(edit, 1, 1, 1, 0);
        assertNull(edit.link);
    }

    @Test
    void describesAReplacement() {
        change edit = new DiffMyers(new String[]{"start", "old", "end"},
                new String[]{"start", "new", "extra", "end"}).diff_2(false);

        assertEdit(edit, 1, 1, 1, 2);
        assertNull(edit.link);
    }

    @Test
    void handlesAnEntirelyAddedOrRemovedInput() {
        change added = new DiffMyers(new String[0], new String[]{"alpha", "beta"}).diff_2(false);
        assertEdit(added, 0, 0, 0, 2);
        assertNull(added.link);

        change removed = new DiffMyers(new String[]{"alpha", "beta"}, new String[0]).diff_2(false);
        assertEdit(removed, 0, 0, 2, 0);
        assertNull(removed.link);
    }

    @Test
    void ordersSeparatedChangesInForwardAndReverseScripts() {
        String[] before = {"head", "old", "anchor", "tail"};
        String[] after = {"head", "new", "anchor", "tail", "extra"};
        DiffMyers forwardDiff = new DiffMyers(before, after);
        forwardDiff.no_discards = true;
        change forward = forwardDiff.diff_2(false);

        assertEdit(forward, 1, 1, 1, 1);
        assertEdit(forward.link, 4, 4, 0, 1);
        assertNull(forward.link.link);

        DiffMyers reverseDiff = new DiffMyers(before, after);
        reverseDiff.heuristic = true;
        change reverse = reverseDiff.diff_2(true);

        assertEdit(reverse, 4, 4, 0, 1);
        assertEdit(reverse.link, 1, 1, 1, 1);
        assertNull(reverse.link.link);
    }

    private static void assertEdit(change edit, int oldLine, int newLine, int deleted, int inserted) {
        assertNotNull(edit);
        assertEquals(oldLine, edit.line0);
        assertEquals(newLine, edit.line1);
        assertEquals(deleted, edit.deleted);
        assertEquals(inserted, edit.inserted);
    }
}
