package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.graph.Mec;
import de.tum.in.probmodels.model.Distribution;
import it.unimi.dsi.fastutil.ints.*;
import prism.Pair;

import java.util.ArrayList;
import java.util.List;

public class RestrictedMecBoundedValueIterator<S> {

//  public final M model; // Original Model
  public final Mec mec; // Mec with respect to original model
  public final double targetPrecision;
  public final Int2ObjectMap<Bounds> values;  // map of states and total values; the average value can be obtained by dividing by iterCount
  public final RewardGenerator<S> rewardGenerator;
  private int iterCount;  // number of iterations in value iteration
  private final Int2ObjectFunction<S> stateIndexMap; // map from original model state number to corresponding state object
  private final double rMax;

  private Bounds bounds;

  // Returns the confidence width for a state x and it's corresponding action index y
  private Int2ObjectFunction<Int2DoubleFunction> confidenceWidthFunction = x -> (y -> (0));

  // Returns the distribution for a state x and it's corresponding action index y
  private Int2ObjectFunction<Int2ObjectFunction<Distribution>> distributionFunction = x -> (y -> null);

  // Returns the label of the action y
  private Int2ObjectFunction<Int2ObjectFunction<Object>> labelFunction = x -> (y -> null);

  private final double aperidocityConstant;

  private final long timeout;

  public RestrictedMecBoundedValueIterator(Mec mec, double targetPrecision, RewardGenerator<S> rewardGenerator,
                                    Int2ObjectFunction<S> stateIndexMap, double rMax, long timeout){
    this.mec = mec;
    this.targetPrecision = targetPrecision;
    this.values = new Int2ObjectOpenHashMap<>();
    this.rewardGenerator = rewardGenerator;
    this.stateIndexMap = stateIndexMap;
    this.iterCount = 0;
    this.aperidocityConstant = 0.8;
    this.rMax = rMax;
    this.timeout = timeout;
  }

  public RestrictedMecBoundedValueIterator(Mec mec, double targetPrecision, RewardGenerator<S> rewardGenerator,
                                    Int2ObjectFunction<S> stateIndexMap, Int2ObjectMap<Bounds> values, double rMax,
                                           long timeout){
    this.mec = mec;
    this.targetPrecision = targetPrecision;
    this.values = values;
    this.rewardGenerator = rewardGenerator;
    this.stateIndexMap = stateIndexMap;
    this.iterCount = 0;
    this.aperidocityConstant = 0.8;
    this.rMax = rMax;
    this.timeout = timeout;
  }

  /**
   * Setter for confidenceWidthFunction.
   */
  public void setConfidenceWidthFunction(Int2ObjectFunction<Int2DoubleFunction> confidenceWidthFunction){
    this.confidenceWidthFunction = confidenceWidthFunction;
  }

  public void resetConfidenceWidthFunction(){
    this.confidenceWidthFunction = x -> (y -> (0));
  }

  public void setDistributionFunction(Int2ObjectFunction<Int2ObjectFunction<Distribution>> distributionFunction) {
    this.distributionFunction = distributionFunction;
  }

  public void setLabelFunction(Int2ObjectFunction<Int2ObjectFunction<Object>> labelFunction) {
    this.labelFunction = labelFunction;
  }

