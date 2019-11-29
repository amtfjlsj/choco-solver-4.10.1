package amtf;

//import org.chocosolver.util.objects.LargeBitSet;

import org.chocosolver.util.objects.LargeBitSet;
import org.chocosolver.util.objects.NaiveBitSet;
import org.chocosolver.util.objects.SparseSet;

import static java.lang.System.out;

public class amtf {
    public static void main(String[] args) {
//        SparseSet sparseSet = new SparseSet(10);
//        sparseSet.iterateValid();
//
//        int e;
//        while (sparseSet.hasNextValid()) {
//            e = sparseSet.next();
//            if ((e & 1) == 0){
//                out.println(e);
//                sparseSet.remove();
//                out.println(sparseSet.toString());
//            }
//        }
//
//        sparseSet.iterateInvalid();
//        while (sparseSet.hasNextInvalid()) {
//            out.println(sparseSet.next());
//        }
//
//        out.println(sparseSet.contain(4));


        NaiveBitSet[] v = new NaiveBitSet[3];
        NaiveBitSet[] a = new NaiveBitSet[3];

        for (int i = 0; i < 3; ++i) {
            v[i] = new NaiveBitSet(3);
            a[i] = new NaiveBitSet(3);
        }

        v[0].set(0);
        v[0].set(2);

        v[1].set(1);
        v[1].set(2);

        v[2].set(0);
        v[2].set(1);


        a[0].set(0);
        a[0].set(2);
        
        a[1].set(1);
        a[1].set(2);

        a[2].set(0);
        a[2].set(1);


        for (int i = 0; i < 3; ++i) {
            out.println(v[i]);
        }
        out.println();
        for (int i = 0; i < 3; ++i) {
            out.println(a[i]);
        }

    }
}
