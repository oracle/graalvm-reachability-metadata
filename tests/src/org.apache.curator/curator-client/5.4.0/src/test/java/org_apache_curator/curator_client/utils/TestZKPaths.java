/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client.utils;

import org.apache.curator.utils.ZKPaths;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestZKPaths {
    @Test
    public void testMakePath() {
        assertEquals(ZKPaths.makePath(null, "/"), "/");
        assertEquals(ZKPaths.makePath("", null), "/");
        assertEquals(ZKPaths.makePath("/", null), "/");
        assertEquals(ZKPaths.makePath(null, null), "/");
        assertEquals(ZKPaths.makePath("/", "/"), "/");
        assertEquals(ZKPaths.makePath("", "/"), "/");
        assertEquals(ZKPaths.makePath("/", ""), "/");
        assertEquals(ZKPaths.makePath("", ""), "/");
        assertEquals(ZKPaths.makePath("foo", ""), "/foo");
        assertEquals(ZKPaths.makePath("foo", "/"), "/foo");
        assertEquals(ZKPaths.makePath("/foo", ""), "/foo");
        assertEquals(ZKPaths.makePath("/foo", "/"), "/foo");
        assertEquals(ZKPaths.makePath("foo", null), "/foo");
        assertEquals(ZKPaths.makePath("foo", null), "/foo");
        assertEquals(ZKPaths.makePath("/foo", null), "/foo");
        assertEquals(ZKPaths.makePath("/foo", null), "/foo");
        assertEquals(ZKPaths.makePath("", "bar"), "/bar");
        assertEquals(ZKPaths.makePath("/", "bar"), "/bar");
        assertEquals(ZKPaths.makePath("", "/bar"), "/bar");
        assertEquals(ZKPaths.makePath("/", "/bar"), "/bar");
        assertEquals(ZKPaths.makePath(null, "bar"), "/bar");
        assertEquals(ZKPaths.makePath(null, "bar"), "/bar");
        assertEquals(ZKPaths.makePath(null, "/bar"), "/bar");
        assertEquals(ZKPaths.makePath(null, "/bar"), "/bar");
        assertEquals(ZKPaths.makePath("foo", "bar"), "/foo/bar");
        assertEquals(ZKPaths.makePath("/foo", "bar"), "/foo/bar");
        assertEquals(ZKPaths.makePath("foo", "/bar"), "/foo/bar");
        assertEquals(ZKPaths.makePath("/foo", "/bar"), "/foo/bar");
        assertEquals(ZKPaths.makePath("/foo", "bar/"), "/foo/bar");
        assertEquals(ZKPaths.makePath("/foo/", "/bar/"), "/foo/bar");
        assertEquals(ZKPaths.makePath("foo", "bar", "baz"), "/foo/bar/baz");
        assertEquals(ZKPaths.makePath("foo", "bar", "baz", "qux"), "/foo/bar/baz/qux");
        assertEquals(ZKPaths.makePath("/foo", "/bar", "/baz"), "/foo/bar/baz");
        assertEquals(ZKPaths.makePath("/foo/", "/bar/", "/baz/"), "/foo/bar/baz");
        assertEquals(ZKPaths.makePath("foo", null, ""), "/foo");
        assertEquals(ZKPaths.makePath("foo", "bar", ""), "/foo/bar");
        assertEquals(ZKPaths.makePath("foo", null, "baz"), "/foo/baz");
    }

    @Test
    public void testSplit() {
        assertEquals(ZKPaths.split("/"), Collections.emptyList());
        assertEquals(ZKPaths.split("/test"), Collections.singletonList("test"));
        assertEquals(ZKPaths.split("/test/one"), Arrays.asList("test", "one"));
        assertEquals(ZKPaths.split("/test/one/two"), Arrays.asList("test", "one", "two"));
    }
}
