package de.tum.in.pet.implementation.qp_meanpayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.implementation.qp_meanpayoff.LPRewardProvider;
import de.tum.in.pet.implementation.qp_meanpayoff.MecInformationProvider;
import de.tum.in.probmodels.model.Distribution;
import it.unimi.dsi.fastutil.ints.*;
//This class is used to create a stochastic game from a given MEC. Player 1 chooses the actions
//while Player 2 chooses the probabilities. The choice of probabilities are given based on thesis
// by Maximillian Weininger.
public class StochasticGameMec {

    private final NatBitSet Player1; //Player 1 states

    private final NatBitSet Player2; //Player 2 states, the states are named in such a way that, an action 2 from player1
    //  state 1 would lead to Player2 state 12.

    private Int2DoubleMap Player2rewards; // Maps the reward obtained from each player2 state

    private final Int2ObjectMap<IntSet> get_actions_p1; //Player 1 actions,

//    private final Int2ObjectMap<IntSet> get_unique_actions_p1; // Player 1 actions with unique name

    private final Int2ObjectMap<Int2IntMap> get_successor_p2; //gets the Player 2 state from Player 1 state and action

    //    private final Int2ObjectMap<GameDistribution> p1_action_dist;// Transitions and distribution of an action
    private final Int2ObjectMap<IntSet> get_actions_p2; // Player 2 actions
    private final Int2ObjectMap<GameDistribution> p2_action_dist;// Transitions and distribution of a
    private int Player_2_state_count; //keeps track of the number of Player 2 states
    private final MecInformationProvider mecInfoProvider;
    private int Player_2_action_count ;// keeps track of action counts in the addDistribution function

//    private int Player_1_action_count; //keeps track of player 1 action counts
//    //for unique action number

    private final double pMin;

    private final LPRewardProvider rewardProvider;


    private final boolean enableLog;

    public StochasticGameMec(NatBitSet states, MecInformationProvider mecInfoProvider, boolean enableLog, double pMin, LPRewardProvider rewardProvider) {
        this.mecInfoProvider = mecInfoProvider;
        this.enableLog = enableLog;
        this.Player1 = states;
        get_actions_p1 = new Int2ObjectOpenHashMap<>();
        Player_2_state_count = Player1.lastInt();
        Player2 = NatBitSets.ensureModifiable(NatBitSets.simpleSet());
        get_successor_p2 = new Int2ObjectOpenHashMap<>();
        get_actions_p2 = new Int2ObjectOpenHashMap<>();
        p2_action_dist = new Int2ObjectOpenHashMap<>();
        this.pMin = pMin;
        Player_2_action_count = 0;
        this.rewardProvider = rewardProvider;
        this.Player2rewards = new Int2DoubleOpenHashMap();
    }

    //This functions creates the blown up stochastic game by linear or exponential blow up
    public void createSG(boolean ifbylinearblowup) {
        getP1P2Information(ifbylinearblowup);
    }
    //For each state action pair of the MDP we add a player 2 state and then add actions for those states
    private void getP1P2Information(boolean ifbylinearblowup){
        for (int p1_state : Player1)
        {
            IntSet actions = mecInfoProvider.provideActions(p1_state);
            get_actions_p1.put(p1_state, actions);//The set of actions are added to the variable
            Int2IntMap p1ActionsToP2Successor = new Int2IntArrayMap();
            for (int action : actions)
            {
                Player_2_state_count ++;
                int p2state = Player_2_state_count;
                Player2.set(p2state);
                Player2rewards.put(p2state, getRewardP1(p1_state,action));
                p1ActionsToP2Successor.put(action, p2state);
                if (ifbylinearblowup)
                    addP2ActionsLinear(p1_state, p2state, action);
                else
                    addP2Actions(p1_state, p2state , action);

            }
            get_successor_p2.put(p1_state, p1ActionsToP2Successor);

        }

    }

