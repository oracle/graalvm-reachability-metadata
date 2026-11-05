/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

public class ReflectionFixture extends ReflectionFixtureParent {
    public String label;
    private String hidden = "initial";

    public ReflectionFixture() {
        this("default");
    }

    public ReflectionFixture(String label) {
        this.label = label;
    }

    private ReflectionFixture(int number) {
        this.label = Integer.toString(number);
    }

    public String greet(String name) {
        label = "Hello " + name;
        return label;
    }

    private String secret() {
        return hidden;
    }
}
