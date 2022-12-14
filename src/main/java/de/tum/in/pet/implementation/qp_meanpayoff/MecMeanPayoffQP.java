package de.tum.in.pet.implementation.qp_meanpayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.probmodels.model.Distribution;
import gurobi.*;
import it.unimi.dsi.fastutil.ints.*;

public class MecMeanPayoffQP {
    private final MecInformationProvider mecInfoProvider;

    private final LPRewardProvider rewardProvider;

    private final boolean enableLog;

    private GRBEnv env;
    private GRBModel model;
    private double pMin;


    private Int2ObjectMap<Int2DoubleMap> final_values;

    // For every transition, there is a gurobi variable
    private Int2ObjectMap<Int2ObjectMap<GRBVar[]>> t;

    // For every state-action pair, variable x_a is present
    private Int2ObjectMap<Int2ObjectMap<GRBVar>> x_a;

    public MecMeanPayoffQP(MecInformationProvider mecInfoProvider, LPRewardProvider rewardProvider, double pMin, boolean enableLog) {
        this.mecInfoProvider = mecInfoProvider;
        this.rewardProvider = rewardProvider;
        this.enableLog = enableLog;
        this.pMin = pMin;
        this.final_values = new Int2ObjectOpenHashMap<>();
    }

    public double solveForMeanPayoff() throws GRBException {
        createGurobiEnv();
        createGurobiModel();
        initializeVariables();
        writeLPConstraints();
        setObjectiveFunction();
        double value = optimizeModel();
//       xa_values = getStrategyValues();
        getStrategyValues();
        disposeGurobiModel();
        disposeGurobiEnv();
        if (enableLog) {
            System.out.println("MeanPayoff is " + value);
        }
        return value;
    }

    private void initializeVariables() throws GRBException {
        t = new Int2ObjectOpenHashMap<>();
        x_a = new Int2ObjectOpenHashMap<>();

        NatBitSet states = mecInfoProvider.provideStates();
        for (int state : states) {
            IntSet actions = mecInfoProvider.provideActions(state);
            Int2ObjectMap<GRBVar[]> ta = new Int2ObjectOpenHashMap<>();
            Int2ObjectMap<GRBVar> xa_action_vars = new Int2ObjectOpenHashMap<>();

            for (int action : actions) {
                Distribution distribution = mecInfoProvider.provideDistribution(state, action);
                int numTransitions = distribution.size();

                double[] lb = new double[numTransitions];
                double[] ub = new double[numTransitions];
                double[] obj = new double[numTransitions];
                char[] type = new char[numTransitions];
                String[] names = new String[numTransitions];

                int transition=0;
                for (Int2DoubleMap.Entry entry : distribution) {
                    int target = entry.getIntKey();
                    lb[transition] = 0;
                    ub[transition] = 1;
                    obj[transition] = 0;
                    type[transition] = GRB.CONTINUOUS;
                    names[transition] = "t(" + state + ", " + action + ", " + target + ")";
                    transition++;
                }

                GRBVar[] vars = model.addVars(lb, ub, obj, type, names);
                ta.put(action, vars);

                GRBVar xa_action_var = model.addVar(0, 1, 0, GRB.CONTINUOUS, "x_a(" + state + ", " + action + ")");
                xa_action_vars.put(action, xa_action_var);
            }

            t.put(state, ta);
            x_a.put(state, xa_action_vars);
        }
    }

    private void writeLPConstraints() throws GRBException {
        writeConstraints();
    }

    // Bounds for transition probabilities
    private void writeConstraints() throws GRBException {
        model.set(GRB.IntParam.NonConvex, 2);
        NatBitSet states = mecInfoProvider.provideStates();
        Int2ObjectMap<GRBQuadExpr> x_a_lhs_exprs = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<GRBQuadExpr> x_a_rhs_exprs = new Int2ObjectOpenHashMap<>();

        GRBLinExpr c5_lhs_expr = new GRBLinExpr();
        for (int state : states) {
            IntSet actions = mecInfoProvider.provideActions(state);
            for (int action : actions) {
                // Sum of all x_a should be equal to 1
                c5_lhs_expr.addTerm(1, x_a.get(state).get(action));

                addTransitionProbabilityConstraints(state, action, x_a_lhs_exprs);

                // Add current action to rhs_exprs
                getExprForState(x_a_rhs_exprs, state).addTerm(1, x_a.get(state).get(action));
            }
        }

        for (int state: states) {

            GRBQuadExpr lhs_expr = x_a_lhs_exprs.getOrDefault(state, null);
            GRBQuadExpr rhs_expr = x_a_rhs_exprs.getOrDefault(state, null);

            model.addQConstr(lhs_expr, GRB.EQUAL, rhs_expr, "C4_" + state);
        }

        model.addConstr(c5_lhs_expr, GRB.EQUAL, 1d, "C5");
    }

    private GRBQuadExpr getExprForState(Int2ObjectMap<GRBQuadExpr> exprs, int state) {
        GRBQuadExpr exp = exprs.getOrDefault(state, null);
        if (exp == null) {
            exp = new GRBQuadExpr();
            exprs.put(state, exp);
        }
        return exp;
    }

