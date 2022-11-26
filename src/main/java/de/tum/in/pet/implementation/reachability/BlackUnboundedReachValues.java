package de.tum.in.pet.implementation.reachability;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.sampler.SuccessorHeuristic;
import de.tum.in.pet.util.SampleUtil;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.model.Distribution;
import it.unimi.dsi.fastutil.ints.*;
import prism.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.ToDoubleFunction;

import static de.tum.in.probmodels.util.Util.isOne;
import static de.tum.in.probmodels.util.Util.isZero;

public class BlackUnboundedReachValues extends UnboundedReachValues {

  private final UpdateMethod updateMethod;

  // Returns the confidence width for a state x and it's corresponding action index y
  private Int2ObjectFunction<Int2DoubleFunction> confidenceWidthFunction = x -> (y -> (0));

  private Int2ObjectFunction<Int2DoubleFunction> twoSidedConfidenceWidthFunction = x -> (y -> (0));

  private Int2ObjectMap<Bounds> oldBounds;

  public BlackUnboundedReachValues(ValueUpdate update, UpdateMethod updateMethod, IntPredicate target, double precision,
                                   SuccessorHeuristic heuristic) {
    super(update, target, precision, heuristic);
    this.updateMethod = updateMethod;
  }

  /**
   * Setter for confidenceWidthFunction.
   */
  public void setConfidenceWidthFunction(Int2ObjectFunction<Int2DoubleFunction> confidenceWidthFunction) {
    this.confidenceWidthFunction = confidenceWidthFunction;
  }

  /**
   * Setter for  twoSidedConfidenceWidthFunction.
   */
  public void setTwoSidedConfidenceWidthFunction(Int2ObjectFunction<Int2DoubleFunction> twoSidedConfidenceWidthFunction)
  {
    this.twoSidedConfidenceWidthFunction =  twoSidedConfidenceWidthFunction;
  }

  public void resetConfidenceWidthFunction(){
    this.confidenceWidthFunction = x -> (y -> (0));
  }

  /**
   * Creates a copy of the current bounds.
   */
  public void cacheCurrBounds(){
    oldBounds = new Int2ObjectOpenHashMap<>(this.bounds);
  }

  /**
   * Checks whether there have been any significant changes in values compared to the last time the cacheCurrBounds()
   * was called
   * @return true if there are changes, else false
   */
  public boolean checkProgress(){
    for(int state: bounds.keySet()){
      if(!oldBounds.containsKey(state)||!((Math.abs(bounds.get(state).upperBound()-oldBounds.get(state).upperBound())<1e-6)&&
              (Math.abs(bounds.get(state).lowerBound()-oldBounds.get(state).lowerBound())<1e-6))){
        return true;
      }
    }
    return false;
  }

  /**
   * Function to sample the index of the next action from choices from state.
   * @param state: Current state from which the action originates.
   * @param choices: Set of candidate choices.
   * @return Index of the sampled action corresponding to choices.
   */
  @Override
  public int sampleNextAction(int state, List<Distribution> choices){

    ToDoubleFunction<Integer> actionScore = i -> choices.get(i).isEmpty()
            ? 1 : isSmallestFixPoint()
                  ? 1.0d - choices.get(i).sumWeighted(this::lowerBound)
                  : successorBounds(state, choices.get(i), confidenceWidthFunction.apply(state).applyAsDouble(i)).upperBound();

    return SampleUtil.getOptimalChoice(choices, actionScore);
  }

