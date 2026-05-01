/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_abego_treelayout.org_abego_treelayout_core;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.abego.treelayout.Configuration;
import org.abego.treelayout.NodeExtentProvider;
import org.abego.treelayout.TreeForTreeLayout;
import org.abego.treelayout.TreeLayout;
import org.abego.treelayout.util.DefaultConfiguration;
import org.abego.treelayout.util.DefaultTreeForTreeLayout;
import org.abego.treelayout.util.FixedNodeExtentProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

public class Org_abego_treelayout_coreTest {
    private static final double EPSILON = 0.000001;

    @Test
    void defaultTreeMaintainsStructureAndChildOrder() {
        DefaultTreeForTreeLayout<String> tree = new DefaultTreeForTreeLayout<>("root");
        tree.addChildren("root", "left", "middle", "right");
        tree.addChild("middle", "grandchild");

        assertThat(tree.getRoot()).isEqualTo("root");
        assertThat(tree.hasNode("grandchild")).isTrue();
        assertThat(tree.hasNode("missing")).isFalse();
        assertThat(tree.getParent("middle")).isEqualTo("root");
        assertThat(tree.getParent("root")).isNull();
        assertThat(tree.isLeaf("left")).isTrue();
        assertThat(tree.isLeaf("middle")).isFalse();
        assertThat(tree.isChildOfParent("grandchild", "middle")).isTrue();
        assertThat(tree.getFirstChild("root")).isEqualTo("left");
        assertThat(tree.getLastChild("root")).isEqualTo("right");
        assertThat(iterableToList(tree.getChildren("root"))).containsExactly("left", "middle", "right");
        assertThat(iterableToList(tree.getChildrenReverse("root"))).containsExactly("right", "middle", "left");
        assertThat(tree.getChildrenList("left")).isEmpty();
    }

