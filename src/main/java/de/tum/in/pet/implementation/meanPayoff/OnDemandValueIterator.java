package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.sampler.AnnotatedModel;
import de.tum.in.pet.sampler.Iterator;
import de.tum.in.pet.sampler.UnboundedValues;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.explorer.Explorer;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.graph.Mec;
import de.tum.in.probmodels.graph.MecComponentAnalyser;
import de.tum.in.probmodels.model.*;
import it.unimi.dsi.fastutil.ints.*;
import prism.Pair;
import prism.PrismException;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static de.tum.in.probmodels.util.Util.isZero;

// This class implements the OnDemand VI Algorithm from the CAV'17 paper.
public class OnDemandValueIterator<S, M extends Model> implements Iterator<S, M> {
  protected static final Logger logger = Logger.getLogger(OnDemandValueIterator.class.getName());

  protected final Explorer<S, M> explorer;
  protected final UnboundedValues values;
  private final BoundedMecQuotient<M> boundedMecQuotient;
  protected final RewardGenerator<S> rewardGenerator;

  protected final MecComponentAnalyser mecAnalyser = new MecComponentAnalyser();

  protected final double precision;
  protected final int revisitThreshold;
  protected final double rMax;

  // variable keeps track if new states have been explored and if handleComponents() needs to be run again.
  protected boolean newStatesSinceCollapse = false;

  // stores most recent VI results for all states.
  protected Int2ObjectMap<Int2DoubleMap> mecValueCache = new Int2ObjectOpenHashMap<>();

  protected List<Pair<Long, Bounds>> timeVBound = new ArrayList<>();

  protected List<Double> qp_result = new ArrayList<>();

  protected final long timeout;

  // Each string will be added to the temp.txt file.
  protected final List<String> additionalWriteInfo = new ArrayList<>();

  public OnDemandValueIterator(Explorer<S, M> explorer, UnboundedValues values, RewardGenerator<S> rewardGenerator, 
                               int revisitThreshold, double rMax, double precision, long timeout) {
    this.explorer = explorer;

    this.values = values;
    this.rewardGenerator = rewardGenerator;

    this.revisitThreshold = revisitThreshold;
    this.boundedMecQuotient = new BoundedMecQuotient<>(explorer.model());
    this.rMax = rMax;

    this.precision = precision;

    this.timeout = timeout;

  }

  /**
   * Returns explorer object consisting of the partial model.
   */
  @Override
  public Explorer<S, M> explorer() {
    return explorer;
  }

  public List<Pair<Long, Bounds>> getTimeVBound() {
    return timeVBound;
  }

  @Override
  public AnnotatedModel<M> model() {
    IntSet exploredStates = new IntOpenHashSet(explorer.exploredStates());
    exploredStates.removeIf((int state) -> values.isUnknown(boundedMecQuotient.representative(state)));
    return new AnnotatedModel<>(explorer.model(), explorer::getState, exploredStates);
  }

  /**
   * Returns lower and upper bounds of the reachability value of state.
   * @param state: Integer value of state for which value is to be found.
   */
  @Override
  public Bounds bounds(int state) {
    return values.bounds(this.boundedMecQuotient.representative(state));
  }

  /**
   * Initialize Sink State bounds.
   */
  protected void initSinkStates(){

    int plusState = BoundedMecQuotient.getPlusState();
    int minusState = BoundedMecQuotient.getMinusState();
    int uncertainState = BoundedMecQuotient.getUncertainState();

    assert values.bounds(plusState).lowerBound()==1;

    values.update(minusState, List.of());
    assert values.bounds(minusState).upperBound()==0;

    assert values.bounds(uncertainState).equals(Bounds.reachUnknown());

  }

  /**
   * Run OnDemandVI Algorithm.
   */
  public void run() throws PrismException {

    initSinkStates();

    logger.log(Level.INFO, "Initialized Sink States.");

    int run = 0;

    int initialState = explorer.initialStates().stream().findFirst().orElse(-1);
    assert initialState!=-1: "Explorer has no initial state";
    int representative = boundedMecQuotient.representative(initialState);

    // isSolved() defined in UnboundedReachValues
    while(!(values.isSolved(representative)|| isTimeout())) {  // The values between upper and lower bounds for the initial states should,be less than epsilon
//      logger.log(Level.INFO, "Run "+run);
      logger.log(Level.INFO, values.bounds(representative).toString());
      if (sample(representative, run)) {
        // initialState may be part of an MEC and the MEC may be collapsed, and we may have a representative that is different
        // from initialState
        representative = boundedMecQuotient.representative(initialState);
      }
      timeVBound.add(new Pair<>(System.currentTimeMillis(), Bounds.of(this.rMax*bounds(initialState).lowerBound(), this.rMax*bounds(initialState).upperBound())));
      run++;  // count of episodic runs
      if (run%1000==0){
//        logger.log(Level.INFO, "Bounds "+bounds(representative));
      }
    }

    onSamplingFinished(initialState);
  }

