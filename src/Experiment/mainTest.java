import amtf.TimeCount;
import amtf.parser.XCSPParser;
import amtf.parser.XCSPParser2;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.constraints.extension.Tuples;
import org.chocosolver.solver.constraints.extension.nary.PropCompactTable;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.impl.BitsetIntVarImpl;
import org.junit.Assert;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

import static java.lang.System.out;
import static org.chocosolver.solver.search.strategy.Search.*;

public class mainTest {

    public static void main(String[] args) throws Exception {

        String[] path = {"D:\\dataheu\\AllInterval\\AllInterval-005.xml",
                "D:\\dataheu\\Blackhole-m1-s04\\Blackhole-04-3-00.xml",
                "D:\\dataheu\\Crossword-m1-lex-puzzle\\Crossword-lex-p01.xml",
                "D:\\dataheu\\DiamondFree\\DiamondFree-004.xml",
                "D:\\dataheu\\Dubois\\Dubois-015.xml",
                "D:\\dataheu\\Knights\\Knights-008-05.xml",
                "D:\\dataheu\\Langford-m1-k2\\Langford-2-02.xml",
                "D:\\dataheu\\MagicSequence\\MagicSequence-003-ca.xml",
                "D:\\dataheu\\MagicSquare\\MagicSquare-9-f10-01.xml",
                "D:\\dataheu\\MultiKnapsack-m1-gp\\MultiKnapsack-1-01.xml",
                "D:\\dataheu\\NumberPartitioning\\NumberPartitioning-004.xml",
                "D:\\dataheu\\Primes\\Primes-10-20-2-1.xml",
                "D:\\dataheu\\QRandom-bdd-15-21-2\\bdd-15-21-2-2713-79-01.xml",
                "D:\\dataheu\\Random-RB-low\\frb-30-15-1.xml",
                "D:\\dataheu\\QRandom-mdd-7-25-5\\mdd-7-25-5-56-01.xml",
                "D:\\dataheu\\QueensKnights\\QueensKnights-008-05-add.xml",
                "D:\\dataheu\\RoomMate\\RoomMate-sr0004-int.xml",
                "D:\\dataheu\\TravellingSalesman\\TravellingSalesman-20-001_X2.xml",
                "D:\\data3\\Random\\Random-m1-8-20-5\\rand-8-20-5-18-800-01.xml",
                "D:\\dataheu\\Bibd\\Bibd-sc-22-033-12-08-04.xml" }; //时间长

//        for(int i=0;i<path.length;i++){
//            solve(path[13]);
//        }

        solve(path[13]);


    }

    public static void solve(String ins) throws Exception {

        float IN_SEC = 1000 * 1000 * 1000f;
        XCSPParser2 parser = new XCSPParser2();
        String allDiffConsistency;
        int runNum = 1;
        for (int i = 0; i < runNum; i++) {
            TimeCount.initial();
            out.println(ins);
            Model model = new Model();
            try {
                parser.model(model, ins);
            } catch (Exception e) {
                e.printStackTrace();
            }

            Map<String, Object> ho=model.getHooks();

            IntVar[] decVars = (IntVar[]) model.getHook("decisions");
            if (decVars == null) {
                decVars = parser.mvars.values().toArray(new IntVar[parser.mvars.size()]);
            }
            Arrays.sort(decVars, Comparator.comparingInt(IntVar::getId));


            Solver solver = model.getSolver();
//            solver.setSearch(activityBasedSearch(decVars));
//            solver.setSearch(AbsCondomOverWDegSearch(decVars));
//            solver.setSearch(CaCdOverWDegSearch(decVars));
            solver.setSearch(dualSearch(decVars));
//            solver.setSearch(dualSearch1(decVars));
//            solver.setSearch(domOverWDegSearch(decVars));
            solver.limitTime(900000);
            if (solver.solve()) {
                out.printf("solution: ");
                for (IntVar v : decVars) {
                    out.printf("%d ", v.getValue());
                }
                out.println();
            }
            out.println("node: " + solver.getNodeCount());
            out.println("time: " + solver.getTimeCount() + "s");
            out.println("find matching time: " + TimeCount.matchingTime / IN_SEC + "s");
            out.println("filter time: " + TimeCount.filterTime / IN_SEC + "s");

        }
    }

    public static void solve1(String ins) throws Exception {

        float IN_SEC = 1000 * 1000 * 1000f;
        XCSPParser2 parser = new XCSPParser2();
        String allDiffConsistency;
        int runNum = 1;
        for (int i = 0; i < runNum; i++) {
            TimeCount.initial();
            out.println(ins);
            Model model = new Model();
            try {
                parser.model(model, ins);
            } catch (Exception e) {
                e.printStackTrace();
            }

            Map<String, Object> ho=model.getHooks();

            IntVar[] decVars = (IntVar[]) model.getHook("decisions");
            if (decVars == null) {
                decVars = parser.mvars.values().toArray(new IntVar[parser.mvars.size()]);
            }
            Arrays.sort(decVars, Comparator.comparingInt(IntVar::getId));


            Solver solver = model.getSolver();
//            solver.setSearch(activityBasedSearch(decVars));
//            solver.setSearch(AbsCondomOverWDegSearch(decVars));
//            solver.setSearch(CaCdOverWDegSearch(decVars));
//            solver.setSearch(dualSearch(decVars));
//            solver.setSearch(dualSearch1(decVars));
//            solver.setSearch(domOverWDegSearch(decVars));
            solver.limitTime(900000);
            if (solver.solve()) {
                out.printf("solution: ");
                for (IntVar v : decVars) {
                    out.printf("%d ", v.getValue());
                }
                out.println();
            }
            out.println("node: " + solver.getNodeCount());
            out.println("time: " + solver.getTimeCount() + "s");
            out.println("find matching time: " + TimeCount.matchingTime / IN_SEC + "s");
            out.println("filter time: " + TimeCount.filterTime / IN_SEC + "s");

        }
    }


}