  /**
   * Returns the bounds of an action according to the modified Bellman Equations equations in the CAV'19 paper. They
   * have been slightly modified according to Section 3.6.
   * @param state: originating state. If the distribution is void, the bounds of state are returned.
   * @param distribution: The probability distribution for sampling the next successor for an action.
   * @param confidenceWidth: The confidence width for a state-action pair.
   * @return Bounds of an action from a state with some confidence width.
   */
  private Bounds successorBounds(int state, Distribution distribution, double confidenceWidth) {
    if (distribution.support().size()==0){
      return Bounds.reachUnknown();
    }
    double lower = 0.0d;
    double upper = 0.0d;
    double sum = 0.0d;
    double minLower = 1;
    double maxUpper = 0;
    for (Int2DoubleMap.Entry entry : distribution) {
      int successor = entry.getIntKey();
      Bounds successorBounds = bounds(successor);
      double probability = Math.max(0, entry.getDoubleValue()-confidenceWidth);
      sum += probability;
      lower += successorBounds.lowerBound() * probability;
      upper += successorBounds.upperBound() * probability;
      minLower = Math.min(minLower, successorBounds.lowerBound());
      maxUpper = Math.max(maxUpper, successorBounds.upperBound());
    }

//  If the confidence width is very high, then all the successor probabilities (T_HAT) of state, Distribution will be 0.
//  Hence, sum will be 0. In that case, we don't return the successor bounds. We just return the bounds of the state
//  itself. This is because, bounds of the incoming state will anyways be larger than the successorBounds, since it is
//  a predecessor.
    if (sum == 0.0d) {
      // If there is no return statement here, and the sum is 0, then minLower, maxUpper will be returned.
      // Sum is 0, because we have visited this transition very few times. So returning the minLower, maxUpper
      // of successor might be bad, since it may be wrong. Some actions of successor, might not even be explored.
      return bounds(state);
    }
    double remProb = 1-sum;
    if(doMostConservativeGuess(state, distribution)) {
      minLower = 0;
      maxUpper = 1;
    }
    return Bounds.reach(lower+remProb*minLower, upper+remProb*maxUpper);
  }
  /**
   * Returns the bounds of an action according to the new Bellman equations in the CAV'23 paper.
   * @param state: originating state. If the distribution is void, the bounds of state are returned.
   * @param distribution: The probability distribution for sampling the next successor for an action.
   * @param confidenceWidth: The 2-sided confidence width for a state-action pair.
   * @return Bounds of an action from a state with some confidence width.
   */
  private Bounds successorBoundsNew(int state, Distribution distribution, double confidenceWidth) {
    if (distribution.support().size()==0){
      return Bounds.reachUnknown();
    }
    double lower = 0.0d;
    double upper = 0.0d;
    double sum = 0.0d;
    double minLower = 1;
    double maxUpper = 0;
    Int2DoubleMap lowerBoundMapping = new Int2DoubleOpenHashMap();
    Int2DoubleMap upperBoundMapping = new Int2DoubleOpenHashMap();
    Int2DoubleMap probabilityMapping = new Int2DoubleOpenHashMap();
    NatBitSet successors = NatBitSets.ensureModifiable(NatBitSets.simpleSet());
    for (Int2DoubleMap.Entry entry : distribution) {
      int successor = entry.getIntKey();
      successors.add(successor);
      Bounds successorBounds = bounds(successor);
      double probability = Math.max(0, entry.getDoubleValue()-confidenceWidth);
      double uprobability = Math.min(1, entry.getDoubleValue() + confidenceWidth);
      sum += probability;
      lowerBoundMapping.put(successor, successorBounds.lowerBound());
      upperBoundMapping.put(successor, successorBounds.upperBound());
      probabilityMapping.put(successor, uprobability - probability);
      lower += successorBounds.lowerBound() * probability;
      upper += successorBounds.upperBound() * probability;
      minLower = Math.min(minLower, successorBounds.lowerBound());
      maxUpper = Math.max(maxUpper, successorBounds.upperBound());
    }
    System.out.println("Out of loop");

//  If the confidence width is very high, then all the successor probabilities (T_HAT) of state, Distribution will be 0.
//  Hence, sum will be 0. In that case, we don't return the successor bounds. We just return the bounds of the state
//  itself. This is because, bounds of the incoming state will anyways be larger than the successorBounds, since it is
//  a predecessor.
    if (sum == 0.0d) {
      // If there is no return statement here, and the sum is 0, then minLower, maxUpper will be returned.
      // Sum is 0, because we have visited this transition very few times. So returning the minLower, maxUpper
      // of successor might be bad, since it may be wrong. Some actions of successor, might not even be explored.
      return bounds(state);
    }
    double remProb = 1-sum;
    if(doMostConservativeGuess(state, distribution)) {
      minLower = 0;
      maxUpper = 1;
    }
    lower += addRemainingProbabilities(lowerBoundMapping, probabilityMapping, remProb, successors, true );
    upper += addRemainingProbabilities(upperBoundMapping, probabilityMapping, remProb, successors, false );
    System.out.println("Finished calling remaining probabilities");
    return Bounds.reach(lower, upper);
  }

  /** Adds the remaining probabilities according to the new Bellman equations introduced **/
  private  double addRemainingProbabilities(Int2DoubleMap values, Int2DoubleMap remainingProbabilites, double remprob, NatBitSet successors, boolean isLower)
  {
    double sum = 0;
    NatBitSet temp = NatBitSets.ensureModifiable(NatBitSets.simpleSet());
    temp = successors.clone();
    while (remprob > 0)
    {
      if (isLower)
      {
        double min = Double.MAX_VALUE;
        int minstate = 0;
        for(int i : temp)
        {
          if(min >= values.get(i))
          {
            minstate = i;
            min = values.get(i);
          }
        }
        temp.remove(minstate);
        double probound = remainingProbabilites.get(minstate);
        if(remprob<= probound)
        {
          sum += remprob * min;
          remprob = 0;
        }
        else
        {
          remprob  -= probound;
          sum += probound * min;
        }
      }
      else
      {
        double max = 0.0;
        int maxstate = 0;
        for(int i : temp)
        {
          if(max <= values.get(i))
          {
            maxstate = i;
            max = values.get(i);
          }
        }
        temp.remove(maxstate);
        double probound = remainingProbabilites.get(maxstate);

        if(remprob<= probound)
        {
          sum += remprob* max;
          remprob = 0;
        }
        else
        {
          remprob  -= probound;
          sum += probound* max;
        }
      }
    }
    return sum;

  }

