package de.tum.in.pet.implementation.qp_meanpayoff;

import de.tum.in.naturals.set.NatBitSet;
import gurobi.*;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;

public class StochasticGameLPForLowerBound {
    private Int2ObjectMap<Int2ObjectMap<GRBVar>> x_a;
    private final StochasticGameMec sg;

    private GRBEnv env;
    private GRBModel model;

    private Int2ObjectMap<Int2DoubleMap> Player1Strategy;

    private final boolean enableLog;

    public StochasticGameLPForLowerBound (StochasticGameMec sg, Int2ObjectMap<Int2DoubleMap> Player1Strategy, boolean enableLog)
    {
        this.sg = sg;
        this.enableLog = enableLog;
        this.Player1Strategy = Player1Strategy;
    }
    public double solveForMeanPayoff() throws GRBException
    {
        createGurobiEnv();
        createGurobiModel();
        initializeVariables();
        writeLPConstraints();
        setObjectiveFunction();
        double value = optimizeModel();
        disposeGurobiModel();
        disposeGurobiEnv();
        if (enableLog) {
            System.out.println("MeanPayoff is " + value);
        }
        return value;
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

    private void initializeVariables() throws GRBException {
        x_a = new Int2ObjectOpenHashMap<>();

        NatBitSet statesP1 = sg.player1();         //changes here as only a single action from Player 1 states
        for (int state : statesP1) {
            Int2ObjectMap<GRBVar> xa_action_vars = new Int2ObjectOpenHashMap<>();
            GRBVar xa_action_var = model.addVar(0, 1, 0, GRB.CONTINUOUS, "x_a(" + state + ", " + 1 + ")");
            xa_action_vars.put(1, xa_action_var);
            x_a.put(state, xa_action_vars);
        }
        NatBitSet statesP2 = sg.player2();

        for (int state : statesP2) {
            IntSet actions = sg.getActionP2(state);
            Int2ObjectMap<GRBVar> xa_action_vars = new Int2ObjectOpenHashMap<>();

            for (int action : actions) {
                GRBVar xa_action_var = model.addVar(0, 1, 0, GRB.CONTINUOUS, "x_a(" + state + ", " + action + ")");
                xa_action_vars.put(action, xa_action_var);
            }
            x_a.put(state, xa_action_vars);
        }
    }
    private void writeLPConstraints() throws GRBException {
        writeConstraints();
    }

    // Bounds for transition probabilities
    private void writeConstraints() throws GRBException {
        model.set(GRB.IntParam.NonConvex, 2);
        NatBitSet statesP1 = sg.player1();
        Int2ObjectMap<GRBLinExpr> x_a_lhs_exprs = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<GRBLinExpr> x_a_rhs_exprs = new Int2ObjectOpenHashMap<>();

        GRBLinExpr c5_lhs_expr = new GRBLinExpr();
        for (int state : statesP1) {
            // Sum of all x_a should be equal to 1, only 1 action for a PLayer 1 state
            c5_lhs_expr.addTerm(1, x_a.get(state).get(1));
            IntSet actions = sg.getActionP1(state);
            for (int action : actions) {
                addTransitionProbabilityConstraintsP1(state, action, x_a_lhs_exprs);
            }
            // Only a single action to be added to RHS expr
            getExprForState(x_a_rhs_exprs, state).addTerm(1, x_a.get(state).get(1));
        }
        NatBitSet statesP2 = sg.player2();

        for (int state : statesP2) {
            IntSet actions = sg.getActionP2(state);
            for (int action : actions) {
                // Sum of all x_a should be equal to 1
                c5_lhs_expr.addTerm(1, x_a.get(state).get(action));

                addTransitionProbabilityConstraintsP2(state, action, x_a_lhs_exprs);

                // Add current action to rhs_exprs
                getExprForState(x_a_rhs_exprs, state).addTerm(1, x_a.get(state).get(action));
            }
        }

        for (int state: statesP1) {

            GRBLinExpr lhs_expr = x_a_lhs_exprs.getOrDefault(state, null);
            GRBLinExpr rhs_expr = x_a_rhs_exprs.getOrDefault(state, null);
            model.addConstr(lhs_expr, GRB.EQUAL, rhs_expr, "C4_" + state);
        }

        for (int state: statesP2) {

            GRBLinExpr lhs_expr = x_a_lhs_exprs.getOrDefault(state, null);
            GRBLinExpr rhs_expr = x_a_rhs_exprs.getOrDefault(state, null);
            model.addConstr(lhs_expr, GRB.EQUAL, rhs_expr, "C4_" + state);
        }

        model.addConstr(c5_lhs_expr, GRB.EQUAL, 1d, "C5");


    }


    private GRBLinExpr getExprForState(Int2ObjectMap<GRBLinExpr> exprs, int state) {
        GRBLinExpr exp = exprs.getOrDefault(state, null);
        if (exp == null) {
            exp = new GRBLinExpr();
            exprs.put(state, exp);
        }
        return exp;
    }

    private void addTransitionProbabilityConstraintsP1(int state, int action, Int2ObjectMap<GRBLinExpr> lhs_exprs) throws GRBException {
        int target = sg.getP2Successor(state, action);
        double strategyval = Player1Strategy.get(state).get(action);
        getExprForState(lhs_exprs, target).addTerm( strategyval, x_a.get(state).get(1));
    }

    private void addTransitionProbabilityConstraintsP2(int state, int action, Int2ObjectMap<GRBLinExpr> lhs_exprs) throws GRBException {
        GameDistribution gd = sg.getP2distribution(state,action);
        int [] successors = gd.getSuccessors();
        double [] estimated_probabilities = gd.getProbabilities();
        for (int i = 0 ; i < successors.length ; i++ ) {
            double estimatedProb = rounded(estimated_probabilities[i]);
            int target = successors[i];
            getExprForState(lhs_exprs, target).addTerm( estimatedProb, x_a.get(state).get(action));
        }
    }

    private void setObjectiveFunction() throws GRBException {
        GRBLinExpr objectiveExpr = new GRBLinExpr();


        NatBitSet statesP1 = sg.player1();
        for (int state : statesP1) {
            double rewardsP1 = 0.0;
            IntSet actions = sg.getActionP1(state);
            for (int action : actions) {
                double r = sg.getRewardP1(state,action);
                rewardsP1 += r * Player1Strategy.get(state).get(action);
            }
            objectiveExpr.addTerm(rewardsP1, x_a.get(state).get(1));
        }

//        objectiveExpr.addConstant(rewardsP1);
        NatBitSet statesP2 = sg.player2();
        for (int state : statesP2) {
            IntSet actions = sg.getActionP2(state);
            for (int action : actions) {
                double r =  sg.getRewardP2(state);
                objectiveExpr.addTerm(r, x_a.get(state).get(action));
            }
        }

        model.setObjective(objectiveExpr, GRB.MINIMIZE);
    }


    private void setLowerBoundObjectiveFunction() throws GRBException {
        GRBLinExpr objectiveExpr = new GRBLinExpr();

        NatBitSet statesP1 = sg.player1();
        for (int state : statesP1) {
            IntSet actions = sg.getActionP1(state);
            for (int action : actions) {
                double r = sg.getRewardP1(state,action);
                objectiveExpr.addTerm(r, x_a.get(state).get(action));
            }
        }
        NatBitSet statesP2 = sg.player2();
        for (int state : statesP2) {
            IntSet actions = sg.getActionP2(state);
            for (int action : actions) {
                double r = sg.getRewardP2(state) * -1;
                objectiveExpr.addTerm(r, x_a.get(state).get(action));
            }
        }

        model.setObjective(objectiveExpr, GRB.MAXIMIZE);
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

    private double rounded (double val)
    {
        double round_val = Math.round(val * 10000.0) / 10000.0;
        return round_val;
    }

    private double getMeanPayoff() throws GRBException
    {
        Int2ObjectMap<Int2DoubleMap> xa_values = new Int2ObjectOpenHashMap<>();
        NatBitSet statesP1 = sg.player1();
        double meanPayoff = 0;
        for(int state : statesP1)
        {
            IntSet actions = sg.getActionP1(state);
            for(int action : actions)
            {
                GRBVar v = x_a.get(state).get(action);
                double value = v.get(GRB.DoubleAttr.X);
                double reward = sg.getRewardP1(state,action);
                meanPayoff += value*reward;
            }

        }
        NatBitSet statesP2 = sg.player2();

        for(int state : statesP2)
        {
            IntSet actions = sg.getActionP2(state);
            for(int action : actions)
            {
                GRBVar v = x_a.get(state).get(action);
                double value = v.get(GRB.DoubleAttr.X);
                double reward = sg.getRewardP2(state);
                meanPayoff += value*reward;
            }

        }
        return meanPayoff;
    }

}
