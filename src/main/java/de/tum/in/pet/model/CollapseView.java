package de.tum.in.pet.model;

import com.google.common.collect.Sets;
import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.naturals.unionfind.IntArrayUnionFind;
import de.tum.in.naturals.unionfind.IntUnionFind;
import explicit.SuccessorsIterator;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import prism.ModelType;

public class CollapseView<M extends Model> extends AbstractModel implements CollapseModel<M> {
  private static final Logger logger = Logger.getLogger(CollapseView.class.getName());
  private final IntUnionFind collapseUF = new IntArrayUnionFind(0);
  private final NatBitSet removedStates = NatBitSets.set();
  private final NatBitSet representativeStates = NatBitSets.set();
  private final M model;
  private final Int2ObjectMap<List<Distribution>> overwrite = new Int2ObjectOpenHashMap<>();
  private final NatBitSet overwriteCacheValid = NatBitSets.set();

  public CollapseView(M model) {
    this.model = model;
  }

  @Override
  public ModelType getModelType() {
    return model.getModelType();
  }

  @Override
  public int getNumStates() {
    return model.getNumStates() - removedStates.size();
  }

  @Override
  public int getNumTransitions() {
    throw new UnsupportedOperationException();
  }

  @Override
  public IntCollection getInitialStates() {
    IntCollection initialStates = model.getInitialStates();
    IntSortedSet states = new IntAVLTreeSet();
    initialStates.forEach((int state) -> states.add(representative(state)));
    return states;
  }

  @Override
  public boolean isInitialState(int state) {
    return model.isInitialState(representative(state));
  }

  @Override
  public List<Distribution> getChoices(int state) {
    assert !isRemoved(state);

    List<Distribution> distributions;
    if (overwriteCacheValid.contains(state)) {
      // This state has already been updated
      distributions = overwrite.get(state);
      if (distributions == null) {
        // State retains its original transitions
        distributions = model.getChoices(state);
      }
    } else {
      distributions = overwrite.get(state);
      if (distributions == null) {
        // Make the list modifiable
        distributions = new ArrayList<>(model.getChoices(state));
      }

      // Update the cache
      boolean anyDifferent = false;

      ListIterator<Distribution> iterator = distributions.listIterator();
      while (iterator.hasNext()) {
        Distribution distribution = iterator.next();
        if (distribution.containsOneOf(removedStates)) {
          anyDifferent = true;
          iterator.set(distribution.map(this::representative));
        } else {
          assert Objects.equals(distribution, distribution.map(this::representative));
        }
      }
      if (anyDifferent) {
        Set<Distribution> uniqueDistributions = new HashSet<>(distributions);
        if (representativeStates.contains(state)) {
          // Remove self-loops
          uniqueDistributions.remove(new Distribution(state, 1.0d));
        }

        List<Distribution> newDistributions = new ArrayList<>(uniqueDistributions);
        overwrite.put(state, newDistributions);
        distributions = newDistributions;
      }

      overwriteCacheValid.set(state);
    }

    assert distributions.stream()
        .map(Distribution::support).flatMap(Collection::stream).distinct()
        .allMatch(s -> s == representative(s));

    return Collections.unmodifiableList(distributions);
  }

  @Override
  public Distribution getChoice(int state, int action) {
    return getChoices(state).get(action);
  }

  @Override
  public List<Action> getActions(int state) {
    return getChoices(state).stream().map(Action::of).collect(Collectors.toList());
  }

  @Override
  public SuccessorsIterator getSuccessors(int s) {
    List<Distribution> choices = getChoices(s);
    if (choices.isEmpty()) {
      return SuccessorsIterator.empty();
    }
    if (choices.size() == 1) {
      return SuccessorsIterator.from(choices.get(0).support().iterator(), true);
    }

    NatBitSet union = NatBitSets.set();
    choices.forEach(d -> union.or(d.support()));
    return SuccessorsIterator.from(union.iterator(), true);
  }


