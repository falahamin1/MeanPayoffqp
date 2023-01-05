package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.model.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

// Class to represent the Bounded MEC Quotient of a partially explored model. Please make sure that the number of states
// is less than or equal to Integer.MAX_VALUE-3.
public class BoundedMecQuotient<M extends Model> extends CollapseView<M> {

  private static final int plusState = Integer.MAX_VALUE;
  private static final int minusState = Integer.MAX_VALUE-1;
  private static final int uncertainState = Integer.MAX_VALUE-2;

  private final Int2ObjectOpenHashMap<Distribution> stayActionMap = new Int2ObjectOpenHashMap<>();

  public BoundedMecQuotient(M model) {
    super(model);
  }

  /**
   * @return Returns the integer value of the plusState of Bounded MEC Quotient.
   */
  public static int getPlusState() {
    return plusState;
  }

  /**
   * @return Returns the integer value of the minusState of Bounded MEC Quotient.
   */
  public static int getMinusState() {
    return minusState;
  }

  /**
   * @return Returns the integer value of the uncertainState of Bounded MEC Quotient.
   */
  public static int getUncertainState() {
    return uncertainState;
  }

  /**
   * @param state Integer value of state to be checked.
   * @return Returns whether the given state is a sink state in the Bounded MEC Quotient.
   */
  public static boolean isSinkState(int state){
    return state==plusState||state==minusState
            ||state==uncertainState;
  }

  /**
   * @param state Integer value of state to be checked
   * @return Returns whether the given state is the uncertain state in the Bounded MEC Quotient.
   */
  public static boolean isUncertainState(int state){
    return state==uncertainState;
  }

  /**
   * @param state Integer value of state to be checked
   * @return Returns whether the given state is the plus state in the Bounded MEC Quotient.
   */
  public static boolean isPlusState(int state) {
    return state==plusState;
  }

  public static boolean isStayAction(Distribution distribution) {
    return distribution.support().stream().anyMatch(BoundedMecQuotient::isSinkState);
  }

  /**
   * Updates the stay action originating from the representative state of an MEC.
   * @param representative Integer value of representative state of an MEC.
   * @param bounds Reward upper bounds using which the distribution of the stay action is to be calculated.
   */
  public void updateStayAction(int representative, Bounds bounds) {

    Distribution distribution = getStayDistribution(bounds);
    stayActionMap.put(representative, distribution);

  }

  /**
   * @param bounds Bounds from which the stay distribution is to be calculated.
   * @return Returns the distribution of the stay action.
   */
  public static Distribution getStayDistribution(Bounds bounds){
//    System.out.println(" In get Stay distribution");

    assert bounds.lowerBound()<=1 && bounds.upperBound()<=1;
    assert bounds.lowerBound()>=0 && bounds.upperBound()>=0;

    DistributionBuilder builder = Distributions.defaultBuilder();
    if(bounds.lowerBound()>0) {
//      System.out.println("lower bound > 0");
      builder.add(plusState, bounds.lowerBound());
    }
    if(bounds.upperBound()<1) {
//      System.out.println("lower bound < 1");
      builder.add(minusState, 1d-bounds.upperBound());

    }
    if(bounds.difference()>0) {
//      System.out.println("difference is"+ bounds.difference());
        builder.add(uncertainState, bounds.difference());
    }

    return builder.scaled();

  }

