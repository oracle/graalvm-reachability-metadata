/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.Structure;

import java.util.List;

public class PointStructure extends Structure {
    public int x;
    public int y;

    @Override
    protected List<String> getFieldOrder() {
        return List.of("x", "y");
    }
}