  @Override
  public IntList collapse(List<? extends IntSet> stateList) {
    if (stateList.isEmpty()) {
      return IntLists.EMPTY_LIST;
    }

    assert stateList.stream().allMatch(states ->
        stateList.stream().noneMatch(others -> others != states
            && !Sets.intersection(states, others).isEmpty()));

    logger.log(Level.FINER, "Collapsing state sets {0}", stateList);

    NatBitSet newCollapsedStates = NatBitSets.set();
    stateList.forEach(newCollapsedStates::or);

    // Only collapse states of the collapsed model
    assert !newCollapsedStates.intersects(removedStates);

    // Collapse the states
    IntList representatives = new IntArrayList(stateList.size());
    for (IntSet states : stateList) {
      // collapse() also updates collapsedStates
      representatives.add(collapse(states));
    }
    logger.log(Level.FINER, "Representatives: {0}", representatives);

    // Representatives are consistent
    assert stateList.stream().allMatch(states ->
        states.stream().mapToInt(this::representative).allMatch(states::contains));
    assert stateList.stream().allMatch(states ->
        states.stream().mapToInt(this::representative).distinct().count() == 1L);
    assert stateList.stream().flatMap(Collection::stream).mapToInt(this::representative)
        .distinct().count() == stateList.size();

    for (int i = 0; i < stateList.size(); i++) {
      IntSet states = stateList.get(i);
      int representativeState = representatives.getInt(i);
      assert states.contains(representativeState)
          && representativeState == representative(representativeState);

      Collection<Distribution> collapsedDistributions = new HashSet<>();

      // Delete transitions of all states in the MEC, only keep outgoing ones
      states.forEach((int state) -> {
        List<Distribution> distributions = overwrite.get(state);
        if (distributions == null) {
          distributions = model.getChoices(state);
        }

        distributions.forEach(distribution -> {
          // Build filtered distribution (only outgoing transitions)
          Distribution newDistribution = distribution.map(successor -> {
            int successorRepresentative = representative(successor);
            return successorRepresentative == representativeState ? -1 : successorRepresentative;
          });

          if (!newDistribution.isEmpty()) {
            newDistribution.scale();
            // No internal transitions will be added (important for correctness)
            assert newDistribution.support().intStream().noneMatch(states::contains);
            collapsedDistributions.add(newDistribution);
          }
        });
        overwrite.remove(state);
      });

      // All states are cleared
      assert states.stream().mapToInt(Integer::intValue)
          .mapToObj(overwrite::get).allMatch(Objects::isNull);
      overwrite.put(representativeState, new ArrayList<>(collapsedDistributions));
      representatives.add(representativeState);
    }

    // Collapsed states are empty
    assert overwrite.keySet().stream().noneMatch(this::isRemoved);

    // Remap all transitions. Other states might be pointing to some now merged state - we have
    // to update them too.
    overwriteCacheValid.and(representatives);
    overwrite.keySet().retainAll(representatives);

    if (logger.isLoggable(Level.INFO)) {
      int transitionCount = 0;
      int actionCount = 0;
      int maxTransitions = 0;
      int maxActions = 0;
      int stateCount = representatives.size();
      for (int representative : representatives) {
        List<Distribution> distributions = getChoices(representative);
        if (distributions == null) {
          continue;
        }
        actionCount += distributions.size();
        maxActions = Math.max(maxActions, distributions.size());
        for (Distribution distribution : distributions) {
          transitionCount += distribution.size();
          maxTransitions = Math.max(maxTransitions, distribution.size());
        }
      }
      logger.info(String.format("Collapsed states: %d, Actions: %.2f avg/%d max, "
              + "Transitions %.2f avg/%d max", stateCount, actionCount / (double) stateCount,
          maxActions, transitionCount / (double) stateCount, maxTransitions));
    }

    return representatives;
  }

  private int collapse(IntSet states) {
    assert !states.isEmpty();
    if (states.size() == 1) {
      int state = states.iterator().nextInt();
      representativeStates.set(state);
      return state;
    }
    int modelStates = model.getNumStates();
    if (collapseUF.size() <= modelStates) {
      collapseUF.add(modelStates * 2 + 1);
    }

    int anyState = representative(states.iterator().nextInt());
    states.forEach((int state) -> collapseUF.union(anyState, state));
    int representativeState = representative(anyState);

    removedStates.or(states);
    removedStates.clear(representativeState);
    representativeStates.set(representativeState);

    return representativeState;
  }

  @Override
  public int representative(int state) {
    if (state >= collapseUF.size()) {
      return state;
    }
    return collapseUF.find(state);
  }

  @Override
  public boolean isRemoved(int state) {
    assert (representative(state) != state) == removedStates.contains(state);
    return representative(state) != state;
  }

  @Override
  public NatBitSet removedStates() {
    return removedStates;
  }

  @Override
  public M getModel() {
    return model;
  }


  // Mutators

  @Override
  public void setInitialStates(Collection<Integer> initialStates) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addChoice(int state, Distribution distribution) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addChoice(int state, Action action) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setChoice(int state, int action, Distribution distribution) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setActions(int state, List<Action> actions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addInitialState(int i) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearState(int i) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int addState() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addStates(int numToAdd) {
    throw new UnsupportedOperationException();
  }
}