  /**
   * Collapses the stateList into a set of representative states. If representatives for subsets already exist, they
   * will be merged into a single representative state. The stay action is updated for all representative states.
   * @param stateList A list of Integer sets where each set consists of the states in a single MEC.
   * @return An Integer list consisting of the representatives of the respective MECs.
   */
  @Override
  public IntList collapse(List<? extends IntSet> stateList){

    // The key set of the stayActionMap should have an old list of representatives
    IntSet oldRepresentatives = stayActionMap.keySet();
    // New set of representatives
    IntList newRepresentatives = super.collapse(stateList);

    // These 2 operations give removedRepresentatives = oldRepresentatives-newRepresentatives
    NatBitSet removedRepresentatives = NatBitSets.copyOf(oldRepresentatives);
    newRepresentatives.forEach((IntConsumer) removedRepresentatives::remove);

    // Note that oldRepresentatives may also contain representatives that are not a part of the stateList. These
    // maybe previously collapsed components' representatives. The below if condition should filter out such
    // representatives from removedRepresentatives.
    for (int oldRepresentative : oldRepresentatives) {
      if (oldRepresentative==representative(oldRepresentative)){
        removedRepresentatives.remove(oldRepresentative);
      }
    }

    for(int i: newRepresentatives){
      // The below condition will be true when we collapse a new group with zero members in removedRepresentatives.
      // For such a group, l = 0, u = 1
      if(!stayActionMap.containsKey(i)){
        updateStayAction(i, Bounds.reachUnknown());
      }
    }

    // For every group, we need to have only one stayAction to update. However, the collapse operation may create groups
    // with more than one stayActions as it may now have more than 1 members from oldRepresentatives. This loop merges
    // the stay actions for such groups.
    for (int removedRepresentative : removedRepresentatives) {
      Bounds removedRepresentativeBound = getBoundsFromStayAction(stayActionMap.get(removedRepresentative));

      // it can be the case that "stateList" only consists of newComponents.
      if (this.representative(removedRepresentative)!=removedRepresentative) {
        // Removing stay action of removed representative
        stayActionMap.remove(removedRepresentative);
      }
      else{
        continue;
      }

      // Representative of group to which removedRepresentative now belongs
      int representative = this.representative(removedRepresentative);
      assert representative!=removedRepresentative;

      Bounds representativeBound = getBoundsFromStayAction(stayActionMap.get(representative));

      // Creating new bounds based on both stayAction bounds.
      double newLowerBound = Math.max(removedRepresentativeBound.lowerBound(), representativeBound.lowerBound());
      double newUpperBound = Math.max(removedRepresentativeBound.upperBound(), representativeBound.upperBound());
      Bounds newRepresentativeBound = Bounds.of(newLowerBound, newUpperBound);

      // Update stayAction of group
      updateStayAction(representative, newRepresentativeBound);

    }

    return newRepresentatives;
  }

  /**
   * @param state: Integer value of state from which choices are to be found.
   * @return Returns the choices from a state in the collapsed model.
   */
  @Override
  public List<Distribution> getChoices(int state){
    // Get the choices from the collapse model
    List<Distribution> choices;

    // CollapseView.getChoices() returns all actions from a state, but the self-loops. This may produce states with
    // dead-ends and ignore several MECs(single states with self-loops). This can be bad for reward calculation. So we
    // need to handle such cases separately.

    // If a state is a key in stayActionMap, it must be a representative of a few collapsed states. If the state is a
    // representative of collapsed states, we don't need to take care of self-loops. We have already taken care of the
    // rewards for these states during VI.
    if(stayActionMap.containsKey(state)) {

      choices = new ArrayList<>(super.getChoices(state));
      // Adding the stay action if there is one associated with representative
      choices.add(stayActionMap.get(state));
    }
    else{
      // For states that aren't collapsed yet, we need to fetch the distributions directly from the model. The
      // Model.getChoices() function doesn't ignore self loops. However, at the same time, we also need to take care of
      // choices that may point to states that are now collapsed into some MECs.
      choices = getModel().getChoices(state);
      List<Distribution> transformedChoices = new ArrayList<>();

      // Function to remap distributions
      IntUnaryOperator map = successor -> {
        int representative = representative(successor);
        // Checking if the state hasn't been removed
        assert !isRemoved(representative);
        return representative == state ? -1 : representative;
      };

      // There may be some distributions for which we don't need to remap distributions at all. This predicate checks
      // if the choice points to a removed state or if the choice has a self-loop, but with other transitions. Note
      // that choices with a single self loop would be deemed "unchanged".
      Predicate<Distribution> unchanged = d -> !d.containsOneOf(this.removedStates()) &&
              !(d.contains(state) && d.support().size()>1);

      // This checks if the choice has a self-loop along with other transitions. Since self loops are going to be
      // removed from the choice, we need to accordingly scale these distributions.
      Predicate<Distribution> removed = d -> d.contains(state) && d.support().size()>1;

      for (Distribution choice : choices) {
        if (!unchanged.test(choice)) {
          DistributionBuilder builder = choice.map(map);
          if (removed.test(choice)) {
            choice = builder.scaled();
          }
          else {
            choice = builder.build();
          }
        }

        transformedChoices.add(choice);
      }

      choices = transformedChoices;
    }

    return choices;
  }

  /**
   * @param stayAction: Distribution of the stay action.
   * @return Returns upper and lower bound for a state from it's stayAction distribution
   */
  public static Bounds getBoundsFromStayAction(Distribution stayAction){
    double upperBound = 1d-stayAction.get(minusState);
    double lowerBound = stayAction.get(plusState);

    return Bounds.reach(lowerBound, upperBound);
  }

  /**
   * @param representative: Integer value of the representative state of an MEC.
   * @return Returns the distribution of the stay action from the representative state.
   */
  public Distribution getStayAction(int representative){
    assert stayActionMap.containsKey(representative);

    return stayActionMap.get(representative);
  }

}