  protected boolean isTimeout() {
    return System.currentTimeMillis() > timeout;
  }


  /**
   * This function will be called once the sampling is done. Either the bounds converged or time limit exceeded.
   *
   * @param initialState There can be multiple initial states in the model. But we pick one and check reachability.
   *                     This variable corresponds to the initial state we used to get reachability.
   */
  protected void onSamplingFinished(int initialState) {
    additionalWriteInfo.add(String.valueOf(explorer.exploredStateCount()));
  }

  /**
   * Simulate a single iteration of the OnDemandVI algorithm. Encapsulates sample, findMec and update functions
   * of the algorithm.
   * @param initialState: Integer value of state from which sampling should start.
   * @param run: Integer value representing how many iterations of the algorithm have been done.
   * @return whether MECs were updated during the simulation.
   */
  protected boolean sample(int initialState, int run) throws PrismException {

    IntStack visitStack = new IntArrayList();
    int currentState = initialState;
    Int2IntOpenHashMap stateVisitCounts = new Int2IntOpenHashMap();  // keeps counts of the number of times a state is visited

    boolean updatedEC = false;
    boolean foundDesignatedSinkState = false;

    while(true){

      visitStack.push(currentState);
      stateVisitCounts.putIfAbsent(currentState, 0);
      stateVisitCounts.addTo(currentState, 1);

      // checks plus state,minus state and uncertain state
      if(BoundedMecQuotient.isSinkState(currentState)){
        foundDesignatedSinkState = true;
        break;
      }
      if(stateVisitCounts.get(currentState)>=this.revisitThreshold){
        updatedEC = true;
        break;
      }

      if(!explorer.isExploredState(currentState)) {
        explore(currentState);  // action choices etc. are populated in the partial model. The bounds of currentState are also initialised.
      }

      int nextState = values.sampleNextState(currentState, choices(currentState));

      // This is true when the currentState doesn't have any choices from it, i.e. it is a sink state.
      if (nextState == -1) {
        break;
      }

      currentState = nextState;

    }

    if(updatedEC){
      handleComponents();
    }
    else{
      // We update the MEC reward bounds through running VI if we reach the uncertain or the plus state. This is
      // slightly different from the version in CAV'17 where VI is only run when the uncertain state is reached.
      // However, this is also OK as reaching the plus state shows that probably the lower reward bound is high
      // enough, meaning the EC is promising and it is worth getting a more precise value. We make sure in updateMEC
      // that we don't get value that is more precise than what is required.
      if(foundDesignatedSinkState) {
        int sinkState = visitStack.popInt();
        if (BoundedMecQuotient.isUncertainState(sinkState) || BoundedMecQuotient.isPlusState(sinkState)) {
          int mecRepresentative = visitStack.popInt();
          updateMec(mecRepresentative);
        }
      }
    }

    // update the values of the states along the path that is stored in visitStack (Line 18--21 of OnDemandVI algorithm from CAV'17 paper)
    while(!visitStack.isEmpty()){
      // In a path there can be at most one sink state at the end. If such a sink state appears in the path, then it would have already
      // been popped inside the else block above.
      int state = boundedMecQuotient.representative(visitStack.popInt());
      assert !BoundedMecQuotient.isSinkState(state);
      values.update(state, choices(state));
    }

    return updatedEC;

  }

  /**
   * @param mecRepresentative: mecRepresentative of MEC for which bounds reward bounds are desired.
   * @return Reward bounds of the MEC in question.
   */
  protected Bounds getMecBounds(int mecRepresentative){
    return BoundedMecQuotient.getBoundsFromStayAction(boundedMecQuotient.getStayAction(mecRepresentative));
  }

  // todo cache
  /**
   * @param mecRepresentative: mecRepresentative of the desired MEC.
   * @return MEC object for the desired mecRepresentative.
   */
  protected Mec getMec(int mecRepresentative){
    NatBitSet mecStates = NatBitSets.copyOf(explorer.exploredStates().stream()
            .filter(s -> boundedMecQuotient.representative(s)==mecRepresentative)
            .collect(Collectors.toList()));
    return Mec.create(explorer().model(), mecStates);
  }

  /**
   * Updates stay action of the MEC according to the given scaled bounds.
   * @param mecRepresentative: mecRepresentative of the desired MEC.
   * @param scaledBounds: scaled reward bounds for the MEC according to which the stay action is to be updated.
   */
  protected void updateStayAction(int mecRepresentative, Bounds scaledBounds){
    boundedMecQuotient.updateStayAction(mecRepresentative, scaledBounds);
  }

