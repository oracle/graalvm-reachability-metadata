/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.jsp_api;

import java.lang.reflect.Field;

import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.tagext.JspFragment;
import javax.servlet.jsp.tagext.JspTag;
import javax.servlet.jsp.tagext.SimpleTagSupport;
import javax.servlet.jsp.tagext.TagAdapter;
import javax.servlet.jsp.tagext.TagSupport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleTagSupportTest {
    private static final String JSP_TAG_CLASS_CACHE = "class$javax$servlet$jsp$tagext$JspTag";

    @Test
    void findAncestorWithClassInitializesJspTagClassCacheThroughPublicApi() throws Exception {
        Field cache = SimpleTagSupport.class.getDeclaredField(JSP_TAG_CLASS_CACHE);
        cache.setAccessible(true);
        Object previousValue = cache.get(null);
        cache.set(null, null);
        try {
            TestSimpleTag child = new TestSimpleTag();

            JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, Runnable.class);

            assertThat(ancestor).isNull();
            assertThat(cache.get(null)).isSameAs(JspTag.class);
        } finally {
            cache.set(null, previousValue);
        }
    }

    @Test
    void findAncestorWithClassReturnsNearestSimpleTagParent() {
        TestSimpleTag root = new TestSimpleTag();
        TestSimpleTag parent = new TestSimpleTag();
        TestSimpleTag child = new TestSimpleTag();
        parent.setParent(root);
        child.setParent(parent);

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, TestSimpleTag.class);

        assertThat(ancestor).isSameAs(parent);
    }

    @Test
    void findAncestorWithClassMatchesParentByInterface() {
        MarkedSimpleTag parent = new MarkedSimpleTag();
        TestSimpleTag child = new TestSimpleTag();
        child.setParent(parent);

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, MarkerTag.class);

        assertThat(ancestor).isSameAs(parent);
    }

    @Test
    void findAncestorWithClassUnwrapsTagAdapter() {
        TestSimpleTag adaptedParent = new TestSimpleTag();
        TagAdapter adapter = new TagAdapter(adaptedParent);
        TagSupport child = new TagSupport();
        child.setParent(adapter);

        JspTag ancestor = SimpleTagSupport.findAncestorWithClass(child, TestSimpleTag.class);

        assertThat(ancestor).isSameAs(adaptedParent);
    }

    @Test
    void findAncestorWithClassRejectsInvalidInputs() {
        TestSimpleTag child = new TestSimpleTag();

        assertThat(SimpleTagSupport.findAncestorWithClass(null, TestSimpleTag.class)).isNull();
        assertThat(SimpleTagSupport.findAncestorWithClass(child, null)).isNull();
        assertThat(SimpleTagSupport.findAncestorWithClass(child, String.class)).isNull();
    }

    @Test
    void settersExposeContainerSuppliedStateToSubclasses() throws Exception {
        TestSimpleTag tag = new TestSimpleTag();
        TestSimpleTag parent = new TestSimpleTag();
        JspFragment body = null;
        JspContext context = null;

        tag.setParent(parent);
        tag.setJspBody(body);
        tag.setJspContext(context);
        tag.doTag();

        assertThat(tag.getParent()).isSameAs(parent);
        assertThat(tag.exposedJspBody()).isSameAs(body);
        assertThat(tag.exposedJspContext()).isSameAs(context);
    }

    private interface MarkerTag {
    }

    private static final class MarkedSimpleTag extends TestSimpleTag implements MarkerTag {
    }

    private static class TestSimpleTag extends SimpleTagSupport {
        JspContext exposedJspContext() {
            return getJspContext();
        }

        JspFragment exposedJspBody() {
            return getJspBody();
        }
    }
}
