package nars.perception.lowlevel.spinglass;

import org.apache.commons.math3.linear.ArrayRealVector;

/**
 *
 */
public class Test0 {
    public static void main(String[] args) {
        Test0 test0 = new Test0();


        test0.network.maxInfluenceDistance = 2.0;

        // init test network
        test0.buildTestgrid(4, 4, 1.0, 1.0);


        int numberOfSteps = 10;

        System.out.println("ListAnimate[{");

        for( int stepI = 0; stepI < numberOfSteps; stepI++) {
            test0.network.step();

            System.out.println("Graphics[{");

            for( int dotI = 0; dotI < test0.network.spatialDots.size(); dotI++ ) {
                ArrayRealVector position = test0.network.spatialDots.get(dotI).spatialPosition;
                ArrayRealVector direction = test0.network.spatialDots.get(dotI).spinAttributes.get(0).direction;

                System.out.print("Line[{");
                System.out.print(String.format("{%s,%s},", position.getDataRef()[0], position.getDataRef()[1]));
                ArrayRealVector positionPlusDirection = position.add(direction);
                System.out.print(String.format("{%s,%s}", positionPlusDirection.getDataRef()[0], positionPlusDirection.getDataRef()[1]));
                System.out.print("}]");

                if( dotI != test0.network.spatialDots.size()-1 ) {
                    System.out.print(",");
                }
            }

            System.out.println("}, PlotRange -> {{-1, 7}, {-1, 7}}]");

            if( stepI != numberOfSteps-1 ) {
                System.out.println(",");
            }

            System.out.println("");
        }

        System.out.println("}]");
    }

    public void buildTestgrid(int sizeX, int sizeY, double spacingX, double spacingY) {
        for( int iy = 0; iy < sizeY; iy++ ) {
            for( int ix = 0; ix < sizeX; ix++ ) {
                double positionX = (double)ix * spacingX;
                double positionY = (double)iy * spacingY;

                SpatialDot createdSpatialDot = new SpatialDot();
                createdSpatialDot.spatialPosition = new ArrayRealVector(new double[]{positionX, positionY});
                createdSpatialDot.spinAttributes.add(new SpinAttribute());
                createdSpatialDot.spinAttributes.get(0).direction = new ArrayRealVector(new double[]{0.0, 0.0});
                network.spatialDots.add(createdSpatialDot);
            }
        }
    }

    public ImplicitNetwork network = new ImplicitNetwork();
}
