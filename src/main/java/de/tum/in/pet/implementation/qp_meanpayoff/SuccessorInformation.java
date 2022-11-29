package de.tum.in.pet.implementation.qp_meanpayoff;

public class SuccessorInformation {
    public int state;
    public double lowerBoundValue;
    public double upperBoundValue;

    public double remainingProbability;
    public SuccessorInformation(int state, double lowerBoundValue, double upperBoundValue, double remainingProbability)
    {
        this.state = state;
        this.lowerBoundValue = lowerBoundValue;
        this.upperBoundValue = upperBoundValue;
        this.remainingProbability = remainingProbability;
    }

}
