package de.tum.in.pet.implementation.qp_meanpayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.probmodels.model.Distribution;
import it.unimi.dsi.fastutil.ints.IntSet;

public interface MecInformationProvider {
    NatBitSet provideStates();
    IntSet provideActions(int state);
    Distribution provideDistribution(int state, int action);
    double provideConfidenceWidth(int state, int action);
}
