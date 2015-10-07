package nars.guifx.graph2;

import automenta.vivisect.dimensionalize.IterativeLayout;
import javafx.beans.property.SimpleDoubleProperty;
import org.apache.commons.math3.linear.ArrayRealVector;

import java.util.function.ToDoubleFunction;

/**
 * Created by me on 9/6/15.
 */
public class CircleLayout<N extends TermNode, E extends TermEdge> implements IterativeLayout<N,E> {

    public final SimpleDoubleProperty radiusMin = new SimpleDoubleProperty(100);
    public final SimpleDoubleProperty radiusMax = new SimpleDoubleProperty(100);

    @Override
    public void init(N n) {
        //n/a
    }

    public void run(TermNode[] verts,
                    //PreallocatedResultFunction<N,double[]> getPosition,
                    ToDoubleFunction<TermNode> radiusFraction,
                    ToDoubleFunction<TermNode> angle,
                    NARGraph.PairConsumer<TermNode, double[]> setPosition) {


        double d[] = new double[2];

        for (TermNode v : verts) {
            if (v == null) continue; //break?

            final double r = radiusFraction.applyAsDouble(v);
            final double a = angle.applyAsDouble(v);
            d[0] = Math.cos(a) * r;
            d[1] = Math.sin(a) * r;
            setPosition.accept(v, d);
        }

    }

    @Override
    public ArrayRealVector getPosition(N vertex) {
        return null;
    }

    @Override
    public void run(NARGraph graph, int iterations) {
        final TermNode[] termList = graph.displayed;

        double[] i = new double[1];
        double numFraction = Math.PI * 2.0 * 1.0 / termList.length;
        double radiusMin = this.radiusMin.get();
        double radiusMax = radiusMin + this.radiusMax.get();

        run(termList,
                (v) -> {
                    double r = 1f - (v.c != null ? v.c.getPriority() : 0);
                    double min = radiusMin;
                    double max = radiusMax;
                    return r * (max - min) + min;
                },
                (v) -> {
                    //return Math.PI * 2 * (v.term.hashCode() % 8192) / 8192.0;

                    i[0] += numFraction;
                    return i[0];
                },
                (v, d) -> {
                    v.move(d[0], d[1]);//, 0.5f, 1f);
                });

    }

    @Override
    public void resetLearning() {

    }

    @Override
    public double getRadius(N vertex) {
        return 0;
    }

}
