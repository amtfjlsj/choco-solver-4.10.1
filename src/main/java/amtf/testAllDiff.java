package amtf;

import amtf.parser.XCSPParser;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.variables.IntVar;

import java.util.Arrays;
import java.util.Comparator;

import static java.lang.System.out;
import static org.chocosolver.solver.search.strategy.Search.activityBasedSearch;
import static org.chocosolver.solver.search.strategy.Search.intVarSearch;


public class testAllDiff {

    public static void main(String[] args) {
        float IN_SEC = 1000 * 1000 * 1000f;

        String[] instances = new String[]{
//                "F:\\chenj\\data\\XCSP3\\GracefulGraph\\GracefulGraph-m1-s1\\GracefulGraph-K03-P05.xml",
//                "F:\\chenj\\data\\XCSP3\\Langford-m1-k2\\Langford-2-08.xml",
//                "F:\\chenj\\data\\XCSP3\\Langford\\Langford-m1-k4\\Langford-4-07.xml",
//                "F:\\chenj\\data\\XCSP3\\Queens-m1-s1\\Queens-0080-m1.xml",
//                "F:\\chenj\\data\\XCSP3\\LatinSquare-xcsp2-bqwh15-106\\bqwh-15-106-01_X2.xml",
//                "F:\\chenj\\data\\XCSP3\\LatinSquare-xcsp2-bqwh15-106\\bqwh-15-106-02_X2.xml",
//                "F:\\chenj\\data\\XCSP3\\LatinSquare-xcsp2-bqwh15-106\\bqwh-15-106-03_X2.xml",
//                "F:\\chenj\\data\\XCSP3\\LatinSquare\\LatinSquare-xcsp2-bqwh18-141\\bqwh-18-141-01_X2.xml",
//                "F:\\chenj\\data\\XCSP3\\LatinSquare\\LatinSquare-xcsp2-bqwh18-141\\bqwh-18-141-02_X2.xml",
//                "F:\\chenj\\data\\XCSP3\\LatinSquare\\LatinSquare-xcsp2-bqwh18-141\\bqwh-18-141-03_X2.xml",
//                "F:\\chenj\\data\\XCSP3\\ColouredQueens\\ColouredQueens-m1-s1\\ColouredQueens-07.xml",
//                "F:\\chenj\\data\\XCSP3\\ColouredQueens\\ColouredQueens-m1-s1\\ColouredQueens-09.xml",
//                "F:\\chenj\\data\\XCSP3\\DistinctVectors\\DistinctVectors-m1-s1\\DistinctVectors-30-010-02.xml",
//                "F:\\chenj\\data\\XCSP3\\QuasiGroups\\QuasiGroups-elt-qg7\\QuasiGroup-7-04.xml",
//                "F:\\chenj\\data\\XCSP3\\LatinSquare-m1-gp\\qwh-o30-h374-04.xml",
//                "F:\\chenj\\data\\XCSP3\\AllDiff\\LatinSquare-m1-gs\\qwh-o040-h1600.xml",
                "F:\\chenj\\data\\XCSP3\\AllDiff\\SchurrLemma-mod-s1\\SchurrLemma-012-9-mod.xml",
//                "F:\\chenj\\data\\XCSP3\\SchurrLemma-mod-s1\\SchurrLemma-015-9-mod.xml",
//                "F:\\chenj\\data\\XCSP3\\SchurrLemma-mod-s1\\SchurrLemma-020-9-mod.xml",
//                "F:\\chenj\\data\\XCSP3\\SchurrLemma-mod-s1\\SchurrLemma-030-9-mod.xml",
        };
        XCSPParser parser = new XCSPParser();
        String[] algorithms = new String[]{
//                "AC",
                "ACFast",
                "ACFastbit1",
                "ACFastbit2",
//                "ACNaive",
        };

        int runNum = 2;

        for (String ins : instances) {
            out.println(ins);
            for (String algorithm : algorithms) {
                for (int i = 0; i < runNum; i++) {
                    Measurer.initial();
                    out.println(algorithm + "======>");
                    Model model = new Model();
                    try {
                        parser.model(model, ins, algorithm);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    IntVar[] decVars = (IntVar[]) model.getHook("decisions");

                    if (decVars == null) {
                        decVars = parser.mvars.values().toArray(new IntVar[parser.mvars.size()]);
                    }
                    Arrays.sort(decVars, Comparator.comparingInt(IntVar::getId));
                    Solver solver = model.getSolver();
                    solver.setSearch(activityBasedSearch(decVars));
//                    solver.setSearch(Search.defaultSearch(model));

                    if (solver.solve()) {
                        out.printf("solution: ");
                        for (IntVar v : decVars) {
                            out.printf("%d ", v.getValue());
                        }
                        out.println();
                    }
                    out.println("node: " + solver.getNodeCount());
                    out.println("time: " + solver.getTimeCount() + "s");
                    out.println("find matching time: " + Measurer.matchingTime / IN_SEC + "s");
                    out.println("filter time: " + Measurer.filterTime / IN_SEC + "s");
                    out.println("check scc time: " + Measurer.checkSCCTime / IN_SEC + "s");
                    out.println("propNum: " + Measurer.propNum);
                }
            }
        }
    }
}