  /**
   * @return true, if we have to update using most conservative bounds. i.e minLower to be zero and maxUpper to be 1
   */
  protected boolean doMostConservativeGuess(int state, Distribution distribution) {
    return updateMethod == UpdateMethod.BLACKBOX;
  }

  public List<Pair<Integer, Integer>> getBestLeavingAction(IntSet states, Int2ObjectFunction<List<Distribution>> choiceFunction) {
    double bestUpperBound;
    List<Pair<Integer, Integer>> bestActionStatePairs = new ArrayList<>();

    if (update == ValueUpdate.MAX_VALUE) {
      bestUpperBound = 0.0d;
      for (int state: states){
        List<Distribution> choices = choiceFunction.get(state);
        for(int i=0; i<choices.size(); i++){
          Distribution distribution = choices.get(i);
          // checks if the action's support is contained within the set of states. This means this action doesn't leave
          // the set of states.
          if(states.containsAll(distribution.support()) && !distribution.isEmpty()){
            continue;
          }
          double newUpperBound = successorBounds(state, distribution, confidenceWidthFunction.get(state).get(i)).upperBound();
          if (newUpperBound > bestUpperBound) {
            bestUpperBound = newUpperBound;
            bestActionStatePairs = new ArrayList<>();
            bestActionStatePairs.add(new Pair<>(state, i));
          }
          else if (newUpperBound == bestUpperBound) {
            bestActionStatePairs.add(new Pair<>(state, i));
          }
        }
      }
    } else {
      assert update == ValueUpdate.MIN_VALUE;

      bestUpperBound = 1.0d;
      for (int state: states){
        List<Distribution> choices = choiceFunction.get(state);
        for(int i=0; i<choices.size(); i++){
          Distribution distribution = choices.get(i);
          if(distribution.support().containsAll(states)){
            continue;
          }
          double newUpperBound = successorBounds(state, distribution, confidenceWidthFunction.get(state).get(i)).upperBound();
          if (newUpperBound < bestUpperBound){
            bestUpperBound = newUpperBound;
            bestActionStatePairs = new ArrayList<>();
            bestActionStatePairs.add(new Pair<>(state, i));
          }
          else if (newUpperBound == bestUpperBound) {
            bestActionStatePairs.add(new Pair<>(state, i));
          }
        }
      }
    }

    return bestActionStatePairs;
  }

  /**
   * The deflate algorithm as mentioned in Algorithm 6 of the CAV'19 paper. For a set states (probably in an MEC), this
   * sets the upper bound of all states to the upper bound of the best action leaving the set of states.
   * @param states: set of states, whose values are to be deflated.
   * @param choiceFunction: function that returns the choices for a given state.
   */
  public void deflate(IntSet states, Int2ObjectFunction<List<Distribution>> choiceFunction){

    List<Pair<Integer, Integer>> bestLeavingActionStatePair = this.getBestLeavingAction(states, choiceFunction);

    int actionState = bestLeavingActionStatePair.get(0).first;
    int actionIndex = bestLeavingActionStatePair.get(0).second;
    Distribution distribution = choiceFunction.get(actionState).get(actionIndex);
    double newUpperBound = successorBounds(actionState, distribution,
            confidenceWidthFunction.get(actionState).get(actionIndex)).upperBound();

    for (int state: states){
      if (upperBound(state)>newUpperBound) {
        bounds.put(state, Bounds.of(lowerBound(state), newUpperBound));
      }
    }
  }

