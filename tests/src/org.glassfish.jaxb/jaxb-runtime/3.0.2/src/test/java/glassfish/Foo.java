/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package glassfish;

import java.util.Objects;

import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Foo {

    private String message;

    public Foo() {
    }

    public Foo(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Foo foo = (Foo) o;
        return Objects.equals(message, foo.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message);
    }
}
