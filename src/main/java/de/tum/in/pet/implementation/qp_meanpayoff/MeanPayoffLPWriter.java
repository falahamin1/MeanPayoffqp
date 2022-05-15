package de.tum.in.pet.implementation.qp_meanpayoff;

import de.tum.in.probmodels.graph.Mec;
import de.tum.in.probmodels.model.*;
import gurobi.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.List;

/**
 * The following LP is from the paper arXiv:1502.00611
 *
 */
public class MeanPayoffLPWriter {
    private final MarkovDecisionProcess mdp;
    private final List<Mec> mecs;
    private final LPRewardProvider rewardProvider;

    private GRBEnv env;
    private GRBModel model;

    // For every state action pair we will have y_a variable
    private Int2ObjectMap<GRBVar[]> y_a;

    // For every state we will have an instance of GRBVar
    private GRBVar[] y_s;

    //For every state action pair variable x_a is present
    private Int2ObjectMap<GRBVar[]> x_a;



    public MeanPayoffLPWriter(MarkovDecisionProcess mdp, List<Mec> mecs, LPRewardProvider rewardProvider) {
        this.mdp = mdp;
        this.mecs = mecs;
        this.rewardProvider = rewardProvider;
    }

    public double constructLP() throws GRBException {
        createGurobiEnv();
        createGurobiModel();
        initializeVariables();
        writeLPConstraints();
        setObjectiveFunction();
        double value = optimizeModel();
        disposeGurobiModel();
        disposeGurobiEnv();
        return value;
    }

    private void initializeVariables() throws GRBException {
        initializeYA();
        initializeYS();
        initializeXA();
    }

    private void initializeYA() throws GRBException {
        y_a = new Int2ObjectOpenHashMap<>();

        for (int state = 0; state < mdp.getNumStates(); state++) {
            int numChoices = mdp.getNumChoices(state);

            double[] lb = new double[numChoices];
            double[] ub = new double[numChoices];
            double[] obj = new double[numChoices];
            char[] type = new char[numChoices];
            String[] names = new String[numChoices];

            for (int choice = 0; choice < numChoices; choice++) {
                lb[choice] = 0;
                ub[choice] = GRB.INFINITY;
                obj[choice] = 0;
                type[choice] = GRB.CONTINUOUS;
                names[choice] = "y_a_" + state + "_" + choice;
            }

            GRBVar[] vars = model.addVars(lb, ub, obj, type, names);
            y_a.put(state, vars);
        }
    }

    private void initializeYS() throws GRBException {
        int numStates = mdp.getNumStates();

        double[] lb = new double[numStates];
        double[] ub = new double[numStates];
        double[] obj = new double[numStates];
        char[] type = new char[numStates];
        String[] names = new String[numStates];

        for (int state = 0; state < numStates; state++) {
            lb[state] = 0;
            ub[state] = 1;
            obj[state] = 0;
            type[state] = GRB.CONTINUOUS;
            names[state] = "y_s_" + state;
        }
        y_s = model.addVars(lb, ub, obj, type, names);
    }

    private void initializeXA() throws GRBException {
        x_a = new Int2ObjectOpenHashMap<>();

        for (int state = 0; state < mdp.getNumStates(); state++) {
            int numChoices = mdp.getNumChoices(state);

            double[] lb = new double[numChoices];
            double[] ub = new double[numChoices];
            double[] obj = new double[numChoices];
            char[] type = new char[numChoices];
            String[] names = new String[numChoices];

            for (int choice = 0; choice < numChoices; choice++) {
                lb[choice] = 0;
                ub[choice] = 1;
                obj[choice] = 0;
                type[choice] = GRB.CONTINUOUS;
                names[choice] = "x_a_" + state + "_" + choice;
            }

            GRBVar[] vars = model.addVars(lb, ub, obj, type, names);
            x_a.put(state, vars);
        }
    }

    private void writeLPConstraints() throws GRBException {
        // transient flow
        addConstraint1();

        // almost sure recurring behaviour
        addConstraint2();

        // probability of switching in MEC is equal to frequency of using its actions
        addConstraint3();

        // Recurrent flow
        addConstraint4();
    }

    private void addConstraint1() throws GRBException {
        for (int state = 0; state < mdp.getNumStates(); state++) {
            addConstraint1ForState(state);
        }
    }

    private void addConstraint1ForState(int state) throws GRBException {
        GRBLinExpr LHSExpr = getLHSConstraint1(state);
        GRBLinExpr RHSExpr = getRHSConstraint1(state);
        model.addConstr(LHSExpr, GRB.EQUAL, RHSExpr, "c1_" +state);
    }

    private GRBLinExpr getLHSConstraint1(int state) {
        GRBLinExpr expr = new GRBLinExpr();
        // Add identity function
        if (mdp.getInitialStates().contains(state)) {
            expr.addConstant(1);
        }

        forEachIncomingTransition(state, ((source, probability, target, actionIndex, actionLabel) ->
            expr.addTerm(probability, y_a.get(source)[actionIndex])
        ));

        return expr;
    }