    @Test
    void defaultTreeRejectsUnknownParentsAndRepeatedNodes() {
        DefaultTreeForTreeLayout<String> tree = new DefaultTreeForTreeLayout<>("root");
        tree.addChild("root", "child");

        assertThatThrownBy(() -> tree.addChild("missing", "orphan"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("parentNode is not in the tree");
        assertThatThrownBy(() -> tree.addChild("root", "child"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("node is already in the tree");
    }

    @Test
    void defaultConfigurationAndFixedExtentProviderExposeConfiguredValuesAndValidateInput() {
        DefaultConfiguration<String> configuration = new DefaultConfiguration<>(12.5, 3.5,
                Configuration.Location.Left, Configuration.AlignmentInLevel.AwayFromRoot);
        FixedNodeExtentProvider<String> extentProvider = new FixedNodeExtentProvider<>(30.0, 8.0);

        assertThat(configuration.getGapBetweenLevels(1)).isEqualTo(12.5);
        assertThat(configuration.getGapBetweenLevels(99)).isEqualTo(12.5);
        assertThat(configuration.getGapBetweenNodes("a", "b")).isEqualTo(3.5);
        assertThat(configuration.getRootLocation()).isEqualTo(Configuration.Location.Left);
        assertThat(configuration.getAlignmentInLevel()).isEqualTo(Configuration.AlignmentInLevel.AwayFromRoot);
        assertThat(extentProvider.getWidth("any node")).isEqualTo(30.0);
        assertThat(extentProvider.getHeight("any node")).isEqualTo(8.0);

        assertThat(new DefaultConfiguration<String>(1.0, 2.0).getRootLocation())
                .isEqualTo(Configuration.Location.Top);
        assertThat(new DefaultConfiguration<String>(1.0, 2.0, Configuration.Location.Right).getAlignmentInLevel())
                .isEqualTo(Configuration.AlignmentInLevel.Center);
        assertThat(new FixedNodeExtentProvider<String>().getWidth("zero-sized")).isZero();

        assertThatThrownBy(() -> new DefaultConfiguration<String>(-1.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("gapBetweenLevels must be >= 0");
        assertThatThrownBy(() -> new DefaultConfiguration<String>(0.0, -1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("gapBetweenNodes must be >= 0");
        assertThatThrownBy(() -> new FixedNodeExtentProvider<String>(-1.0, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("width must be >= 0");
        assertThatThrownBy(() -> new FixedNodeExtentProvider<String>(1.0, -1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("height must be >= 0");
    }

    @Test
    void topRootLayoutUsesNodeGapsLevelGapsAndNormalizedBounds() {
        TreeLayout<String> layout = simpleLayout(Configuration.Location.Top);
        Map<String, Rectangle2D.Double> bounds = layout.getNodeBounds();

        assertThat(layout.getTree().getRoot()).isEqualTo("root");
        assertThat(layout.getNodeExtentProvider().getWidth("root")).isEqualTo(10.0);
        assertThat(layout.getConfiguration().getRootLocation()).isEqualTo(Configuration.Location.Top);
        assertThat(layout.getLevelCount()).isEqualTo(2);
        assertThat(layout.getSizeOfLevel(0)).isEqualTo(6.0);
        assertThat(layout.getSizeOfLevel(1)).isEqualTo(6.0);
        assertRectangle(layout.getBounds(), 0.0, 0.0, 24.0, 32.0);
        assertRectangle(bounds.get("root"), 7.0, 0.0, 10.0, 6.0);
        assertRectangle(bounds.get("left"), 0.0, 26.0, 10.0, 6.0);
        assertRectangle(bounds.get("right"), 14.0, 26.0, 10.0, 6.0);
    }

    @Test
    void bottomRootLayoutMirrorsTopLayoutVertically() {
        Map<String, Rectangle2D.Double> bounds = simpleLayout(Configuration.Location.Bottom).getNodeBounds();

        assertRectangle(bounds.get("root"), 7.0, 26.0, 10.0, 6.0);
        assertRectangle(bounds.get("left"), 0.0, 0.0, 10.0, 6.0);
        assertRectangle(bounds.get("right"), 14.0, 0.0, 10.0, 6.0);
    }

    @Test
    void leftAndRightRootLayoutsSwapAxesAndRespectOrientation() {
        TreeLayout<String> leftLayout = simpleLayout(Configuration.Location.Left);
        TreeLayout<String> rightLayout = simpleLayout(Configuration.Location.Right);
        Map<String, Rectangle2D.Double> leftBounds = leftLayout.getNodeBounds();
        Map<String, Rectangle2D.Double> rightBounds = rightLayout.getNodeBounds();

        assertRectangle(leftLayout.getBounds(), 0.0, 0.0, 40.0, 16.0);
        assertRectangle(leftBounds.get("root"), 0.0, 5.0, 10.0, 6.0);
        assertRectangle(leftBounds.get("left"), 30.0, 0.0, 10.0, 6.0);
        assertRectangle(leftBounds.get("right"), 30.0, 10.0, 10.0, 6.0);

        assertRectangle(rightLayout.getBounds(), 0.0, 0.0, 40.0, 16.0);
        assertRectangle(rightBounds.get("root"), 30.0, 5.0, 10.0, 6.0);
        assertRectangle(rightBounds.get("left"), 0.0, 0.0, 10.0, 6.0);
        assertRectangle(rightBounds.get("right"), 0.0, 10.0, 10.0, 6.0);
    }

    @Test
    void alignmentControlsHowDifferentSizedNodesSitWithinALevel() {
        Map<String, Rectangle2D.Double> centered = alignedLayout(Configuration.AlignmentInLevel.Center).getNodeBounds();
        Map<String, Rectangle2D.Double> towardsRoot = alignedLayout(Configuration.AlignmentInLevel.TowardsRoot)
                .getNodeBounds();
        Map<String, Rectangle2D.Double> awayFromRoot = alignedLayout(Configuration.AlignmentInLevel.AwayFromRoot)
                .getNodeBounds();

        assertThat(centered.get("short").getCenterY()).isCloseTo(centered.get("tall").getCenterY(), within(EPSILON));
        assertThat(towardsRoot.get("short").getMinY()).isCloseTo(towardsRoot.get("tall").getMinY(), within(EPSILON));
        assertThat(awayFromRoot.get("short").getMaxY()).isCloseTo(awayFromRoot.get("tall").getMaxY(), within(EPSILON));
        assertRectangle(centered.get("short"), 0.0, 24.0, 8.0, 4.0);
        assertRectangle(towardsRoot.get("short"), 0.0, 20.0, 8.0, 4.0);
        assertRectangle(awayFromRoot.get("short"), 0.0, 28.0, 8.0, 4.0);
    }

    @Test
    void variableNodeExtentsDetermineLevelSizesAndSiblingDistances() {
        DefaultTreeForTreeLayout<String> tree = new DefaultTreeForTreeLayout<>("root");
        tree.addChildren("root", "wide", "narrow", "deep");
        tree.addChild("deep", "leaf");
        NodeExtentProvider<String> extents = new MapBackedExtentProvider()
                .put("root", 20.0, 10.0)
                .put("wide", 30.0, 4.0)
                .put("narrow", 6.0, 12.0)
                .put("deep", 10.0, 8.0)
                .put("leaf", 14.0, 5.0);
        TreeLayout<String> layout = new TreeLayout<>(tree, extents, new DefaultConfiguration<>(7.0, 3.0));
        Map<String, Rectangle2D.Double> bounds = layout.getNodeBounds();

        assertThat(layout.getLevelCount()).isEqualTo(3);
        assertThat(layout.getSizeOfLevel(0)).isEqualTo(10.0);
        assertThat(layout.getSizeOfLevel(1)).isEqualTo(12.0);
        assertThat(layout.getSizeOfLevel(2)).isEqualTo(5.0);
        assertThat(bounds.get("narrow").getMinX() - bounds.get("wide").getMaxX()).isCloseTo(3.0, within(EPSILON));
        assertThat(bounds.get("deep").getMinX() - bounds.get("narrow").getMaxX()).isCloseTo(3.0, within(EPSILON));
        assertThat(bounds.get("leaf").getMinY() - bounds.get("narrow").getMaxY()).isCloseTo(7.0, within(EPSILON));
    }

    @Test
    void compactLayoutSeparatesCousinSubtreesWhileKeepingParentsCentered() {
        DefaultTreeForTreeLayout<String> tree = new DefaultTreeForTreeLayout<>("root");
        tree.addChildren("root", "left", "right");
        tree.addChildren("left", "left.left", "left.right");
        tree.addChildren("right", "right.left", "right.right");
        tree.addChildren("left.right", "left.right.leaf1", "left.right.leaf2");
        tree.addChildren("right.left", "right.left.leaf1", "right.left.leaf2");
        TreeLayout<String> layout = new TreeLayout<>(tree, new FixedNodeExtentProvider<>(10.0, 6.0),
                new DefaultConfiguration<>(8.0, 4.0));
        Map<String, Rectangle2D.Double> bounds = layout.getNodeBounds();

        assertThat(bounds.get("root").getCenterX()).isCloseTo(
                midpoint(bounds.get("left").getCenterX(), bounds.get("right").getCenterX()), within(EPSILON));
        assertThat(bounds.get("left").getCenterX()).isCloseTo(
                midpoint(bounds.get("left.left").getCenterX(), bounds.get("left.right").getCenterX()),
                within(EPSILON));
        assertThat(bounds.get("right").getCenterX()).isCloseTo(
                midpoint(bounds.get("right.left").getCenterX(), bounds.get("right.right").getCenterX()),
                within(EPSILON));
        assertThat(bounds.get("right.left.leaf1").getMinX() - bounds.get("left.right.leaf2").getMaxX())
                .isCloseTo(4.0, within(EPSILON));
        assertRectangle(layout.getBounds(), 0.0, 0.0, 66.0, 48.0);
    }

    @Test
    void customConfigurationCanVaryLevelAndSiblingGaps() {
        DefaultTreeForTreeLayout<String> tree = new DefaultTreeForTreeLayout<>("root");
        tree.addChildren("root", "left", "middle", "right");
        tree.addChild("middle", "leaf");
        TreeLayout<String> layout = new TreeLayout<>(tree, new FixedNodeExtentProvider<>(10.0, 6.0),
                new VariableGapConfiguration());
        Map<String, Rectangle2D.Double> bounds = layout.getNodeBounds();

        assertThat(bounds.get("left").getMinY() - bounds.get("root").getMaxY()).isCloseTo(5.0, within(EPSILON));
        assertThat(bounds.get("leaf").getMinY() - bounds.get("middle").getMaxY()).isCloseTo(11.0, within(EPSILON));
        assertThat(bounds.get("middle").getMinX() - bounds.get("left").getMaxX()).isCloseTo(2.0, within(EPSILON));
        assertThat(bounds.get("right").getMinX() - bounds.get("middle").getMaxX()).isCloseTo(9.0, within(EPSILON));
        assertRectangle(layout.getBounds(), 0.0, 0.0, 41.0, 34.0);
    }

    @Test
    void checkTreeUsesEqualityByDefaultAndIdentityWhenRequested() {
        EqualNode root = new EqualNode("root");
        EqualNode first = new EqualNode("duplicate");
        EqualNode second = new EqualNode("duplicate");
        DuplicateNodeTree tree = new DuplicateNodeTree(root, first, second);
        NodeExtentProvider<EqualNode> extents = new FixedNodeExtentProvider<>(4.0, 4.0);
        Configuration<EqualNode> configuration = new DefaultConfiguration<>(2.0, 1.0);

        assertThatThrownBy(() -> new TreeLayout<>(tree, extents, configuration).checkTree())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Node used more than once in tree: duplicate");

        TreeLayout<EqualNode> identityLayout = new TreeLayout<>(tree, extents, configuration, true);
        identityLayout.checkTree();
        assertThat(identityLayout.getNodeBounds()).hasSize(3);
    }

    @Test
    void dumpTreeUsesConfiguredIndentationSizesAndStringEscaping() {
        DefaultTreeForTreeLayout<String> tree = new DefaultTreeForTreeLayout<>("root");
        tree.addChildren("root", "line\nbreak", "quote\"and\\slash");
        TreeLayout<String> layout = new TreeLayout<>(tree, new FixedNodeExtentProvider<>(3.0, 2.0),
                new DefaultConfiguration<>(1.0, 1.0));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        layout.dumpTree(new PrintStream(output, true, StandardCharsets.UTF_8),
                new TreeLayout.DumpConfiguration("--", true, false));

        String lineSeparator = System.lineSeparator();
        assertThat(output.toString(StandardCharsets.UTF_8))
                .isEqualTo("\"root\" (size: 3.0x2.0)" + lineSeparator
                        + "--\"line\\nbreak\" (size: 3.0x2.0)" + lineSeparator
                        + "--\"quote\\\"and\\\\slash\" (size: 3.0x2.0)" + lineSeparator);
    }

    @Test
    void getSizeOfLevelRejectsOutOfRangeLevels() {
        TreeLayout<String> layout = simpleLayout(Configuration.Location.Top);

        assertThatThrownBy(() -> layout.getSizeOfLevel(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("level must be >= 0");
        assertThatThrownBy(() -> layout.getSizeOfLevel(2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("level must be < levelCount");
    }

    private static TreeLayout<String> simpleLayout(Configuration.Location rootLocation) {
        DefaultTreeForTreeLayout<String> tree = new DefaultTreeForTreeLayout<>("root");
        tree.addChildren("root", "left", "right");
        return new TreeLayout<>(tree, new FixedNodeExtentProvider<>(10.0, 6.0),
                new DefaultConfiguration<>(20.0, 4.0, rootLocation));
    }

    private static TreeLayout<String> alignedLayout(Configuration.AlignmentInLevel alignment) {
        DefaultTreeForTreeLayout<String> tree = new DefaultTreeForTreeLayout<>("root");
        tree.addChildren("root", "short", "tall");
        NodeExtentProvider<String> extents = new MapBackedExtentProvider()
                .put("root", 8.0, 10.0)
                .put("short", 8.0, 4.0)
                .put("tall", 8.0, 12.0);
        Configuration<String> configuration = new DefaultConfiguration<>(10.0, 2.0,
                Configuration.Location.Top, alignment);
        return new TreeLayout<>(tree, extents, configuration);
    }

    private static List<String> iterableToList(Iterable<String> values) {
        return StreamSupport.stream(values.spliterator(), false).collect(Collectors.toList());
    }

    private static double midpoint(double first, double second) {
        return (first + second) / 2.0;
    }

    private static void assertRectangle(Rectangle2D rectangle, double x, double y, double width, double height) {
        assertThat(rectangle.getX()).isCloseTo(x, within(EPSILON));
        assertThat(rectangle.getY()).isCloseTo(y, within(EPSILON));
        assertThat(rectangle.getWidth()).isCloseTo(width, within(EPSILON));
        assertThat(rectangle.getHeight()).isCloseTo(height, within(EPSILON));
    }

    private static final class MapBackedExtentProvider implements NodeExtentProvider<String> {
        private final Map<String, double[]> extents = new HashMap<>();

        MapBackedExtentProvider put(String node, double width, double height) {
            extents.put(node, new double[] {width, height});
            return this;
        }

        @Override
        public double getWidth(String treeNode) {
            return extents.get(treeNode)[0];
        }

        @Override
        public double getHeight(String treeNode) {
            return extents.get(treeNode)[1];
        }
    }

    private static final class VariableGapConfiguration implements Configuration<String> {
        @Override
        public Location getRootLocation() {
            return Location.Top;
        }

        @Override
        public AlignmentInLevel getAlignmentInLevel() {
            return AlignmentInLevel.Center;
        }

        @Override
        public double getGapBetweenLevels(int nextLevel) {
            return nextLevel == 1 ? 5.0 : 11.0;
        }

        @Override
        public double getGapBetweenNodes(String node1, String node2) {
            if (isPair(node1, node2, "left", "middle")) {
                return 2.0;
            }
            if (isPair(node1, node2, "middle", "right")) {
                return 9.0;
            }
            return 4.0;
        }

        private boolean isPair(String node1, String node2, String expected1, String expected2) {
            return node1.equals(expected1) && node2.equals(expected2)
                    || node1.equals(expected2) && node2.equals(expected1);
        }
    }

    private static final class EqualNode {
        private final String id;

        EqualNode(String id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof EqualNode;
        }

        @Override
        public int hashCode() {
            return 1;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    private static final class DuplicateNodeTree implements TreeForTreeLayout<EqualNode> {
        private final EqualNode root;
        private final List<EqualNode> children;
        private final Map<EqualNode, EqualNode> parents = new IdentityHashMap<>();

        DuplicateNodeTree(EqualNode root, EqualNode first, EqualNode second) {
            this.root = root;
            this.children = Arrays.asList(first, second);
            parents.put(first, root);
            parents.put(second, root);
        }

        @Override
        public EqualNode getRoot() {
            return root;
        }

        @Override
        public boolean isLeaf(EqualNode node) {
            return node != root;
        }

        @Override
        public boolean isChildOfParent(EqualNode node, EqualNode parentNode) {
            return parents.get(node) == parentNode;
        }

        @Override
        public Iterable<EqualNode> getChildren(EqualNode node) {
            return node == root ? children : Collections.emptyList();
        }

        @Override
        public Iterable<EqualNode> getChildrenReverse(EqualNode node) {
            if (node != root) {
                return Collections.emptyList();
            }
            List<EqualNode> reversed = new ArrayList<>(children);
            Collections.reverse(reversed);
            return reversed;
        }

        @Override
        public EqualNode getFirstChild(EqualNode parentNode) {
            return children.get(0);
        }

        @Override
        public EqualNode getLastChild(EqualNode parentNode) {
            return children.get(children.size() - 1);
        }
    }
}