    private void addTransitionProbabilityConstraints(int state, int action, Int2ObjectMap<GRBQuadExpr> lhs_exprs) throws GRBException {
        Distribution distribution = mecInfoProvider.provideDistribution(state, action);
        double confidenceWidth = rounded(mecInfoProvider.provideTwoSidedConfidenceWidth(state, action));
//        System.out.println("Width is 2: "+confidenceWidth);
        GRBLinExpr lhs_sumToOneConstraint = new GRBLinExpr();

        int i = 0;
        for (Int2DoubleMap.Entry entry : distribution) {
            double estimatedProb = rounded(entry.getDoubleValue());
            int target = entry.getIntKey();

            GRBLinExpr expr = new GRBLinExpr();
            expr.addTerm(1, t.get(state).get(action)[i]);
            double c1_prob = estimatedProb + confidenceWidth;
            c1_prob = (c1_prob > 1) ? 1 : c1_prob;
            model.addConstr(expr, GRB.LESS_EQUAL, c1_prob, "C1( " + state + ", " + action + ", " + target + ")");

            GRBLinExpr expr2 = new GRBLinExpr();
            expr2.addTerm(1, t.get(state).get(action)[i]);
            double c2_prob = estimatedProb-confidenceWidth;
            c2_prob = Math.max(c2_prob, pMin);
            model.addConstr(expr2, GRB.GREATER_EQUAL, c2_prob, "C2( " + state + ", " + action + ", " + target + ")");

            lhs_sumToOneConstraint.addTerm(1, t.get(state).get(action)[i]);

            getExprForState(lhs_exprs, target).addTerm(1, x_a.get(state).get(action), t.get(state).get(action)[i]);

            i++;
        }

        model.addConstr(lhs_sumToOneConstraint, GRB.EQUAL, 1d, "C3(" + state + ", " + action + ")");
    }

    private void setObjectiveFunction() throws GRBException {
        GRBLinExpr objectiveExpr = new GRBLinExpr();

        NatBitSet states = mecInfoProvider.provideStates();
        for (int state : states) {
            IntSet actions = mecInfoProvider.provideActions(state);
            for (int action : actions) {
                double transReward = rewardProvider.transitionReward(state, action, null);
                double stateReward = rewardProvider.stateReward(state);
                double r = stateReward + transReward;

                objectiveExpr.addTerm(r, x_a.get(state).get(action));
            }
        }

        model.setObjective(objectiveExpr, GRB.MAXIMIZE);
    }

    private void createGurobiEnv() throws GRBException {
        env = new GRBEnv(true);

        if (enableLog) {
            env.set("logFile", "lpLog.log");
            env.set(GRB.IntParam.OutputFlag, 1);
        } else {
            env.set(GRB.IntParam.OutputFlag, 0);
        }
        env.start();
    }

    private void createGurobiModel() throws GRBException {
        model = new GRBModel(env);
    }

    private double optimizeModel() throws GRBException {
        if (enableLog) {
            model.write("gurobimodel.lp");
        }
        model.optimize();

        GRBLinExpr objective = (GRBLinExpr) model.getObjective();
        return objective.getValue();
    }

    private void disposeGurobiModel() {
        model.dispose();
    }

    private void disposeGurobiEnv() throws GRBException {
        env.dispose();
    }

    private void forEachIncomingTransition(int state, IncomingTransitionConsumer consumer) {
//        for (int s = 0; s < mecInfoProvider.provideStates().size(); s++) {
//            for (int action = 0; action < mdp.getActions(s).size(); action++) {
//
//                int finalAction = action;
//                int finalS = s;
//
//                mdp.forEachTransition(s, action, (destination, probability) -> {
//                    if (destination == state) {
//                        consumer.accept(finalS, probability, destination, finalAction, mdp.getAction(finalS, finalAction));
//                    }
//                });
//            }
//        }
    }

    private void printAllVars() throws GRBException {
        NatBitSet states = mecInfoProvider.provideStates();
        for (int state : states) {
            IntSet actions = mecInfoProvider.provideActions(state);
            for (int action : actions) {
                printVar(state, action);
            }
        }
    }

    private void printVar(int state, int action) throws GRBException {
        GRBVar[] t_var = t.get(state).get(action);
        GRBVar x_a_var = x_a.get(state).get(action);

        System.out.println("For state " + state + " action " + action);
        System.out.println("t_var length is " + t_var.length);
        System.out.println("x_a_var name is " + x_a_var.get(GRB.StringAttr.VarName));
        System.out.println("\n");
        System.out.println("\n");
    }
    private double rounded (double val)
    {
        double round_val = Math.round(val * 10000.0) / 10000.0;
        return round_val;
    }
    private void getStrategyValues() throws GRBException //Adds the xa_values obtainned using qp to the variable final
    //final_values.
    {
//        Int2ObjectMap<Int2DoubleMap> xa_values = new Int2ObjectOpenHashMap<>();
        NatBitSet states = mecInfoProvider.provideStates();
        for(int state : states)
        {
            Int2DoubleMap actionset = new Int2DoubleOpenHashMap();
            IntSet actions = mecInfoProvider.provideActions(state);
            double sum = 0.0;
            for(int action : actions)
            {
                GRBVar v = x_a.get(state).get(action);
                double value = v.get(GRB.DoubleAttr.X);
//                System.out.println("Value is :"+ value + " state "+ state + " action " + action);
                sum += value;
            }
            if (sum == 0)
            {sum = 1;}

            for(int action : actions)
            {
                GRBVar v = x_a.get(state).get(action);
                double value = v.get(GRB.DoubleAttr.X);
                actionset.put(action, value/sum);
//                System.out.println(value/sum);
            }
            final_values.put(state, actionset);
        }
    }

    public Int2ObjectMap<Int2DoubleMap> getFinalValues ()
    {
        return final_values;
    }

}
