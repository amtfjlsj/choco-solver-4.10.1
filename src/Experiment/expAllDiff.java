import amtf.parser.XCSPParser;
import amtf.parser.XCSPParser2;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.nary.nvalue.amnv.mis.F;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.variables.IntVar;

import java.io.*;
import java.util.Arrays;
import java.util.Comparator;

import static java.lang.System.out;
import static org.chocosolver.solver.search.strategy.Search.*;

public class expAllDiff {

    public static void main(String[] args) {

        String inputFolder = "D:\\dataheu\\";
        String outputFolder = "E:";
        String[] series = new String[]{
//                "Sat-m1-aim050",
//                "Sat-m1-aim100",
//                "AllInterval",
//                "Bibd",
//                "Blackhole-m1-s04",
//                "CarSequencing-m1-jcr",
//                "ColouredQueens",
//                "CostasArray",
//                "CoveringArray",
//                "Crossword-m1-lex-herald",
//                "Crossword-m1-lex-puzzle",
//                "Crossword-m1-lex-vg",
//                "Crossword-m1-ogd2008-herald",
//                "Crossword-m1-ogd2008-puzzle",
//                "Crossword-m1-ogd2008-vg",
//                "Crossword-m1-ogd-herald",
//                "Crossword-m1-ogd-puzzle",
//                "Crossword-m1-ogd-vg",
//                "Crossword-m1-uk-herald",
//                "Crossword-m1-uk-puzzle",
//                "Crossword-m1-uk-vg",
//                "Crossword-m1-words-herald",
//                "Crossword-m1-words-puzzle",
//                "Crossword-m1-words-vg",
//                "DeBruijnSequence",
//                "DiamondFree",
//                "DistinctVectors",
//                "Dubois",
//                "GracefulGraph",
//                "Knights",
//                "KnightTour-ext-s1",
//                "Langford-m1-k2",
//                "Langford-m1-k3",
//                "Langford-m1-k4",
//                "Langford-m2-s1",
//                "MagicSequence",
//                "MagicSquare",
//                "MarketSplit",
//                "MultiKnapsack-m1-gp",
//                "NumberPartitioning",
//                "Ortholatin",
//                "PigeonsPlus",
//                "Primes",
//                "QRandom-bdd-15-21-2",
//                "QRandom-bdd-18-21-2",
//                "QRandom-mdd-7-25-5",
//                "QuasiGroups-elt-qg3",
//                "QueensKnights",
//                "RadarSurveillance-m1-s8-24",
                "Random-RB-low",
//                "Rlfap-m1-graphs",
//                "RoomMate",
//                "SocialGolfers-cp-s1",
//                "StripPacking-m1-series1",
//                "Subisomorphism-m1-LV",
//                "TravellingSalesman",
//                "Wwtpp"

        };

        XCSPParser2 parser = new XCSPParser2();
        String[] algorithms = new String[]{
                "ABS",
                "DOW",
                "DOWA",
                "DOWC",
                "DOWD",
//                "DOWD1",
        };
        int runNum = 1;
        long node = 0, propNum = 0;
        float time, matchingTime, filterTime;
        float IN_SEC = 1000 * 1000 * 1000f;


        for (String s : series) {
            try {
                File csv = new File(outputFolder + s + ".csv");
                BufferedWriter bw = new BufferedWriter(new FileWriter(csv, false));
                bw.write("instance");
                for (int i = 0; i < algorithms.length; i++) {
//                    bw.write(",algorithm,node,propNum,time,matchingTime,filterTime");
                    bw.write(",algorithm,node,time");
                }
                bw.newLine();
                // 读取实例集s下的所有实例文件名
                File[] instances = new File(inputFolder + s).listFiles();
                for (File ins : instances) {
                    out.println(ins.getName());
                    bw.write(ins.getName());
                    for (String algorithm : algorithms) {
                        time = 0f;
                        matchingTime = 0f;
                        filterTime = 0f;
                        out.println(algorithm + "======>");
                        for (int i = 0; i < runNum; i++) {
//                            Measurer.initial();
                            Model model = new Model();
                            try {
                                parser.model(model, ins.getPath());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            IntVar[] decVars = (IntVar[]) model.getHook("decisions");

                            if (decVars == null) {
                                decVars = parser.mvars.values().toArray(new IntVar[parser.mvars.size()]);
                            }
                            Arrays.sort(decVars, Comparator.comparingInt(IntVar::getId));
                            Solver solver = model.getSolver();
                            solver.limitTime("180s");
                            switch (algorithm){
                                case "ABS":solver.setSearch(activityBasedSearch(decVars));break;
                                case "DOW":solver.setSearch(domOverWDegSearch(decVars));break;
                                case "DOWA":solver.setSearch(CaCdOverWDegSearch(decVars));break;
                                case "DOWC":solver.setSearch(AbsCondomOverWDegSearch(decVars));break;
                                case "DOWD":solver.setSearch(dualSearch(decVars));break;
                                case "DOWD1":solver.setSearch(dualSearch1(decVars));break;
                            }


                            if (solver.solve()) {
//                                out.printf("solution: ");
//                                for (IntVar v : decVars) {
//                                    out.printf("%d ", v.getValue());
//                                }
//                                out.println();
                                out.println("solution: "+solver.getNodeCount());
                            }
                            node = solver.getNodeCount();
//                            propNum = Measurer.propNum;
                            time += solver.getTimeCount() / runNum;
//                            matchingTime += Measurer.matchingTime / IN_SEC / runNum;
//                            filterTime += Measurer.filterTime / IN_SEC / runNum;
                        }
//                        bw.write("," + algorithm + "," + node + "," + propNum + "," + time + "," + matchingTime + "," + filterTime);
                        bw.write(","+algorithm +"," + node + "," + time);
                        bw.flush();
                    }
                    bw.newLine();
                }
                bw.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
