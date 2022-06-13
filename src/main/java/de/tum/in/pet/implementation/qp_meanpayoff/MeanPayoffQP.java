package de.tum.in.pet.implementation.qp_meanpayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.model.MarkovDecisionProcess;
import gurobi.*;
import it.unimi.dsi.fastutil.ints.*;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MeanPayoffQP {
    private final MarkovDecisionProcess MDP;

    private final LPRewardProvider rewardProvider;

    private final List<NatBitSet> components;

    private final Int2ObjectFunction<Int2DoubleFunction> confidenceWidthFunction;

    private final Int2ObjectFunction<Int2BooleanFunction> validStateActionPairInMECDetector;
    private final boolean enableLog;

    private GRBEnv env;
    private GRBModel model;

    // For every transition, there is a gurobi variable
    private Int2ObjectMap<Int2ObjectMap<GRBVar[]>> t;

    // For every state-action pair, variable x_a is present
    private Int2ObjectMap<GRBVar[]> x_a;

    // For every state action pair we will have y_a variable
    private Int2ObjectMap<GRBVar[]> y_a;

    // For every state we will have an instance of GRBVar
    private GRBVar[] y_s;

    public MeanPayoffQP(MarkovDecisionProcess MDP, List<NatBitSet> components, LPRewardProvider rewardProvider, Int2ObjectFunction<Int2DoubleFunction> confidenceWidthFunction, Int2ObjectFunction<Int2BooleanFunction> validStateActionPairInMECDetector, boolean enableLog) {
        this.MDP = MDP;
        this.rewardProvider = rewardProvider;
        this.components = components;
        this.confidenceWidthFunction = confidenceWidthFunction;
        this.validStateActionPairInMECDetector = validStateActionPairInMECDetector;
        this.enableLog = enableLog;
    }

    public double solveForMeanPayoff() throws GRBException {
        createGurobiEnv();
        createGurobiModel();
        initializeVariables();
        writeConstraints();
        setObjectiveFunction();
        double value = optimizeModel();
        if (enableLog) {
//            printAllVars();
            System.out.println("MeanPayoff is " + value);
        }
        disposeGurobiModel();
        disposeGurobiEnv();
        return value;
    }

    private void initializeVariables() throws GRBException {
        int numStates = MDP.getNumStates();

        t = new Int2ObjectOpenHashMap<>();
        x_a = new Int2ObjectOpenHashMap<>();
        y_a = new Int2ObjectOpenHashMap<>();

        //Initialize variables for y_s for all states
        double[] ys_lb = new double[numStates];
        double[] ys_ub = new double[numStates];
        double[] ys_obj = new double[numStates];
        char[] ys_type = new char[numStates];
        String[] ys_names = new String[numStates];

        for (int state = 0; state < numStates; state++) {

            //Initialize value for ys for this state
            ys_lb[state] = 0;
            ys_ub[state] = 1;
            ys_obj[state] = 0;
            ys_type[state] = GRB.CONTINUOUS;
            ys_names[state] = "y_s_" + state;

            int numActions = MDP.getNumChoices(state);
            Int2ObjectMap<GRBVar[]> ta = new Int2ObjectOpenHashMap<>();
            Int2ObjectMap<GRBVar> xa_action_vars = new Int2ObjectOpenHashMap<>();

            // Initialize x_a variable for all actions in this state
            double[] xa_lb = new double[numActions];
            double[] xa_ub = new double[numActions];
            double[] xa_obj = new double[numActions];
            char[] xa_type = new char[numActions];
            String[] xa_names = new String[numActions];

            // Initialize y_a variable for all actions in this state
            double[] ya_lb = new double[numActions];
            double[] ya_ub = new double[numActions];
            double[] ya_obj = new double[numActions];
            char[] ya_type = new char[numActions];
            String[] ya_names = new String[numActions];

            for (int action = 0; action < numActions; action++) {

                // Initialize values to x_a variable of this state-action
                xa_lb[action] = 0;
                xa_ub[action] = 1;
                xa_obj[action] = 0;
                xa_type[action] = GRB.CONTINUOUS;
                xa_names[action] = "x_a(" + state + ", " + action + ")";

                // Initialize values to y_a variable of this state-action
                ya_lb[action] = 0;
                ya_ub[action] = 1;
                ya_obj[action] = 0;
                ya_type[action] = GRB.CONTINUOUS;
                ya_names[action] = "y_a(" + state + ", " + action + ")";

                Distribution distribution = MDP.getChoice(state, action);
                int numTransitions = distribution.size();

                // Initialize t variable for all transitions of this state-action pair
                double[] lb = new double[numTransitions];
                double[] ub = new double[numTransitions];
                double[] obj = new double[numTransitions];
                char[] type = new char[numTransitions];
                String[] names = new String[numTransitions];

                int transition = 0;
                for (Int2DoubleMap.Entry entry : distribution) {
                    // Set values to t variable for this transition (state-action-target)
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
            }

            t.put(state, ta);
            x_a.put(state, model.addVars(xa_lb, xa_ub, xa_obj, xa_type, xa_names));
            y_a.put(state, model.addVars(ya_lb, ya_ub, ya_obj, ya_type, ya_names));
        }

        y_s = model.addVars(ys_lb, ys_ub, ys_obj, ys_type, ys_names);
    }

    private void writeConstraints() throws GRBException {
        model.set(GRB.IntParam.NonConvex, 2);

        // Variables for Constraint 4
        Int2ObjectMap<GRBQuadExpr> x_a_lhs_exprs = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<GRBQuadExpr> x_a_rhs_exprs = new Int2ObjectOpenHashMap<>();

        // Variables for Constraint 1
        Int2ObjectMap<GRBQuadExpr> y_a_lhs_exprs = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<GRBQuadExpr> y_a_rhs_exprs = new Int2ObjectOpenHashMap<>();

        int numStates = MDP.getNumStates();
        for (int state = 0; state < numStates; state++) {
            int numActions = MDP.getNumChoices(state);

            for (int action = 0; action < numActions; action++) {
                Distribution distribution = MDP.getChoice(state, action);
                double confidenceWidth = confidenceWidthFunction.apply(state).apply(action);

                if (confidenceWidth > 1) {
                    confidenceWidth = 1;
                }

                if (confidenceWidth < 0) {
                    confidenceWidth = 0;
                }

                if (Double.isNaN(confidenceWidth)) {
                    Logger.getLogger("QP").log(Level.WARNING, "CW is NAN for " + state + " " + action );
                }

                // Add current action to rhs_exprs of Constraint 4
                getExprForState(x_a_rhs_exprs, state).addTerm(1, x_a.get(state)[action]);

                // Add current action to rhs_exprs of Constraint 1
                getExprForState(y_a_rhs_exprs, state).addTerm(1, y_a.get(state)[action]);

                GRBLinExpr probSumToOneConstraint = new GRBLinExpr();

                int i = 0;
                for (Int2DoubleMap.Entry entry : distribution) {
                    double estimatedProb = entry.getDoubleValue();
                    int target = entry.getIntKey();

                    // Constraint 1 : Transition Probability <= Estimated Probability + Confidence Width
                    GRBLinExpr expr = new GRBLinExpr();
                    expr.addTerm(1, t.get(state).get(action)[i]);
                    double c1_prob = estimatedProb + confidenceWidth;
                    c1_prob = (c1_prob > 1) ? 1 : c1_prob;

                    if (Double.isNaN(c1_prob)) {
                        Logger.getLogger("QP").log(Level.WARNING, "C1_prob is NAN for " + state + " " + action );
                    }

                    model.addConstr(expr, GRB.LESS_EQUAL, c1_prob, "C0a( " + state + ", " + action + ", " + target + ")");

                    // Constraint 2 : Transition Probability >= Estimated Probability - Confidence Width
                    GRBLinExpr expr2 = new GRBLinExpr();
                    expr2.addTerm(1, t.get(state).get(action)[i]);
                    double c2_prob = estimatedProb - confidenceWidth;
                    c2_prob = (c2_prob < 0) ? 0 : c2_prob;

                    if (Double.isNaN(c2_prob)) {
                        Logger.getLogger("QP").log(Level.WARNING, "C2_prob is NAN for " + state + " " + action );
                    }

                    model.addConstr(expr2, GRB.GREATER_EQUAL, c2_prob, "C0b( " + state + ", " + action + ", " + target + ")");

                    // Constraint 3 : Probability sum equal to 1
                    probSumToOneConstraint.addTerm(1, t.get(state).get(action)[i]);

                    // Add x_a * \delta(a)(s) to lhs of Constraint 4
                    getExprForState(x_a_lhs_exprs, target).addTerm(1, x_a.get(state)[action], t.get(state).get(action)[i]);

                    // Add y_a * \delta(a)(s) to lhs of Constraint 1
                    getExprForState(y_a_lhs_exprs, target).addTerm(1, y_a.get(state)[action], t.get(state).get(action)[i]);
                    i++;
                }

                if (distribution.size() > 0) {
                    model.addConstr(probSumToOneConstraint, GRB.EQUAL, 1d, "C0c(" + state + ", " + action + ")");
                }
            }
        }


        for (int state = 0; state < numStates; state++) {
            // Constraint 4
            GRBQuadExpr lhs_expr = x_a_lhs_exprs.getOrDefault(state, null);
            GRBQuadExpr rhs_expr = x_a_rhs_exprs.getOrDefault(state, null);

            if (rhs_expr == null) {
                rhs_expr = new GRBQuadExpr();
                rhs_expr.addConstant(0);
            }

            if (lhs_expr == null) {
                lhs_expr = new GRBQuadExpr();
                lhs_expr.addConstant(0);
            }


            model.addQConstr(lhs_expr, GRB.EQUAL, rhs_expr, "C4_" + state);

            // Constraint 1
            GRBQuadExpr ya_lhs = y_a_lhs_exprs.getOrDefault(state, null);
            GRBQuadExpr ya_rhs = y_a_rhs_exprs.getOrDefault(state, null);

            if (ya_rhs == null) {
                ya_rhs = new GRBQuadExpr();
                ya_rhs.addConstant(0);
            }

            if (ya_lhs == null) {
                ya_lhs = new GRBQuadExpr();
                ya_lhs.addConstant(0);
            }

            if (MDP.isInitialState(state)) {
                ya_lhs.addConstant(1d);
            }

            ya_rhs.addTerm(1, y_s[state]);
            model.addQConstr(ya_lhs, GRB.EQUAL, ya_rhs, "C1_" + state);
        }

        // Constraint 2 and 3
        GRBLinExpr cons2_lhs = new GRBLinExpr();
        for (int i = 0; i < components.size(); i++) {
            NatBitSet component = components.get(i);

            GRBLinExpr cons3_lhs = new GRBLinExpr();
            GRBLinExpr cons3_rhs = new GRBLinExpr();

            for (int mecState : component) {
                cons2_lhs.addTerm(1, y_s[mecState]);
                cons3_lhs.addTerm(1, y_s[mecState]);

                int numActions = MDP.getNumChoices(mecState);
                for (int action = 0; action < numActions; action++) {
                    Distribution distribution = MDP.getChoice(mecState, action);

                    // TODO Check this action is visited at-least required number of times
                    if (component.containsAll(distribution.support()) && isAValidMecAction(mecState, action)) {
                        cons3_rhs.addTerm(1, x_a.get(mecState)[action]);
                    }
                }
            }

            // Constraint 3 : y_s = x_a
            model.addConstr(cons3_lhs, GRB.EQUAL, cons3_rhs, "C3_" + i);
        }

        // Constraint 2 : y_s = 1
        model.addConstr(cons2_lhs, GRB.EQUAL, 1d, "C2");
    }

    private boolean isAValidMecAction(int mecState, int action) {
        return validStateActionPairInMECDetector.apply(mecState).apply(action);
    }

    private void setObjectiveFunction() throws GRBException {
        GRBLinExpr objectiveExpr = new GRBLinExpr();

        int numStates = MDP.getNumStates();
        for (int state = 0; state < numStates; state++) {
            int numActions = MDP.getNumChoices(state);
            for (int action = 0; action < numActions; action++) {
                double transReward = rewardProvider.transitionReward(state, action, null);
                double stateReward = rewardProvider.stateReward(state);
                double r = stateReward + transReward;

                if (Double.isNaN(r)) {
                    Logger.getLogger("QP").log(Level.WARNING, "reward is NAN for " + state + " " + action );
                }

                objectiveExpr.addTerm(r, x_a.get(state)[action]);
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

        model.optimize();
        int status = model.get(GRB.IntAttr.Status);
        if (status == GRB.Status.UNBOUNDED) {
            System.out.println("The model cannot be solved "
                    + "because it is unbounded");
            return -2d;
        }
        if (status == GRB.Status.OPTIMAL) {
            double optimalVal = model.get(GRB.DoubleAttr.ObjVal);
            System.out.println("The optimal objective is " +
                    optimalVal);
            return optimalVal;
        }
        if (status != GRB.Status.INF_OR_UNBD &&
                status != GRB.Status.INFEASIBLE    ){
            System.out.println("Optimization was stopped with status " + status);
            return -3d;
        }

        // Compute IIS
        System.out.println("The model is infeasible; computing IIS");
        model.computeIIS();
        System.out.println("\nThe following constraint(s) "
                + "cannot be satisfied:");
        for (GRBConstr c : model.getConstrs()) {
            if (c.get(GRB.IntAttr.IISConstr) == 1) {
                System.out.println(c.get(GRB.StringAttr.ConstrName));
            }
        }

        return -1d;
    }

    private void disposeGurobiModel() {
        model.dispose();
    }

    private void disposeGurobiEnv() throws GRBException {
        env.dispose();
    }


    private void printAllVars() throws GRBException {
        int numStates = MDP.getNumStates();
        for (int state = 0; state < numStates; state++) {
            int numActions = MDP.getNumChoices(state);
            for (int action = 0; action < numActions; action++) {
                printVar(state, action);
            }
        }
    }

    private void printVar(int state, int action) throws GRBException {
        GRBVar x_a_var = x_a.get(state)[action];
        GRBVar y_a_var = y_a.get(state)[action];

        System.out.println("For state " + state + " action " + action);
        System.out.println("x_a_var name is " + x_a_var.get(GRB.StringAttr.VarName) + " " + x_a_var.get(GRB.DoubleAttr.X));
        System.out.println("y_a_var name is " + y_a_var.get(GRB.StringAttr.VarName) + " " + y_a_var.get(GRB.DoubleAttr.X));
        System.out.println("\n");
        System.out.println("\n");
    }

    private GRBQuadExpr getExprForState(Int2ObjectMap<GRBQuadExpr> exprs, int state) {
        GRBQuadExpr exp = exprs.getOrDefault(state, null);
        if (exp == null) {
            exp = new GRBQuadExpr();
            exprs.put(state, exp);
        }
        return exp;
    }
}
