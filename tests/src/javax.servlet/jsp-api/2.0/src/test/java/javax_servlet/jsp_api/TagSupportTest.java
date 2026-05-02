/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.jsp_api;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import javax.servlet.jsp.tagext.Tag;
import javax.servlet.jsp.tagext.TagSupport;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(OrderAnnotation.class)
public class TagSupportTest {

    @Test
    @Order(1)
    void invokesCompilerGeneratedTagContractLookup() throws Throwable {
        assertThat(invokeCompilerGeneratedClassLookup()).isSameAs(Tag.class);
    }

    @Test
    @Order(2)
    void initializesTagContractLookupWhenFindingConcreteAncestor() {
        TagSupport ancestor = new TagSupport();
        TagSupport child = new TagSupport();
        child.setParent(ancestor);

        Tag found = TagSupport.findAncestorWithClass(child, TagSupport.class);

        assertThat(found).isSameAs(ancestor);
    }

    @Test
    @Order(3)
    void findsAncestorByTagContractAfterLookupIsInitialized() {
        TagSupport ancestor = new TagSupport();
        TagSupport child = new TagSupport();
        child.setParent(ancestor);

        Tag found = TagSupport.findAncestorWithClass(child, Tag.class);

        assertThat(found).isSameAs(ancestor);
    }

    private static Class<?> invokeCompilerGeneratedClassLookup() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(TagSupport.class, MethodHandles.lookup());
        MethodType methodType = MethodType.methodType(Class.class, String.class);
        MethodHandle classLookup = lookup.findStatic(TagSupport.class, "class$", methodType);
        return (Class<?>) classLookup.invokeExact("javax.servlet.jsp.tagext.Tag");
    }
}
