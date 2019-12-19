package org.chocosolver.solver.search.strategy.selectors.variables;

import gnu.trove.list.array.TIntArrayList;
import org.chocosolver.memory.IStateInt;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.search.loop.monitors.IMonitorContradiction;
import org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory;
import org.chocosolver.solver.search.strategy.decision.Decision;
import org.chocosolver.solver.search.strategy.selectors.values.IntValueSelector;
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.objects.IntMap;

import java.util.stream.Stream;

public class DomOverWDegAbsCon extends AbstractStrategy<IntVar> implements IMonitorContradiction {

    /**
     * Kind of duplicate of pid2ari to limit calls of backtrackable objects
     */
    private IntMap pid2arity;

    /**
     * Temporary. Stores index of variables with the same (best) score
     */
    private TIntArrayList bests;

    /**
     * Randomness to break ties
     */
    private java.util.Random random;

    /**
     * The way value is selected for a given variable
     */
    private IntValueSelector valueSelector;

    /***
     * Pointer to the last uninstantiated variable
     */
    private IStateInt last;
    /**
     * Map (propagator - weight), where weight is the number of times the propagator fails.
     */
//    protected IntMap p2w;
    protected double[][] ss;

    private int prolength;
    private final IntMap v2i;
    private final IntMap p2i;


    /**
     * Creates a DomOverWDeg variable selector
     *
     * @param variables     decision variables
     * @param seed          seed for breaking ties randomly
     * @param valueSelector a value selector
     */
    public DomOverWDegAbsCon(IntVar[] variables, long seed, IntValueSelector valueSelector) {
        super(variables);
        Model model = variables[0].getModel();
        pid2arity = new IntMap(model.getCstrs().length * 3 / 2 + 1, -1);
        bests = new TIntArrayList();
        this.valueSelector = valueSelector;
        random = new java.util.Random(seed);
        this.last = model.getEnvironment().makeInt(vars.length - 1);
//        p2w = new IntMap(10, 0);
//        ss=new  double[variables.length];
        this.v2i = new IntMap(vars.length);
        this.p2i = new IntMap(Stream.of(model.getCstrs())
                .flatMap(c -> Stream.of(c.getPropagators()))
                .toArray(Propagator[]::new).length);

        init(Stream.of(model.getCstrs())
                .flatMap(c -> Stream.of(c.getPropagators()))
                .toArray(Propagator[]::new));

    }

    private void init(Propagator[] propagators) {
//        for (Propagator propagator : propagators) {
//            p2w.put(propagator.getId(), 0);
//        }
        for (int i = 0; i < vars.length; i++) {
            v2i.put(vars[i].getId(), i);
        }
        for (int i = 0; i < propagators.length; i++) {
            p2i.put(propagators[i].getId(), i);
        }
        prolength=propagators.length;
        ss=new  double[propagators.length][];
        for (Propagator propagator : propagators) {
//            System.out.println(propagator.getId()-vars.length);
            ss[p2i.get(propagator.getId())]= new double[vars.length];        //所有约束所有变量
        }
        for (Propagator propagator : propagators) {
            for(int i=0;i<vars.length;i++)
                ss[p2i.get(propagator.getId())][i]= 0;
        }
    }

    @Override
    public boolean init() {
        Solver solver = vars[0].getModel().getSolver();
        if(!solver.getSearchMonitors().contains(this)) {
            vars[0].getModel().getSolver().plugMonitor(this);
        }
        return true;
    }

    @Override
    public void remove() {
        Solver solver = vars[0].getModel().getSolver();
        if(solver.getSearchMonitors().contains(this)) {
            vars[0].getModel().getSolver().unplugMonitor(this);
        }
    }

    @Override
    public void onContradiction(ContradictionException cex) {//冲突发生
        if (cex.c instanceof Propagator) {
            Propagator p = (Propagator) cex.c;
//            p2w.putOrAdjust(p.getId(), 1, 1);
            int to = last.get();
            for (int idx = 0; idx <= to; idx++) {       //未来变量加1
                ss[p2i.get(p.getId())][v2i.get(vars[idx].getId())]+=1;
            }
        }
    }


    @Override
    public Decision<IntVar> computeDecision(IntVar variable) {
        if (variable == null || variable.isInstantiated()) {
            return null;
        }
        int currentVal = valueSelector.selectValue(variable);
//        System.out.println(variable.getId()-1+"  "+currentVal);
        return variable.getModel().getSolver().getDecisionPath().makeIntDecision(variable, DecisionOperatorFactory.makeIntEq(), currentVal);
    }

    @Override
    public Decision<IntVar> getDecision() {

//        for(int i=0;i<prolength;i++){
//            for(int j=0;j<vars.length;j++){
//                System.out.print(ss[i][j]+" ");
//            }
//            System.out.println();
//        }

        IntVar best = null;
        bests.resetQuick();
        pid2arity.clear();
        long _d1 = Integer.MAX_VALUE;
        long _d2 = 0;
        int to = last.get();
        for (int idx = 0; idx <= to; idx++) {
            int dsize = vars[idx].getDomainSize();
            if (dsize > 1) {
                int weight = weight(vars[idx]);
                long c1 = dsize * _d2;
                long c2 = _d1 * weight;
                if (c1 < c2) {
                    bests.resetQuick();
                    bests.add(idx);
                    _d1 = dsize;
                    _d2 = weight;
                } else if (c1 == c2) {
                    bests.add(idx);
                }
            } else {
                // swap
                IntVar tmp = vars[to];
                vars[to] = vars[idx];
                vars[idx] = tmp;
                idx--;
                to--;
            }
        }
        last.set(to);
        if (bests.size() > 0) {
            int currentVar = bests.get(random.nextInt(bests.size()));
            best = vars[currentVar];
        }
        return computeDecision(best);
    }

    private int weight(IntVar v) {
        int w = 1;
        int nbp = v.getNbProps();
        for (int i = 0; i < nbp; i++) {
            Propagator prop = v.getPropagator(i);
            int pid = prop.getId();
            // if the propagator has been already evaluated
            if (pid2arity.get(pid) > -1) {
//                w += p2w.get(prop.getId());
                w+=ss[p2i.get(prop.getId())][v2i.get(v.getId())];
            } else {
                // the arity of this propagator is not yet known
                int futVars = prop.arity();
                assert futVars > -1;
                pid2arity.put(pid, futVars);
                if (futVars > 1) {
//                    w += p2w.get(prop.getId());
                    w+=ss[p2i.get(prop.getId())][v2i.get(v.getId())];
                }
            }
        }
        return w;
    }
}
