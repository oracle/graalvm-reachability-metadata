/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_virtuald.curvesapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;

import com.graphbuilder.curve.BSpline;
import com.graphbuilder.curve.BezierCurve;
import com.graphbuilder.curve.CardinalSpline;
import com.graphbuilder.curve.CatmullRomSpline;
import com.graphbuilder.curve.ControlPath;
import com.graphbuilder.curve.CubicBSpline;
import com.graphbuilder.curve.Curve;
import com.graphbuilder.curve.GroupIterator;
import com.graphbuilder.curve.LagrangeCurve;
import com.graphbuilder.curve.MultiPath;
import com.graphbuilder.curve.NURBSpline;
import com.graphbuilder.curve.NaturalCubicSpline;
import com.graphbuilder.curve.Point;
import com.graphbuilder.curve.Polyline;
import com.graphbuilder.curve.ShapeMultiPath;
import com.graphbuilder.curve.ValueVector;
import com.graphbuilder.geom.Geom;
import com.graphbuilder.geom.Point2d;
import com.graphbuilder.geom.PointFactory;
import com.graphbuilder.math.Expression;
import com.graphbuilder.math.ExpressionTree;
import com.graphbuilder.math.FuncMap;
import com.graphbuilder.math.VarMap;
import com.graphbuilder.math.func.Function;
import com.graphbuilder.struc.Bag;
import com.graphbuilder.struc.Stack;
import org.junit.jupiter.api.Test;

public class CurvesapiTest {
    private static final double TOLERANCE = 1.0e-9;

    @Test
    void expressionTreeEvaluatesVariablesOperatorsAndCustomFunctions() {
        Expression polynomial = ExpressionTree.parse("x * x + 2 * x * y + y ^ 2");
        VarMap variables = new VarMap();
        variables.setValue("x", 3.0);
        variables.setValue("y", 4.0);

        assertThat(polynomial.eval(variables, new FuncMap())).isCloseTo(49.0, within(TOLERANCE));
        assertThat(polynomial.getVariableNames()).containsExactlyInAnyOrder("x", "y");
        assertThat(polynomial.toString()).contains("x").contains("y");

        FuncMap functions = new FuncMap();
        functions.setFunction("hypot", new Function() {
            @Override
            public double of(double[] params, int numParams) {
                return Math.hypot(params[0], params[1]);
            }

            @Override
            public boolean acceptNumParam(int numParams) {
                return numParams == 2;
            }
        });

        Expression customFunction = ExpressionTree.parse("hypot(a, b) + 1");
        variables.setValue("a", 6.0);
        variables.setValue("b", 8.0);

        assertThat(customFunction.eval(variables, functions)).isCloseTo(11.0, within(TOLERANCE));
        assertThat(functions.getFunction("hypot", 2).acceptNumParam(2)).isTrue();
    }

    @Test
    void defaultFunctionMapEvaluatesBuiltInMathFunctions() {
        FuncMap functions = new FuncMap();
        functions.loadDefaultFunctions();
        VarMap variables = new VarMap();

        assertThat(functions.getFunctionNames())
                .contains("sin", "pi", "sqrt", "pow", "min", "max", "avg", "fact", "combin", "log", "ln", "e");
        assertThat(ExpressionTree.parse("sin(pi() / 2)").eval(variables, functions)).isCloseTo(1.0, within(TOLERANCE));
        assertThat(ExpressionTree.parse("sqrt(pow(3, 2) + pow(4, 2))").eval(variables, functions))
                .isCloseTo(5.0, within(TOLERANCE));
        assertThat(ExpressionTree.parse("min(5, 2, 7) + max(5, 2, 7) + avg(2, 4, 6)").eval(variables, functions))
                .isCloseTo(13.0, within(TOLERANCE));
        assertThat(ExpressionTree.parse("fact(5) + combin(5, 2)").eval(variables, functions))
                .isCloseTo(130.0, within(TOLERANCE));
        assertThat(ExpressionTree.parse("log(100) + ln(e())").eval(variables, functions))
                .isCloseTo(3.0, within(TOLERANCE));
    }

