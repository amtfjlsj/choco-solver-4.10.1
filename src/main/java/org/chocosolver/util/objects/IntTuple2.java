package org.chocosolver.util.objects;

public class IntTuple2 {
    int a, b;

    public IntTuple2(int x, int y) {
        this.a = x;
        this.b = y;
    }

    public boolean overlap(IntTuple2 t) {
        return (t.a >= a && t.a <= b) || (t.b >= a && t.b <= b);
    }

    public static boolean EQ(IntTuple2 t1, IntTuple2 t2) {
        return t1.a == t2.a && t1.b == t2.b;
    }

}
