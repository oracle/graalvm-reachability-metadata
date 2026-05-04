/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pdfbox.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.pdmodel.common.PDNumberTreeNode;
import org.apache.pdfbox.pdmodel.common.PDRange;
import org.junit.jupiter.api.Test;

public class PDNumberTreeNodeTest {
    @Test
    void getNumbersConvertsCosValuesToConfiguredPdModelType() throws Exception {
        COSArray rangeArray = new COSArray();
        rangeArray.add(new COSFloat(1.25f));
        rangeArray.add(new COSFloat(3.5f));

        COSArray numbersArray = new COSArray();
        numbersArray.add(COSInteger.get(7));
        numbersArray.add(rangeArray);

        COSDictionary numberTreeDictionary = new COSDictionary();
        numberTreeDictionary.setItem(COSName.NUMS, numbersArray);

        PDNumberTreeNode numberTreeNode = new PDNumberTreeNode(numberTreeDictionary, PDRange.class);

        Map<Integer, COSObjectable> numbers = numberTreeNode.getNumbers();

        assertThat(numbers).containsOnlyKeys(7);
        assertThat(numbers.get(7)).isInstanceOfSatisfying(PDRange.class, range -> {
            assertThat(range.getCOSArray()).isSameAs(rangeArray);
            assertThat(range.getMin()).isEqualTo(1.25f);
            assertThat(range.getMax()).isEqualTo(3.5f);
        });
    }
}