    @Test
    void groupIteratorParsesExpressionBasedRangesAndCanBeReset() {
        GroupIterator iterator = new GroupIterator("0:n-1, n-1:0", 4);

        assertThat(iterator.getControlString()).isEqualTo("0:n-1, n-1:0");
        assertThat(iterator.getGroupLength()).isEqualTo(4);
        assertThat(iterator.getGroupSize()).isEqualTo(8);
        assertThat(iterator.isInRange(0, 4)).isTrue();

        int[] group = new int[iterator.getGroupLength()];
        iterator.copyGroupArray(group);
        assertThat(group).containsExactly(0, 3, 3, 0);

        assertThat(readAll(iterator)).containsExactly(0, 1, 2, 3, 3, 2, 1, 0);
        assertThat(iterator.hasNext()).isFalse();

        iterator.reset();
        assertThat(iterator.index_i()).isZero();
        assertThat(iterator.count_j()).isZero();
        assertThat(iterator.next()).isZero();

        assertThrows(IllegalArgumentException.class, () -> new GroupIterator(new int[] {0, 1, 2}));
    }

    @Test
    void controlPathValueVectorAndPointFactoryManageMutableCurveInputs() {
        ControlPath path = new ControlPath();
        Point2d first = PointFactory.create(0.0, 0.0);
        Point2d second = PointFactory.create(1.0, 1.0);
        Point2d third = PointFactory.create(2.0, 0.0);

        path.addPoint(first);
        path.addPoint(third);
        path.insertPoint(second, 1);
        assertThat(path.numPoints()).isEqualTo(3);
        assertPoint(path.getPoint(1), 1.0, 1.0);

        Point2d replacement = PointFactory.create(3.0, 3.0);
        assertThat(path.setPoint(replacement, 1)).isSameAs(second);
        assertPoint(path.getPoint(1), 3.0, 3.0);
        path.removePoint(replacement);
        assertThat(path.numPoints()).isEqualTo(2);

        first.setLocation(4.0, 5.0);
        assertThat(first.getX()).isCloseTo(4.0, within(TOLERANCE));
        assertThat(first.getY()).isCloseTo(5.0, within(TOLERANCE));
        first.setLocation(new double[] {6.0, 7.0});
        assertPoint(first, 6.0, 7.0);

        ValueVector vector = new ValueVector(new double[] {1.0, 3.0}, 2);
        vector.insert(2.0, 1);
        vector.add(4.0);
        vector.set(5.0, 3);
        assertThat(vector.size()).isEqualTo(4);
        assertThat(vector.get(0)).isCloseTo(1.0, within(TOLERANCE));
        assertThat(vector.get(1)).isCloseTo(2.0, within(TOLERANCE));
        assertThat(vector.get(2)).isCloseTo(3.0, within(TOLERANCE));
        assertThat(vector.get(3)).isCloseTo(5.0, within(TOLERANCE));
        vector.remove(1);
        assertThat(vector.size()).isEqualTo(3);
        assertThat(vector.get(1)).isCloseTo(3.0, within(TOLERANCE));
    }

    @Test
    void polylineAppendsControlPointsToMultiPathAndMeasuresDistanceToSegments() {
        ControlPath path = controlPath(new double[][] {{0.0, 0.0}, {2.0, 0.0}, {2.0, 2.0}, {4.0, 2.0}});
        Polyline polyline = new Polyline(path, new GroupIterator("0:n-1", path.numPoints()));
        MultiPath multiPath = new MultiPath(2);

        polyline.appendTo(multiPath);

        assertThat(multiPath.getNumPoints()).isEqualTo(4);
        assertThat(multiPath.getType(0)).isSameAs(MultiPath.MOVE_TO);
        assertThat(multiPath.getType(1)).isSameAs(MultiPath.LINE_TO);
        assertPoint(multiPath.get(0), 0.0, 0.0);
        assertPoint(multiPath.get(3), 4.0, 2.0);
        assertThat(multiPath.getDistSq(new double[] {1.0, 0.0})).isCloseTo(0.0, within(TOLERANCE));
        assertThat(multiPath.getDistSq(new double[] {1.0, 1.0})).isCloseTo(0.0, within(TOLERANCE));

        multiPath.setFlatness(0.25);
        assertThat(multiPath.getFlatness()).isCloseTo(0.25, within(TOLERANCE));
        assertThrows(IllegalArgumentException.class, () -> multiPath.setType(0, MultiPath.LINE_TO));
    }