  public void run(){
    int numStates = mec.size();
    NatBitSet states = mec.states;

    // This array stores the difference of values for each state between two successive iterations. This is Delta_n in CAV'17.
    List<Pair<Double, Double>> diff = new ArrayList<>();

    // This is to make the size of diff to be num states.
    // During each iteration we use set method to change the values of diff. It will throw error if index is greater than size.
    for (int i = 0; i < numStates; i++) {
      diff.add(new Pair<>(0d, 0d));
    }

    if(values.size()==0) { // no pre-computed values sent
      IntIterator stateIterator = states.iterator();
      while (stateIterator.hasNext()) {
        int state = stateIterator.nextInt();
        values.put(state, Bounds.of(0, 0));
      }
    }

    double maxUpper, minUpper, maxLower, minLower;
    do {
      Int2ObjectMap<Bounds> oldValues = new Int2ObjectOpenHashMap<>(values);
      Int2ObjectMap<Bounds> tempValues = new Int2ObjectOpenHashMap<>();
      IntIterator stateIterator = states.iterator();
      int count = 0;
      // A single iteration of VI
      while (stateIterator.hasNext()) {
        int state = stateIterator.nextInt();
        double maxUpperBound = 0.0;
        double maxLowerBound = 0.0;
        IntSet allowedActions = mec.actions.get(state);  // allowedActions numbered as in original model
        assert allowedActions != null;
//        List<Action> choices = model.getActions(state);  // get all actions (not distributions)
        // Get Actions from original model, filter according to mec actions
        for (int action : allowedActions) {  // find the value of the state over all actions
          // Send action label instead of action object. State object needs to be fetched from stateIndexMap.
          // val_transformed = const*rewards + actionVal. Instead, we have found val = rewards + actionVal/const (This division is done by actionVal itself). We do this to store the original value.
          double val = rewardGenerator.transitionReward(stateIndexMap.get(state), labelFunction.apply(state).apply(action)) // Action.label() returns label
                  + rewardGenerator.stateReward(stateIndexMap.get(state));

          Bounds actionBounds = getActionBounds(state, distributionFunction.apply(state).apply(action), // Action.distribution returns distribution
                  confidenceWidthFunction.get(state).get(action));
          maxLowerBound = Math.max(actionBounds.lowerBound()+val, maxLowerBound);
          maxUpperBound = Math.max(actionBounds.upperBound()+val, maxUpperBound);
        }
        tempValues.put(state, Bounds.of(maxLowerBound, maxUpperBound));
        diff.set(count++, new Pair<>(maxLowerBound-oldValues.get(state).lowerBound(),
                maxUpperBound-oldValues.get(state).upperBound()));
      }
      for(int state: values.keySet()) {
        values.put(state, tempValues.get(state));
      }
      iterCount++;

      maxLower=0.0; // max of diff (max of Delta_n)
      minLower=Double.MAX_VALUE; // min of diff (min of Delta_n)
      maxUpper=0.0; // max of diff (max of Delta_n)
      minUpper=Double.MAX_VALUE; // min of diff (min of Delta_n)
      for (Pair<Double, Double> b : diff) {
        maxLower = Math.max(maxLower, b.first);
        minLower = Math.min(minLower, b.first);
        maxUpper = Math.max(maxUpper, b.second);
        minUpper = Math.min(minUpper, b.second);
      }
      int a = 0;
    } while ((maxLower-minLower) >= targetPrecision && (maxUpper-minUpper) >= targetPrecision && !isTimeout());  // stopping criterion of value iteration

    // Sometimes the upper bound is slightly greater than rMax, because of floating point error.
    // This was observed when running the pnueli-zuck3 model.
    // We change the upper bound to be rMax itself, when it goes beyond rMax.
    if (maxUpper >= rMax) {
      maxUpper = rMax;
    }
    bounds = Bounds.of(minLower, maxUpper);
  }

