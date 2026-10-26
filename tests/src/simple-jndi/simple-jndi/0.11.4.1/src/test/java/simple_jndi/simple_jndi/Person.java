/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package simple_jndi.simple_jndi;

import java.util.ArrayList;
import java.util.List;

/// JavaBean that the BeanConverter builds from String setters and indexed adders.
public class Person {

    private String name;

    private int answer;

    private final List<String> nicknames = new ArrayList<>();

    public void setName(String name) {
        this.name = name;
    }

    public void setAnswer(String answer) {
        this.answer = Integer.parseInt(answer);
    }

    public void addNickname(int index, String nickname) {
        nicknames.add(index, nickname);
    }

    public String getName() {
        return name;
    }

    public int getAnswer() {
        return answer;
    }

    public List<String> getNicknames() {
        return nicknames;
    }
}
