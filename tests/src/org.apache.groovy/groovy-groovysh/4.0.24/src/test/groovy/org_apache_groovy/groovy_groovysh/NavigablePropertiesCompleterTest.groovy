/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_groovysh

import groovy.util.Node
import groovy.util.NodeList
import org.apache.groovy.groovysh.completion.NavigablePropertiesCompleter
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

public class NavigablePropertiesCompleterTest {
    @Test
    void completesNavigableMapKeysAndQuotesKeysThatAreNotIdentifiers() {
        NavigablePropertiesCompleter completer = new NavigablePropertiesCompleter()
        Set<CharSequence> candidates = new TreeSet<>()
        Map<Object, Object> source = [
                alpha        : 1,
                'alpha value': 2,
                "alpha's"    : 3,
                'alpha\\path': 4,
                'alpha$'     : 5,
                beta         : 6,
                'bad\nkey'   : 7,
                (42)         : 8,
        ]

        completer.addCompletions(source, 'alpha', candidates)

        assertThat(candidates).containsExactlyInAnyOrder(
                'alpha',
                "'alpha\$'",
                "'alpha\\\\path'",
                "'alpha\\'s'",
                "'alpha value'",
        )
    }

    @Test
    void completesNodeChildrenAndNodeListEntriesByPrefix() {
        NavigablePropertiesCompleter completer = new NavigablePropertiesCompleter()
        Node root = new Node(null, 'root')
        new Node(root, 'alphaChild')
        new Node(root, 'betaChild')
        root.children().add('alphaText')

        Set<CharSequence> rootCandidates = new TreeSet<>()
        completer.addCompletions(root, 'alpha', rootCandidates)

        assertThat(rootCandidates).containsExactly('alphaChild', 'alphaText')

        Node nestedParent = new Node(null, 'container')
        new Node(nestedParent, 'alphaNested')
        new Node(nestedParent, 'gammaNested')
        NodeList nodeList = new NodeList()
        nodeList.add(nestedParent)

        Set<CharSequence> nestedCandidates = new TreeSet<>()
        completer.addCompletions(nodeList, 'alpha', nestedCandidates)

        assertThat(nestedCandidates).containsExactly('alphaNested')
    }
}