    //This function add the actions to player 2 states where the number of actions added
    // are linear to the number of transitions, here the confidence width is single sided
    // ie, the upper bound is 1.
    private void addP2ActionsLinear(int p1_state,int p2_state, int p1_action)
    {
        Distribution dist = mecInfoProvider.provideDistribution(p1_state, p1_action);
        double confwidth = mecInfoProvider.provideConfidenceWidth(p1_state, p1_action);
        confwidth = rounded(confwidth);
        int successor_size  = dist.size();
        int [] successor_states = new int[successor_size];
        double [] estimated_probs = new double[successor_size];
        double [] lower_bounds = new double[successor_size];
        int i =0 ;
        double sum = 0;
        for (Int2DoubleMap.Entry entry : dist)
        {
            double probability = entry.getDoubleValue();
            probability = rounded(probability);
            estimated_probs[i] = probability;
            successor_states[i] = entry.getIntKey();
            lower_bounds[i] = rounded(Math.max(probability-confwidth,pMin));
            sum += lower_bounds[i];
            i++;
        }
        if(confwidth == 0)
        {
            addp2action(p2_state, successor_states, estimated_probs);
        }
        else
        {
            for (i = 0 ; i< estimated_probs.length; i++)
            {
                addProbabilityDistributionsLinear (p2_state,lower_bounds, successor_states, i, estimated_probs.length, sum);
            }

        }



    }
    // Add the probability distributions which satisfies all the conditions
    private void addProbabilityDistributionsLinear(int p2state, double[] lower_bounds, int[] successor_states, int same, int size, double sum)
    {
        double [] probabilities = new double [successor_states.length];
        System.arraycopy(lower_bounds, 0, probabilities, 0, size);
        probabilities[same] = rounded(probabilities[same] + 1 - sum);
        addp2action(p2state, successor_states, probabilities);

    }


    // Each action is probability distribution where the probabilities are in the interval of
    // sampled probability +- width .
    private void addP2Actions(int p1_state,int p2_state, int p1_action)
    {
        Distribution dist = mecInfoProvider.provideDistribution(p1_state, p1_action);
        double confwidth = mecInfoProvider.provideTwoSidedConfidenceWidth(p1_state, p1_action);
        confwidth = rounded(confwidth);
        int successor_size  = dist.size();
        int [] successor_states = new int[successor_size];
        double [] estimated_probs = new double[successor_size];
        double [] upper_bounds = new double[successor_size];
        double [] lower_bounds = new double[successor_size];
        int i =0 ;
        for (Int2DoubleMap.Entry entry : dist)
        {
            double probability = entry.getDoubleValue();
            probability = rounded(probability);
            estimated_probs[i] = probability;
            successor_states[i] = entry.getIntKey();
            upper_bounds[i] = rounded(Math.min(probability+confwidth, 1));
            lower_bounds[i] = rounded(Math.max(probability-confwidth,pMin));
            i++;
        }
        if(confwidth == 0)
        {
            addp2action(p2_state, successor_states, estimated_probs);
        }
        else
        {
            for (i = 0 ; i< estimated_probs.length; i++)
            {
                addProbabilityDistributions (p2_state,estimated_probs, upper_bounds, lower_bounds,successor_states, i , 0, estimated_probs.length);
            }

        }



    }
    // Add the probability distributions which satisfies all the conditions
    private void addProbabilityDistributions(int p2state, double[] estimated_probs, double[] upper_bounds, double[] lower_bounds, int[] successor_states, int same, int filled, int size)
    {
        if ( filled == size)
        {
            double sum = 0.0;
            double last_val;
            for (int i = 0 ; i < size; i++)
            {
                if (i!= same)
                    sum += estimated_probs[i];
            }
            last_val = rounded(1- sum);
            if (last_val >= lower_bounds[same] && last_val <= upper_bounds[same])
            {
                estimated_probs[same] = rounded(last_val);
                double[] temp = new double[estimated_probs.length];
                for (int i =0 ; i < estimated_probs.length; i++)
                {
                    temp[i] = estimated_probs[i];
                }
                addp2action(p2state, successor_states , temp);

            }
        }

        else
        {
            double [] addlower = new double[estimated_probs.length];
            double [] addupper = new double[estimated_probs.length];
            for(int i = 0;i< estimated_probs.length; i++)
            {
                addlower[i]= estimated_probs[i];
                addupper[i]= estimated_probs[i];
            }
            if(filled == same)
            {
                filled++;
                addProbabilityDistributions(p2state,addlower,upper_bounds,lower_bounds,successor_states,same,filled,size);
            }
            else
            {
                addupper[filled] = upper_bounds[filled];
                addlower[filled] = lower_bounds[filled];
                filled++;
                addProbabilityDistributions(p2state,addlower,upper_bounds,lower_bounds,successor_states,same,filled,size);
                addProbabilityDistributions(p2state,addupper,upper_bounds,lower_bounds,successor_states,same,filled,size);
            }





        }


    }

