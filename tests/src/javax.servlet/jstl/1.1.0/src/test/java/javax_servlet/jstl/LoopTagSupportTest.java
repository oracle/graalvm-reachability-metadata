/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.jstl;

import java.util.Iterator;
import java.util.List;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.jstl.core.LoopTagStatus;
import javax.servlet.jsp.jstl.core.LoopTagSupport;
import javax.servlet.jsp.tagext.IterationTag;
import javax.servlet.jsp.tagext.Tag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoopTagSupportTest {
    @Test
    void iteratesItemsAndUpdatesTheLiveStatus() throws JspException {
        ListLoop tag = new ListLoop(List.of("apple", "pear", "plum"));
        try {
            assertEquals(Tag.EVAL_BODY_INCLUDE, tag.doStartTag());
            LoopTagStatus status = tag.getLoopStatus();
            assertEquals("apple", tag.getCurrent());
            assertEquals("apple", status.getCurrent());
            assertEquals(0, status.getIndex());
            assertEquals(1, status.getCount());
            assertTrue(status.isFirst());
            assertFalse(status.isLast());
            assertNull(status.getBegin());
            assertNull(status.getEnd());
            assertNull(status.getStep());

            assertEquals(IterationTag.EVAL_BODY_AGAIN, tag.doAfterBody());
            assertSame(status, tag.getLoopStatus());
            assertEquals("pear", status.getCurrent());
            assertEquals(1, status.getIndex());
            assertEquals(2, status.getCount());
            assertFalse(status.isFirst());
            assertFalse(status.isLast());

            assertEquals(IterationTag.EVAL_BODY_AGAIN, tag.doAfterBody());
            assertEquals("plum", status.getCurrent());
            assertEquals(2, status.getIndex());
            assertEquals(3, status.getCount());
            assertTrue(status.isLast());
            assertEquals(Tag.SKIP_BODY, tag.doAfterBody());
        } finally {
            tag.doFinally();
            tag.release();
        }
    }

    @Test
    void skipsTheBodyForAnEmptyCollection() throws JspException {
        ListLoop tag = new ListLoop(List.of());
        try {
            assertEquals(Tag.SKIP_BODY, tag.doStartTag());
        } finally {
            tag.doFinally();
            tag.release();
        }
    }

    @Test
    void appliesBeginEndAndStepToTheIteration() throws JspException {
        ListLoop tag = new ListLoop(List.of("a", "b", "c", "d", "e", "f"));
        tag.setRange(1, 4, 2);
        try {
            assertEquals(Tag.EVAL_BODY_INCLUDE, tag.doStartTag());
            LoopTagStatus status = tag.getLoopStatus();
            assertEquals("b", status.getCurrent());
            assertEquals(1, status.getIndex());
            assertEquals(1, status.getCount());
            assertEquals(Integer.valueOf(1), status.getBegin());
            assertEquals(Integer.valueOf(4), status.getEnd());
            assertEquals(Integer.valueOf(2), status.getStep());
            assertFalse(status.isLast());

            assertEquals(IterationTag.EVAL_BODY_AGAIN, tag.doAfterBody());
            assertEquals("d", status.getCurrent());
            assertEquals(3, status.getIndex());
            assertEquals(2, status.getCount());
            assertTrue(status.isLast());
            assertEquals(Tag.SKIP_BODY, tag.doAfterBody());
        } finally {
            tag.doFinally();
            tag.release();
        }
    }

    private static final class ListLoop extends LoopTagSupport {
        private final List<String> items;
        private Iterator<String> iterator;

        private ListLoop(List<String> items) {
            this.items = items;
        }

        public void setRange(int first, int last, int stride) {
            begin = first;
            end = last;
            step = stride;
            beginSpecified = true;
            endSpecified = true;
            stepSpecified = true;
        }

        @Override
        protected void prepare() {
            iterator = items.iterator();
        }

        @Override
        protected boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        protected Object next() {
            return iterator.next();
        }
    }
}
