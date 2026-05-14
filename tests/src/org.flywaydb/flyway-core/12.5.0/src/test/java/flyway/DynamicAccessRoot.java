/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

public class DynamicAccessRoot {

    private DynamicAccessChild child;

    public DynamicAccessChild getChild() {
        return child;
    }

    public void setChild(final DynamicAccessChild child) {
        this.child = child;
    }
}
