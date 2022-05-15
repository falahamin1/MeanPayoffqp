package de.tum.in.pet.implementation.qp_meanpayoff;

public interface LPRewardProvider {
    double stateReward(int state);
    double transitionReward(int state, int action, Object actionLabel);
}
