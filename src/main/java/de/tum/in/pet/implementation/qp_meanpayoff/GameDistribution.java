package de.tum.in.pet.implementation.qp_meanpayoff;
// Keeps track of the successors and probabilities to them
public class GameDistribution {
    public final double [] probabilities;
    public final int [] successors;
    public final int action;

    public GameDistribution (double[] probabilities, int [] successors, int action )
    {
        this.action = action;
        this.probabilities = probabilities;
        this.successors = successors;
    }
    public double [] getProbabilities()
    {
        return probabilities;
    }
    public int [] getSuccessors()
    {
        return successors;
    }
    public void printDistribution () {
        for (int i = 0; i < successors.length ; i++)
        {
            System.out.println("state "+ successors[i] + " with probability "+ probabilities[i]);
        }

    }

}
