/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.vmplugin.VMPluginFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Java16Test {
    @Test
    void exposesRecordComponentNamesAndTypes() {
        assertEquals(List.of("name", "quantity"), VMPluginFactory.getPlugin().getRecordComponentNames(Line.class));
        ClassNode node = ClassHelper.make(Line.class);
        assertEquals(List.of("name", "quantity"), node.getRecordComponents().stream().map(component -> component.getName()).toList());
        assertEquals(String.class, node.getRecordComponents().get(0).getType().getTypeClass());
        assertEquals(int.class, node.getRecordComponents().get(1).getType().getTypeClass());
    }

    public record Line(String name, int quantity) {
    }
}
