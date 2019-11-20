package amtf;

import org.chocosolver.util.objects.SparseSet;
import static java.lang.System.out;

public class amtf {
    public static void main(String[] args) {
        SparseSet sparseSet = new SparseSet(10);
        sparseSet.iterateValid();

        int e;
        while (sparseSet.hasNextValid()) {
            e = sparseSet.next();
            if ((e & 1) == 0){
                out.println(e);
                sparseSet.remove();
                out.println(sparseSet.toString());
            }
        }

        sparseSet.iterateInvalid();
        while (sparseSet.hasNextInvalid()) {
            out.println(sparseSet.next());
        }
    }
}