  public void runNewVI()
  {
    int numStates = mec.size();
    NatBitSet states = mec.states;

    // This array stores the difference of values for each state between two successive iterations. This is Delta_n in CAV'17.
    List<Pair<Double, Double>> diff = new ArrayList<>();

    // This is to make the size of diff to be num states.
    // During each iteration we use set method to change the values of diff. It will throw error if index is greater than size.
    for (int i = 0; i < numStates; i++) {
      diff.add(new Pair<>(0d, 0d));
    }

    if(values.size()==0) { // no pre-computed values sent
      IntIterator stateIterator = states.iterator();
      while (stateIterator.hasNext()) {
        int state = stateIterator.nextInt();
        values.put(state, Bounds.of(0, 0));
      }
    }

    double maxUpper, minUpper, maxLower, minLower;
    do {
      Int2ObjectMap<Bounds> oldValues = new Int2ObjectOpenHashMap<>(values);
      Int2ObjectMap<Bounds> tempValues = new Int2ObjectOpenHashMap<>();
      IntIterator stateIterator = states.iterator();
      int count = 0;
      // A single iteration of VI
      while (stateIterator.hasNext()) {
        int state = stateIterator.nextInt();
        double maxUpperBound = 0.0;
        double maxLowerBound = 0.0;
        IntSet allowedActions = mec.actions.get(state);  // allowedActions numbered as in original model
        assert allowedActions != null;
//        List<Action> choices = model.getActions(state);  // get all actions (not distributions)
        // Get Actions from original model, filter according to mec actions
        for (int action : allowedActions) {  // find the value of the state over all actions
          // Send action label instead of action object. State object needs to be fetched from stateIndexMap.
          // val_transformed = const*rewards + actionVal. Instead, we have found val = rewards + actionVal/const (This division is done by actionVal itself). We do this to store the original value.
          double val = rewardGenerator.transitionReward(stateIndexMap.get(state), labelFunction.apply(state).apply(action)) // Action.label() returns label
                  + rewardGenerator.stateReward(stateIndexMap.get(state));

          Bounds actionBounds = getActionBoundsNew(state, distributionFunction.apply(state).apply(action), // Action.distribution returns distribution
                  confidenceWidthFunction.get(state).get(action));
          maxLowerBound = Math.max(actionBounds.lowerBound()+val, maxLowerBound);
          maxUpperBound = Math.max(actionBounds.upperBound()+val, maxUpperBound);
        }
        tempValues.put(state, Bounds.of(maxLowerBound, maxUpperBound));
        diff.set(count++, new Pair<>(maxLowerBound-oldValues.get(state).lowerBound(),
                maxUpperBound-oldValues.get(state).upperBound()));
      }
      for(int state: values.keySet()) {
        values.put(state, tempValues.get(state));
      }
      iterCount++;

      maxLower=0.0; // max of diff (max of Delta_n)
      minLower=Double.MAX_VALUE; // min of diff (min of Delta_n)
      maxUpper=0.0; // max of diff (max of Delta_n)
      minUpper=Double.MAX_VALUE; // min of diff (min of Delta_n)
      for (Pair<Double, Double> b : diff) {
        maxLower = Math.max(maxLower, b.first);
        minLower = Math.min(minLower, b.first);
        maxUpper = Math.max(maxUpper, b.second);
        minUpper = Math.min(minUpper, b.second);
      }
    } while ((maxLower-minLower) >= targetPrecision && (maxUpper-minUpper) >= targetPrecision && !isTimeout());  // stopping criterion of value iteration

    // Sometimes the upper bound is slightly greater than rMax, because of floating point error.
    // This was observed when running the pnueli-zuck3 model.
    // We change the upper bound to be rMax itself, when it goes beyond rMax.
    if (maxUpper >= rMax) {
      maxUpper = rMax;
    }
    bounds = Bounds.of(minLower, maxUpper);
  }

  private Bounds getActionBoundsNew(int state, Distribution distribution, double confidenceWidth) {
    double lower = 0.0d;
    double upper = 0.0d;
    double probSum = 0.0d;
    double minLower = Integer.MAX_VALUE;
    double maxUpper = 0;
    Int2DoubleMap remainingProbabilities = new Int2DoubleOpenHashMap();
    Int2DoubleMap lowerValues = new Int2DoubleOpenHashMap();
    Int2DoubleMap upperValues = new Int2DoubleOpenHashMap();
    NatBitSet successors = NatBitSets.ensureModifiable(NatBitSets.simpleSet());

    for (Int2DoubleMap.Entry entry : distribution) {
      int successor = entry.getIntKey();
      successors.add(successor);
      double probability = Math.max(0, entry.getDoubleValue()-confidenceWidth);
      double uprobability = Math.min(1, entry.getDoubleValue() + confidenceWidth);
      remainingProbabilities.put(successor, uprobability - probability); // the remaining probability is the difference between upper and lower bound
      Bounds succValues = values.get(successor);
      probSum += probability;
      double succLower = this.aperidocityConstant*succValues.lowerBound();
      double succUpper = this.aperidocityConstant*succValues.upperBound();
      lowerValues.put(successor, succLower);
      upperValues.put(successor, succUpper);
      lower += succLower*probability*this.aperidocityConstant;
      upper += succUpper*probability*this.aperidocityConstant;
      minLower = Math.min(minLower, succLower);
      maxUpper = Math.max(maxUpper, succUpper);
    }
    double remProb = 1-probSum;
    lower += (1-this.aperidocityConstant)*this.aperidocityConstant*values.get(state).lowerBound();
    upper += (1-this.aperidocityConstant)*this.aperidocityConstant*values.get(state).upperBound();
    if(successors.size()==1)
    {
      lower += remProb*minLower*this.aperidocityConstant;
      upper += remProb*maxUpper*this.aperidocityConstant;
    }
    else
    {
      lower += addRemainingProbabilities(lowerValues, remainingProbabilities, remProb, successors, true);
      upper += addRemainingProbabilities(upperValues, remainingProbabilities, remProb, successors, false);
    }

    return Bounds.of(lower/this.aperidocityConstant, upper/this.aperidocityConstant);
  }