    @Test
    void bezierCurveEvaluatesQuadraticCurveAndAppendsApproximatedPath() {
        ControlPath path = controlPath(new double[][] {{0.0, 0.0}, {1.0, 2.0}, {2.0, 0.0}});
        BezierCurve curve = new BezierCurve(path, new GroupIterator("0:n-1", path.numPoints()));
        curve.setSampleLimit(16);
        curve.setInterval(0.0, 1.0);

        double[] midpoint = {0.0, 0.0, 0.5};
        curve.eval(midpoint);
        assertPoint(midpoint, 1.0, 1.0);
        assertThat(curve.getSampleLimit()).isEqualTo(16);
        assertThat(curve.t_min()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(curve.t_max()).isCloseTo(1.0, within(TOLERANCE));

        MultiPath multiPath = new MultiPath(2);
        multiPath.setFlatness(0.01);
        curve.appendTo(multiPath);

        assertThat(multiPath.getNumPoints()).isGreaterThanOrEqualTo(2);
        assertThat(multiPath.getType(0)).isSameAs(MultiPath.MOVE_TO);
        assertPoint(multiPath.get(0), 0.0, 0.0);
        assertPoint(multiPath.get(multiPath.getNumPoints() - 1), 2.0, 0.0);

        curve.resetMemory();
        assertThrows(IllegalArgumentException.class, () -> curve.setSampleLimit(-1));
        assertThrows(IllegalArgumentException.class, () -> curve.setInterval(1.0, 0.0));
    }

    @Test
    void splineImplementationsAppendContinuousFinitePaths() {
        ControlPath path = controlPath(new double[][] {
                {0.0, 0.0}, {1.0, 2.0}, {2.0, 1.0}, {3.0, 3.0}, {4.0, 0.0}, {5.0, 2.0}
        });

        CardinalSpline cardinal = new CardinalSpline(path, new GroupIterator("0:n-1", path.numPoints()));
        cardinal.setAlpha(0.25);
        assertThat(cardinal.getAlpha()).isCloseTo(0.25, within(TOLERANCE));
        assertCurveAppendsFinitePath(cardinal);

        CatmullRomSpline catmullRom = new CatmullRomSpline(path, new GroupIterator("0:n-1", path.numPoints()));
        assertCurveAppendsFinitePath(catmullRom);

        CubicBSpline cubicBSpline = new CubicBSpline(path, new GroupIterator("0:n-1", path.numPoints()));
        cubicBSpline.setInterpolateEndpoints(true);
        assertThat(cubicBSpline.getInterpolateEndpoints()).isTrue();
        assertCurveAppendsFinitePath(cubicBSpline);

        NaturalCubicSpline natural = new NaturalCubicSpline(path, new GroupIterator("0:n-1", path.numPoints()));
        natural.setClosed(false);
        assertThat(natural.getClosed()).isFalse();
        assertCurveAppendsFinitePath(natural);
        natural.resetMemory();
    }

    @Test
    void bsplineNurbsAndLagrangeCurvesRespectConfigurationVectors() {
        ControlPath path = controlPath(new double[][] {
                {0.0, 0.0}, {1.0, 2.0}, {2.0, 1.0}, {3.0, 3.0}, {4.0, 0.0}
        });

        BSpline bspline = new BSpline(path, new GroupIterator("0:n-1", path.numPoints()));
        bspline.setDegree(2);
        bspline.setKnotVectorType(BSpline.UNIFORM_CLAMPED);
        bspline.setUseDefaultInterval(true);
        assertThat(bspline.getDegree()).isEqualTo(2);
        assertThat(bspline.getKnotVectorType()).isEqualTo(BSpline.UNIFORM_CLAMPED);
        assertThat(bspline.getUseDefaultInterval()).isTrue();
        assertCurveAppendsFinitePath(bspline);
        bspline.resetMemory();

        NURBSpline nurb = new NURBSpline(path, new GroupIterator("0:n-1", path.numPoints()));
        nurb.setDegree(2);
        nurb.setUseWeightVector(true);
        ValueVector weights = new ValueVector(new double[] {1.0, 0.5, 2.0, 0.75, 1.5}, path.numPoints());
        nurb.setWeightVector(weights);
        assertThat(nurb.getUseWeightVector()).isTrue();
        assertThat(nurb.getWeightVector()).isSameAs(weights);
        assertCurveAppendsFinitePath(nurb);
        nurb.resetMemory();

        LagrangeCurve lagrange = new LagrangeCurve(path, new GroupIterator("0:n-1", path.numPoints()));
        lagrange.setBaseIndex(1);
        lagrange.setBaseLength(3);
        lagrange.setInterpolateFirst(true);
        lagrange.setInterpolateLast(true);
        lagrange.setKnotVector(new ValueVector(new double[] {0.0, 0.25, 0.5, 0.75, 1.0}, path.numPoints()));
        assertThat(lagrange.getBaseIndex()).isEqualTo(1);
        assertThat(lagrange.getBaseLength()).isEqualTo(3);
        assertThat(lagrange.getInterpolateFirst()).isTrue();
        assertThat(lagrange.getInterpolateLast()).isTrue();
        assertCurveAppendsFinitePath(lagrange);
        lagrange.resetMemory();
    }

    @Test
    void shapeMultiPathExposesAwtShapeBoundsContainmentAndIteration() {
        ShapeMultiPath shape = new ShapeMultiPath(2);
        shape.setWindingRule(PathIterator.WIND_NON_ZERO);
        shape.moveTo(new double[] {0.0, 0.0});
        shape.lineTo(new double[] {4.0, 0.0});
        shape.lineTo(new double[] {4.0, 3.0});
        shape.lineTo(new double[] {0.0, 3.0});
        shape.lineTo(new double[] {0.0, 0.0});

        assertThat(shape.getWindingRule()).isEqualTo(PathIterator.WIND_NON_ZERO);
        assertThat(shape.contains(2.0, 1.5)).isTrue();
        assertThat(shape.contains(-1.0, 1.5)).isFalse();
        assertThat(shape.contains(new Rectangle2D.Double(1.0, 1.0, 2.0, 1.0))).isTrue();
        assertThat(shape.intersects(new Rectangle2D.Double(3.5, 1.0, 2.0, 1.0))).isTrue();
        assertThat(shape.intersects(new Rectangle2D.Double(5.0, 5.0, 1.0, 1.0))).isFalse();
        assertThat(shape.getDistSq(2.0, 1.5)).isCloseTo(2.25, within(TOLERANCE));

        Rectangle bounds = shape.getBounds();
        assertThat(bounds.getX()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(bounds.getY()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(bounds.getWidth()).isCloseTo(4.0, within(TOLERANCE));
        assertThat(bounds.getHeight()).isCloseTo(3.0, within(TOLERANCE));

        PathIterator iterator = shape.getPathIterator(AffineTransform.getTranslateInstance(1.0, 2.0));
        double[] segment = new double[6];
        assertThat(iterator.currentSegment(segment)).isEqualTo(PathIterator.SEG_MOVETO);
        assertPoint(segment, 1.0, 2.0);
        iterator.next();
        assertThat(iterator.currentSegment(segment)).isEqualTo(PathIterator.SEG_LINETO);
        assertPoint(segment, 5.0, 2.0);
    }

    @Test
    void geometryUtilitiesComputeDistancesIntersectionsAnglesAndCircle() {
        double[] result = new double[3];

        Object intersection = Geom.getLineLineIntersection(0.0, 0.0, 2.0, 2.0, 0.0, 2.0, 2.0, 0.0, result);
        assertThat(intersection).isSameAs(Geom.INTERSECT);
        assertThat(result[0]).isCloseTo(1.0, within(TOLERANCE));
        assertThat(result[1]).isCloseTo(1.0, within(TOLERANCE));

        Object parallel = Geom.getLineLineIntersection(0.0, 0.0, 1.0, 0.0, 0.0, 2.0, 1.0, 2.0, result);
        assertThat(parallel).isSameAs(Geom.PARALLEL);

        double[] closest = new double[3];
        assertThat(Geom.ptSegDistSq(0.0, 0.0, 2.0, 0.0, 3.0, 1.0, closest)).isCloseTo(2.0, within(TOLERANCE));
        assertThat(closest[0]).isCloseTo(2.0, within(TOLERANCE));
        assertThat(closest[1]).isCloseTo(0.0, within(TOLERANCE));

        assertThat(Geom.getAngle(0.0, 0.0, 0.0, 1.0)).isCloseTo(Math.PI / 2.0, within(TOLERANCE));
        assertThat(Geom.getTriangleAreaSq(3.0, 4.0, 5.0)).isCloseTo(36.0, within(TOLERANCE));

        double[] circle = new double[3];
        assertThat(Geom.getCircle(1.0, 0.0, 0.0, 1.0, -1.0, 0.0, circle)).isTrue();
        assertThat(circle[0]).isCloseTo(0.0, within(TOLERANCE));
        assertThat(circle[1]).isCloseTo(0.0, within(TOLERANCE));
        assertThat(circle[2]).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void collectionStructuresSupportIndexedAndStackOperations() {
        Bag bag = new Bag();
        bag.add("a");
        bag.add("c");
        bag.insert("b", 1);

        assertThat(bag.size()).isEqualTo(3);
        assertThat(bag.contains("b")).isTrue();
        assertThat(bag.indexOf("c")).isEqualTo(2);
        assertThat(bag.set("d", 2)).isEqualTo("c");
        assertThat(bag.remove("a")).isEqualTo(0);
        assertThat(bag.remove(0)).isEqualTo("b");
        assertThat(bag.get(0)).isEqualTo("d");

        Stack stack = new com.graphbuilder.struc.Stack();
        assertThat(stack.isEmpty()).isTrue();
        stack.push("first");
        stack.push("second");
        assertThat(stack.peek()).isEqualTo("second");
        assertThat(stack.pop()).isEqualTo("second");
        assertThat(stack.pop()).isEqualTo("first");
        assertThat(stack.isEmpty()).isTrue();
    }

    private static ControlPath controlPath(double[][] coordinates) {
        ControlPath path = new ControlPath();
        for (double[] coordinate : coordinates) {
            path.addPoint(PointFactory.create(coordinate[0], coordinate[1]));
        }
        return path;
    }

    private static int[] readAll(GroupIterator iterator) {
        int[] values = new int[iterator.getGroupSize()];
        int index = 0;
        while (iterator.hasNext()) {
            values[index] = iterator.next();
            index++;
        }
        return values;
    }

    private static void assertCurveAppendsFinitePath(Curve curve) {
        MultiPath multiPath = new MultiPath(2);
        multiPath.setFlatness(0.1);

        curve.appendTo(multiPath);

        assertThat(multiPath.getNumPoints()).isGreaterThanOrEqualTo(2);
        assertThat(multiPath.getType(0)).isSameAs(MultiPath.MOVE_TO);
        for (int i = 0; i < multiPath.getNumPoints(); i++) {
            double[] point = multiPath.get(i);
            assertThat(Double.isFinite(point[0])).isTrue();
            assertThat(Double.isFinite(point[1])).isTrue();
        }
    }

    private static void assertPoint(Point point, double expectedX, double expectedY) {
        assertPoint(point.getLocation(), expectedX, expectedY);
    }

    private static void assertPoint(double[] point, double expectedX, double expectedY) {
        assertThat(point[0]).isCloseTo(expectedX, within(TOLERANCE));
        assertThat(point[1]).isCloseTo(expectedY, within(TOLERANCE));
    }
}