    private GRBLinExpr getRHSConstraint1(int state) {
        GRBLinExpr expr = new GRBLinExpr();
        expr.addTerm(1, y_s[state]);

        for (int action = 0; action < mdp.getActions(state).size(); action++) {
            expr.addTerm(1, y_a.get(state)[action]);
        }

        return expr;
    }

    private void addConstraint2() throws GRBException {
        GRBLinExpr expr = new GRBLinExpr();

        for (Mec mec : mecs) {
            for (Integer mecState : mec.states) {
                expr.addTerm(1, y_s[mecState]);
            }
        }

        model.addConstr(expr, GRB.EQUAL, 1, "c2");
    }

    private void addConstraint3() throws GRBException {
        for (Mec mec : mecs) {
            addConstraint3ForMec(mec);
        }
    }

    private void addConstraint3ForMec(Mec mec) throws GRBException {
        GRBLinExpr LHSExpr = getLHSConstraint3(mec);
        GRBLinExpr RHSExpr = getRHSConstraint3(mec);

        model.addConstr(LHSExpr, GRB.EQUAL, RHSExpr, "c3_" + mecs.indexOf(mec));
    }

    private GRBLinExpr getLHSConstraint3(Mec mec) {
        GRBLinExpr expr = new GRBLinExpr();

        for (Integer mecState : mec.states) {
            expr.addTerm(1, y_s[mecState]);
        }
        return expr;
    }

    private GRBLinExpr getRHSConstraint3(Mec mec) {
        GRBLinExpr expr = new GRBLinExpr();

        for (Integer state : mec.states) {
            for (Integer action : mec.actions.get(state.intValue())) {
                expr.addTerm(1, x_a.get(state.intValue())[action]);
            }
        }

        return expr;
    }

    private void addConstraint4() throws GRBException {
        for (int state = 0; state < mdp.getNumStates(); state++) {
            addConstraint4ForState(state);
        }
    }

    private void addConstraint4ForState(int state) throws GRBException {
        GRBLinExpr LHSExpr = getLHSConstraint4(state);
        GRBLinExpr RHSExpr = getRHSConstraint4(state);

        model.addConstr(LHSExpr, GRB.EQUAL, RHSExpr, "c4_" + state);
    }

    private GRBLinExpr getLHSConstraint4(int state) {
        GRBLinExpr expr = new GRBLinExpr();

        forEachIncomingTransition(state, ((source, probability, target, actionIndex, actionLabel) ->
            expr.addTerm(probability, x_a.get(source)[actionIndex])
        ));

        // It might be the case, there were no incoming transitions for a state. In that case, LHS will be just 0.
        expr.addConstant(0);

        return expr;
    }

    private GRBLinExpr getRHSConstraint4(int state) {
        GRBLinExpr expr = new GRBLinExpr();

        for (int action = 0; action < mdp.getActions(state).size(); action++) {
            expr.addTerm(1, x_a.get(state)[action]);
        }

        return expr;
    }

    private void setObjectiveFunction() throws GRBException {
        GRBLinExpr objectiveExpr = new GRBLinExpr();

        for (int state = 0; state < mdp.getNumStates(); state++) {
            for (int actionIndex = 0; actionIndex < mdp.getActions(state).size(); actionIndex++) {
                double transReward = rewardProvider.transitionReward(state, actionIndex, mdp.getAction(state, actionIndex));
                double stateReward = rewardProvider.stateReward(state);
                double r = stateReward + transReward;

                objectiveExpr.addTerm(r, x_a.get(state)[actionIndex]);
            }
        }

        model.setObjective(objectiveExpr, GRB.MAXIMIZE);
    }

    private double optimizeModel() throws GRBException {
        model.write("gurobimodel.lp");
        model.optimize();

        GRBLinExpr objective = (GRBLinExpr) model.getObjective();
        return objective.getValue();
    }

    private void createGurobiEnv() throws GRBException {
        env = new GRBEnv(true);

        env.set("logFile", "lpLog.log");
        env.start();
    }

    private void createGurobiModel() throws GRBException {
        model = new GRBModel(env);
    }

    private void disposeGurobiModel() {
        model.dispose();
    }

    private void disposeGurobiEnv() throws GRBException {
        env.dispose();
    }

    private void forEachIncomingTransition(int state, IncomingTransitionConsumer consumer) {
        for (int s = 0; s < mdp.getNumStates(); s++) {
            for (int action = 0; action < mdp.getActions(s).size(); action++) {

                int finalAction = action;
                int finalS = s;

                mdp.forEachTransition(s, action, (destination, probability) -> {
                    if (destination == state) {
                        consumer.accept(finalS, probability, destination, finalAction, mdp.getAction(finalS, finalAction));
                    }
                });
            }
        }
    }
}
