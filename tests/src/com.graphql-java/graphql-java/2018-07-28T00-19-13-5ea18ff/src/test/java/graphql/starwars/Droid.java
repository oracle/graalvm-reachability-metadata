/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql.starwars;

import java.util.List;

/**
 * @author Brian Clozel
 */
public class Droid implements Character {

  @Override
  public Long getId() {
    return null;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public Character getFriends() {
    return null;
  }

  @Override
  public List<Episode> getAppearsIn() {
    return null;
  }

  public String getPrimaryFunction() {
    return null;
  }
}
