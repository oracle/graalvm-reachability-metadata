/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import junit.framework.TestCase;

public class LoadingConstructorFixture extends TestCase {
    public LoadingConstructorFixture(String name) {
        super(name);
    }

    public void testRuns() {
    }
}
