package de.tum.in.pet.implementation.qp_meanpayoff;

import java.util.Comparator;

public class CompareSuccessorsUpper implements Comparator<SuccessorInformation> {
    public int compare(SuccessorInformation p1, SuccessorInformation p2) {

        return -1 * Double.compare(p1.lowerBoundValue, p2.lowerBoundValue);
    }
}