  /**
   * Implements lines 11-15 in CAV'17 paper. Runs VI on mec.
   * @param mecRepresentative: Representative state of mec on which VI has to be run.
   */
  protected void updateMec(int mecRepresentative){
    assert this instanceof BlackOnDemandValueIterator || boundedMecQuotient.representative(mecRepresentative) == mecRepresentative;

    // mecBounds now contain the scaled reward upper and lower bounds.
    Bounds mecBounds = getMecBounds(mecRepresentative);

    double currPrecision = mecBounds.difference()*this.rMax;

    if(currPrecision<this.precision){
      return;
    }

    double targetPrecision = currPrecision/2;

    logger.log(Level.INFO, "updating MEC");

    // get all the MEC states corresponding to mecRepresentative.
    Mec mec = getMec(mecRepresentative);

    if (mec.states.size()==0){
      return;
    }

    assert !isZero(targetPrecision);

    // Fetch the precomputed value map of the mec from the cache, and if there isn't any, then returns an empty map.
    // The key of the map is mecRepresentative.
    Int2DoubleMap valueCache = mecValueCache.computeIfAbsent(mecRepresentative, s -> new Int2DoubleOpenHashMap());

    // lambda function that returns a state object when given the state index. required for accessing reward generator function.
    Int2ObjectFunction<S> stateIndexMap = explorer::getState;

    RestrictedMecValueIterator<S, M> valueIterator = new RestrictedMecValueIterator<>(mec, targetPrecision, rewardGenerator, stateIndexMap, valueCache, rMax, timeout);
    valueIterator.setDistributionFunction(x -> y -> this.explorer.model().getChoice(x, y));
    valueIterator.setLabelFunction(x -> y -> this.explorer.model().getActions(x).get(y).label());

    valueIterator.run();

    Bounds newBounds = valueIterator.getBounds();
    Bounds scaledBounds = Bounds.of(newBounds.lowerBound()/this.rMax, newBounds.upperBound()/this.rMax);

    // In the case when we run VI after some new states have been added, the lower bounds may be worse than the
    // previously computed bounds. However, we know that the MEC's reward must be greater than the previously computed
    // lower bound value. Thus, we can use the previously computer lower bound value for slightly faster convergence.
    scaledBounds = scaledBounds.withLower(Math.max(scaledBounds.lowerBound(), mecBounds.lowerBound()));

    updateStayAction(mecRepresentative, scaledBounds);

    valueCache = valueIterator.getValues();
    mecValueCache.put(mecRepresentative, valueCache);
  }


  /**
   * Implements OnTheFlyEC from CAV'17 paper.
   */
  public void handleComponents(){

    if(!newStatesSinceCollapse){
      return;
    }

    newStatesSinceCollapse = false;
    logger.log(Level.INFO, "Searching components");

    NatBitSet states = NatBitSets.copyOf(explorer.exploredStates());
    states.removeAll(boundedMecQuotient.removedStates()); // states variable now stores only the states in the current collapsed partial model.

    List<NatBitSet> newComponents = mecAnalyser.findComponents(boundedMecQuotient, states);  // find all MECs in the partial model.
    // This contains only newly found components. Since all previously found components are collapsed, they won't be recognized as MECs anymore.

    if(newComponents.isEmpty()){
      return;
    }

    if (logger.isLoggable(Level.FINE)) {
      int count = newComponents.stream().mapToInt(NatBitSet::size).sum();
      logger.log(Level.FINE, "Found {0} new components with {1} new states",
              new Object[] {newComponents.size(), count});
    }

    // This collapses the sets of states into representatives. Further, the stay action is added here.
    IntList representatives = boundedMecQuotient.collapse(newComponents);
    var collapseIterator = newComponents.iterator();
    IntIterator representativeIterator = representatives.iterator();

    while(collapseIterator.hasNext()){
      // the components and the corresponding representatives are in the same order.
      int representative = representativeIterator.nextInt();

      // Removing from cache as new states have been added to mec and all values need to computed again from start.
      mecValueCache.remove(representative);

      // Reset stay action bounds as mec has been expanded
      boundedMecQuotient.updateStayAction(representative, Bounds.of(BoundedMecQuotient.getBoundsFromStayAction(boundedMecQuotient.getStayAction(representative)).lowerBound(), 1));

      // We need to run VI on the MEC again to account for the following case. It can be that the bounds on the MEC are
      // already very precise. Thus, the probability of reaching the uncertain state would be very small and we may
      // never be able to run VI on the newly added states again. Thus, we need to run VI straight after adding new
      // states.
      updateMec(representative);

      // updates the bounds of the representative according to all actions of mec members going out of the MEC.
      values.collapse(representative, choices(representative), collapseIterator.next());
    }

  }

  /**
   * Add state to partial model.
   * @param state: Integer value of state to be explored.
   */
  protected void explore(int state) throws PrismException {
    assert !explorer.isExploredState(state);
    assert !BoundedMecQuotient.isSinkState(state);
    newStatesSinceCollapse = true;
    explorer.exploreState(state);  //  state added to partial model, and explorer.isExploredState(state) is set to true.
  }

  /**
   * Fetch choices from state in partial model
   * @param state: Integer value of state from whom choices are required.
   * @return List of distributions of the choices from the state in the partial model.
   */
  protected List<Distribution> choices(int state) {
    assert explorer.isExploredState(state);
    assert !BoundedMecQuotient.isSinkState(state);
    return boundedMecQuotient.getChoices(state);
  }

}
