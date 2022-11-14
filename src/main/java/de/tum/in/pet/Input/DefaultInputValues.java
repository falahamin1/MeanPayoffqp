package de.tum.in.pet.Input;

import de.tum.in.pet.implementation.meanPayoff.DeltaTCalculationMethod;
import de.tum.in.pet.implementation.meanPayoff.SimulateMec;
import de.tum.in.pet.implementation.qp_meanpayoff.LowerBound;
import de.tum.in.pet.implementation.qp_meanpayoff.UpperBound;
import de.tum.in.pet.implementation.reachability.UpdateMethod;
import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.probmodels.explorer.InformationLevel;

public class DefaultInputValues {
    public static final double PRECISION = 1.0e-6;
    public static final int THRESHOLD = 5;
    public static final double REWARD_UPPERBOUND = 10;
    public static final double P_MIN_LOWERBOUND = 1.0e-6;
    public static final double ERROR_TOLERANCE = 0.1;
    public static final int ITERATION_SAMPLE = 10000;
    public static final long TIMEOUT = 1800000;
    public static final SuccessorHeuristic HEURISTIC = SuccessorHeuristic.PROB;
    public static final InformationLevel INFORMATION_LEVEL = InformationLevel.WHITEBOX;
    public static final UpdateMethod UPDATE_METHOD = UpdateMethod.GREYBOX;
    public static final SimulateMec SIMULATE_MEC = SimulateMec.HEURISTIC;
    public static final String OUTPUT_PATH = "temp.txt";
    public static final DeltaTCalculationMethod DELTA_T_CALCULATION_METHOD = DeltaTCalculationMethod.P_MIN;

    public static final LowerBound LOWER_BOUND = LowerBound.VI;

    public static final UpperBound UPPER_BOUND = UpperBound.VI;
}
