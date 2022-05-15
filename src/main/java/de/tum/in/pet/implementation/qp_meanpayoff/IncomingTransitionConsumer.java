package de.tum.in.pet.implementation.qp_meanpayoff;

public interface IncomingTransitionConsumer {
    void accept(int source, double probability, int target, int actionIndex, Object actionLabel);
}