  private  double addRemainingProbabilities(Int2DoubleMap values, Int2DoubleMap remainingProbabilites, double remprob, NatBitSet successors, boolean isLower)
  {
    double sum = 0;
    NatBitSet temp = successors.clone();
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
          sum += remprob*this.aperidocityConstant*min;
          remprob = 0;
        }
        else
        {
          remprob  -= probound;
          sum += probound * this.aperidocityConstant * min;
        }
      }
      else
      {
//      System.out.println("In upper");
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
//      System.out.println("Max state is : "+ maxstate);
        temp.remove(maxstate);
        double probound = remainingProbabilites.get(maxstate);
//      System.out.println("Remaining probability: " + remprob + " Space present: "+ probound );
        if(remprob<= probound)
        {
          sum += remprob*this.aperidocityConstant* max;
          remprob = 0;
        }
        else
        {
          remprob  -= probound;
          sum += probound * this.aperidocityConstant * max;
        }
      }
    }
    return sum;

  }

  // todo: vectorize operation
  /**
   * Suppose for a state s, Distribution d, there is one successor s'. Here we introduce self loop to state s, by taking
   * action d, with probability (1 - aperiodicity), and then calculate the bounds of this state. This is done to avoid
   * periodic MDP's which may not converge at all. Refer CAV'17.
   *
   * So in this case,
   * value(s) = ((1 - aperiodicity) * value(s')) + (aperiodicity * value(s'))
   *
   * But here we do the same in a slightly different manner.
   * value(s) = (((1 - aperiodicity) * aperiodicity * value(s')) + (aperiodicity * aperiodicity * value(s'))) / aperiodicity
   *
   * Both the value functions will return the same value. We use the second one.
   * If there are multiple successors, we multiply each of their value with (aperiodicity * aperiodicity).
   * We also use greybox equations to update our bounds, which has high confidence width.
   */
  private Bounds getActionBounds(int state, Distribution distribution, double confidenceWidth) {
    double lower = 0.0d;
    double upper = 0.0d;
    double probSum = 0.0d;
    double minLower = Integer.MAX_VALUE;
    double maxUpper = 0;
    for (Int2DoubleMap.Entry entry : distribution) {
      int successor = entry.getIntKey();
      double probability = Math.max(0, entry.getDoubleValue()-confidenceWidth);
      Bounds succValues = values.get(successor);
      probSum += probability;
      double succLower = this.aperidocityConstant*succValues.lowerBound();
      double succUpper = this.aperidocityConstant*succValues.upperBound();
      lower += succLower*probability*this.aperidocityConstant;
      upper += succUpper*probability*this.aperidocityConstant;
      minLower = Math.min(minLower, succLower);
      maxUpper = Math.max(maxUpper, succUpper);
    }
    
    double remProb = 1-probSum;
    lower += remProb*minLower*this.aperidocityConstant;
    upper += remProb*maxUpper*this.aperidocityConstant;
    lower += (1-this.aperidocityConstant)*this.aperidocityConstant*values.get(state).lowerBound();
    upper += (1-this.aperidocityConstant)*this.aperidocityConstant*values.get(state).upperBound();
    return Bounds.of(lower/this.aperidocityConstant, upper/this.aperidocityConstant);
  }


  /**
   * @return Returns the calculated reward upper and lower bounds.
   */
  public Bounds getBounds(){
    return bounds;
  }

  /**
   * @return Return values such that in future, value iteration can be continued from current values.
   */
  public Int2ObjectMap<Bounds> getValues(){
    return this.values;
  }

  private boolean isTimeout() {
    return System.currentTimeMillis() >= timeout;
  }

}