  // updates bound of a state according to the given list of distributions
  public void update(int state, List<Distribution> choices) {
    assert update != ValueUpdate.UNIQUE_VALUE || choices.size() <= 1;

    Bounds stateBounds = bounds(state);
    if (isOne(stateBounds.lowerBound()) || isZero(stateBounds.upperBound())) {
      return;
    }
    assert !target.test(state);

    Bounds newBounds;
    // If there are no choices from the state, it must have a zero value (u=0, l=0)
    if (choices.isEmpty()) {
      newBounds = Bounds.reachZero();
      bounds.put(state, newBounds);
    }
    else if (choices.size() == 1) {
      newBounds = successorBounds(state, choices.get(0), confidenceWidthFunction.get(state).get(0));
      bounds.put(state, newBounds);
    }
    else {
      double newLowerBound;
      double newUpperBound;

      // finds bounds for each distribution using successorBounds. The new upper and lower bound are either the maximum or minimum of all computed respective bounds

      if (update == ValueUpdate.MAX_VALUE) {
        newLowerBound = 0.0d;
        newUpperBound = 0.0d;
        for (int distributionIndex=0; distributionIndex<choices.size(); distributionIndex++) {
          Bounds bounds = successorBounds(state, choices.get(distributionIndex),
                  confidenceWidthFunction.get(state).get(distributionIndex));
          double upperBound = bounds.upperBound();
          if (upperBound > newUpperBound) {
            newUpperBound = upperBound;
          }
          double lowerBound = bounds.lowerBound();
          if (lowerBound > newLowerBound) {
            newLowerBound = lowerBound;
          }
        }
      } else {
        assert update == ValueUpdate.MIN_VALUE;

        newUpperBound = 1.0d;
        newLowerBound = 1.0d;
        for (int distributionIndex=0; distributionIndex<choices.size(); distributionIndex++) {
          Bounds bounds = successorBounds(state, choices.get(distributionIndex),
                  confidenceWidthFunction.get(state).get(distributionIndex));
          double upperBound = bounds.upperBound();
          if (upperBound < newUpperBound) {
            newUpperBound = upperBound;
          }
          double lowerBound = bounds.lowerBound();
          if (lowerBound < newLowerBound) {
            newLowerBound = lowerBound;
          }
        }
      }

      assert newLowerBound <= newUpperBound;
      newBounds = Bounds.of(newLowerBound, newUpperBound);
      bounds.put(state, newBounds);
    }
  }

  public void update(int state, List<Distribution> choices, boolean ifByNewVI) {
    assert update != ValueUpdate.UNIQUE_VALUE || choices.size() <= 1;

    Bounds stateBounds = bounds(state);
    if (isOne(stateBounds.lowerBound()) || isZero(stateBounds.upperBound())) {
      return;
    }
    assert !target.test(state);

    Bounds newBounds;
    // If there are no choices from the state, it must have a zero value (u=0, l=0)
    if (choices.isEmpty()) {
      newBounds = Bounds.reachZero();
      bounds.put(state, newBounds);
    }
    else if (choices.size() == 1) {
      newBounds = successorBounds(state, choices.get(0), confidenceWidthFunction.get(state).get(0));
      bounds.put(state, newBounds);
    }
    else {
      double newLowerBound;
      double newUpperBound;

      // finds bounds for each distribution using successorBounds. The new upper and lower bound are either the maximum or minimum of all computed respective bounds

      if (update == ValueUpdate.MAX_VALUE) {
        newLowerBound = 0.0d;
        newUpperBound = 0.0d;
        for (int distributionIndex=0; distributionIndex<choices.size(); distributionIndex++) {
          Bounds bounds;
          if (ifByNewVI)
            bounds = successorBoundsNew(state, choices.get(distributionIndex),
                  twoSidedConfidenceWidthFunction.get(state).get(distributionIndex));
          else
            bounds = successorBounds(state, choices.get(distributionIndex),
                    confidenceWidthFunction.get(state).get(distributionIndex));

          double upperBound = bounds.upperBound();
          if (upperBound > newUpperBound) {
            newUpperBound = upperBound;
          }
          double lowerBound = bounds.lowerBound();
          if (lowerBound > newLowerBound) {
            newLowerBound = lowerBound;
          }
        }
      } else {
        assert update == ValueUpdate.MIN_VALUE;

        newUpperBound = 1.0d;
        newLowerBound = 1.0d;
        for (int distributionIndex=0; distributionIndex<choices.size(); distributionIndex++) {
          Bounds bounds;
          if (ifByNewVI)
            bounds = successorBoundsNew(state, choices.get(distributionIndex),
                    twoSidedConfidenceWidthFunction.get(state).get(distributionIndex));
          else
            bounds = successorBounds(state, choices.get(distributionIndex),
                    confidenceWidthFunction.get(state).get(distributionIndex));
          double upperBound = bounds.upperBound();
          if (upperBound < newUpperBound) {
            newUpperBound = upperBound;
          }
          double lowerBound = bounds.lowerBound();
          if (lowerBound < newLowerBound) {
            newLowerBound = lowerBound;
          }
        }
      }

      assert newLowerBound <= newUpperBound;
      newBounds = Bounds.of(newLowerBound, newUpperBound);
      bounds.put(state, newBounds);
    }
  }

}
