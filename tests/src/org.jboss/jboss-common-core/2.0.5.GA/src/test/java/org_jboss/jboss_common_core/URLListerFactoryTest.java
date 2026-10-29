/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jboss_common_core;

import org.jboss.net.protocol.URLLister;
import org.jboss.net.protocol.URLListerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class URLListerFactoryTest {
    @TempDir
    Path directory;

    @Test
    void discoversFileListerAndListsOnlyRequestedMembers() throws Exception {
        Path orders = Files.writeString(directory.resolve("orders.xml"), "<orders/>");
        Files.writeString(directory.resolve("ignored.txt"), "notes");
        URL base = directory.toUri().toURL();
        URLLister lister = new URLListerFactory().createURLLister(base);

        Collection<?> members = lister.listMembers(base, "orders.xml");

        assertEquals(Set.of(orders.toUri().toURL()), Set.copyOf(members));
    }

    @Test
    void registeredListerRecursesThroughGroupingDirectories() throws Exception {
        Path group = Files.createDirectory(directory.resolve("group"));
        Path nested = Files.writeString(group.resolve("nested.xml"), "<nested/>");
        Path archive = Files.createDirectory(directory.resolve("application.war"));
        Files.writeString(archive.resolve("inside.xml"), "<inside/>");
        URL base = directory.toUri().toURL();
        URLListerFactory factory = new URLListerFactory();
        factory.registerListener("deployment", "org.jboss.net.protocol.file.FileURLLister");
        URLLister lister = factory.createURLLister("deployment");

        Collection<?> members = lister.listMembers(base, "*", true);

        assertEquals(Set.of(nested.toUri().toURL(), archive.toUri().toURL()), Set.copyOf(members));
    }

    @Test
    void rejectsProtocolsWithoutARegisteredLister() {
        URLListerFactory factory = new URLListerFactory();
        assertThrows(MalformedURLException.class, () -> factory.createURLLister("unknown-protocol"));
    }
}
