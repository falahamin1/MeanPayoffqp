package de.tum.in.pet.implementation.qp_meanpayoff;

import java.util.Comparator;

public class CompareSuccessorsLower implements Comparator<SuccessorInformation> {
    public int compare(SuccessorInformation p1, SuccessorInformation p2) {

        return Double.compare(p1.lowerBoundValue, p2.lowerBoundValue);
    }
}