    // The given probability distribution satisfying all the conditions is added as a Player2 action
    private void addp2action(int p2state, int[] successor_states , double[] probabilities)
    {
        Player_2_action_count++;
        if(get_actions_p2.get(p2state) == null)
        {
            IntSet action = new IntArraySet();
            action.add(Player_2_action_count);
            get_actions_p2.put(p2state, action);
        }
        else
        {
            IntSet action = get_actions_p2.get(p2state);
            action.add(Player_2_action_count);
            get_actions_p2.put(p2state,action);
        }
        GameDistribution gd = new GameDistribution(probabilities, successor_states, Player_2_action_count);
        p2_action_dist.put(Player_2_action_count, gd);


    }

    // Rounds to 4 decimal places
    private double rounded (double val)
    {
        double round_val = Math.round(val * 10000.0) / 10000.0;
        return round_val;
    }

    public void displaySG() {
        for (int statep1 : Player1) {
            IntSet actionsp1 = get_actions_p1.get(statep1);
            for (int actionp1 : actionsp1) {
                int statep2 = get_successor_p2.get(statep1).get(actionp1);
                System.out.println("Player 1 state " + statep1 + " on action "
                        + actionp1 + " goes to Player 2 state " + statep2);
                IntSet actionsp2 = get_actions_p2.get(statep2);
                for (int actionp2 : actionsp2) {

                    GameDistribution gd = getP2distribution(statep2, actionp2);
                    System.out.println("Player 2 state " + statep2 + " on action " +
                            actionp2 + " gives the following");
                    gd.printDistribution();
                }

            }

        }
    }

    public GameDistribution getP2distribution (int state, int action)
    {
        GameDistribution gd = p2_action_dist.get(action);
        return gd;
    }

    public double getRewardP1(int state , int action)
    {
        double transitionReward = rewardProvider.transitionReward(state, action, null);
        double stateReward= rewardProvider.stateReward(state);
        double return_val = rounded(transitionReward + stateReward);
        return return_val;

    }

    public double getRewardP2(int state)
    {
        return Player2rewards.get(state);
    }

    public NatBitSet player1()
    {
        return Player1;
    }

    public NatBitSet player2() {
        return Player2;
    }

    public IntSet getActionP1(int state)
    {
        return get_actions_p1.get(state);
    }
    public IntSet getActionP2(int state)
    {
        return get_actions_p2.get(state);
    }

    public int getP2Successor(int state, int action)
    {return  get_successor_p2.get(state).get(action);}

    public void printstates()
    {
        System.out.println("Player 1 states:");
        for (int state : Player1)
        {
            System.out.print(state+" ");
        }

        System.out.println("Player 2 states: ");
        for(int state : Player2)
        {
            System.out.print(state + " ");
        }
    }

    public void addPlayer1Actions(Int2ObjectMap<Int2DoubleMap> xa_values)
    {
        for (int state : Player1)
        {
            IntSet actions = getActionP1(state);
            Int2DoubleMap actionsValues = xa_values.get(state);
        }
    }





}