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
public interface Character {

  Long getId();

  String getName();

  Character getFriends();

  List<Episode> getAppearsIn();

}
