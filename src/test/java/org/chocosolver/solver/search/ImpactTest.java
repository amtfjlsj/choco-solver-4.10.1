/*
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2019, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.solver.search;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.strategy.selectors.variables.ImpactBased;
import org.chocosolver.solver.search.strategy.selectors.variables.ImpactBasedOpt;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.ProblemMaker;
import org.testng.annotations.Test;

import static java.lang.System.out;
import static org.chocosolver.solver.search.strategy.Search.domOverWDegSearch;
import static org.testng.Assert.assertEquals;

/**
 * @author Jean-Guillaume Fages
 * @since 22/04/15
 * Created by IntelliJ IDEA.
 */
public class ImpactTest {

    @Test(groups = "10s", timeOut = 600000)
    public void testCostas() {
        out.println("ImpactBasedOpt");
        Model m1 = costasArray(9, false);
        Solver s1 = m1.getSolver();
        s1.solve() ;
//        while (s1.solve()) ;
        out.println(s1.getSolutionCount());
        out.println(s1.getNodeCount());
        out.println(s1.getTimeCount());

        out.println("ImpactBased");
        Model m2 = costasArray(9, true);
        Solver s2 = m2.getSolver();
        s2.solve() ;
//        while (s2.solve()) ;
        out.println(s2.getSolutionCount());
        out.println(s2.getNodeCount());
        out.println(s2.getTimeCount());


//        assertEquals(m1.getSolver().getSolutionCount(), m2.getSolver().getSolutionCount());
    }

    private Model costasArray(int n, boolean impact) {
        Model model = ProblemMaker.makeCostasArrays(n);
        IntVar[] vectors = (IntVar[]) model.getHook("vectors");

        Solver r = model.getSolver();
//        r.limitTime(20000);
        if (impact) {
            r.setSearch(new ImpactBased(vectors, 2, 5, 10, 0, true));
        }
//        else {
//            r.setSearch(domOverWDegSearch(vectors));
//        }
        else {
            r.setSearch(new ImpactBasedOpt(vectors, 2, 5, 10, 0, true));
        }
        return model;
    }
}
