package de.tum.in.pet.implementation.qp_meanpayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.pet.values.Bounds;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
 //Conducts VI on the given Stochastic game, VI algorithm is similar to that of the one implemented in CAV' 22.
//The difference is in the update function where Player 2 picks the action that gives minimum value for
//lower bound. The update equations do not have greybox or blackbox distinction as the probability distributions
// sums to 1.
public class StochasticGameVI {
    private Int2DoubleMap upperboundsP1_i;

    private Int2DoubleMap player2Rewards;

    private Int2DoubleMap upperboundsP1_i1;

    private Int2DoubleMap lowerboundsP1_i;

    private Int2DoubleMap lowerboundsP1_i1;

    private Int2DoubleMap upperboundsP2_i;

    private Int2DoubleMap upperboundsP2_i1;

    private Int2DoubleMap lowerboundsP2_i;

    private Int2DoubleMap lowerboundsP2_i1;
    private  final  StochasticGameMec sg; // Input stochastic game

    private  Double lowerBound;

    private  Double upperBound;

    private long timeout;

    private double tau; //for adding aperiodicity as mentioned in Putterman section 8.5.4

     public StochasticGameVI(StochasticGameMec sg, double tau, long timeout )
     {
         this.sg = sg;
         upperboundsP1_i = new Int2DoubleOpenHashMap();
         lowerboundsP1_i = new Int2DoubleOpenHashMap();
         upperboundsP2_i = new Int2DoubleOpenHashMap();
         lowerboundsP2_i = new Int2DoubleOpenHashMap();

         upperboundsP1_i1 = new Int2DoubleOpenHashMap();
         lowerboundsP1_i1 = new Int2DoubleOpenHashMap();
         upperboundsP2_i1 = new Int2DoubleOpenHashMap();
         lowerboundsP2_i1 = new Int2DoubleOpenHashMap();
         player2Rewards = new Int2DoubleOpenHashMap();
         this.tau = tau;
         lowerBound= 0.0d;
         upperBound = 0.0d;
         this.timeout = timeout;
     }

     public void SolveSG(double precision)
     {
         boolean endcondition = false;
         NatBitSet Player1 = sg.player1();
         NatBitSet Player2= sg.player2();
         initialiseBounds();
         while(!endcondition && !isTimeout())
         {
             Int2DoubleMap uvaluesp1 = new Int2DoubleOpenHashMap();
             Int2DoubleMap lvaluesp1 = new Int2DoubleOpenHashMap();
             Int2DoubleMap uvaluesp2 = new Int2DoubleOpenHashMap();
             Int2DoubleMap lvaluesp2 = new Int2DoubleOpenHashMap();
             for(int player : Player1)  //player 1 is the maximizer
             {

                 double maxu = 0.0d;
                 double maxl = 0.0d;
                 IntSet actions = sg.getActionP1(player);
                 for (int action : actions)
                 {
                     double reward = sg.getReward(player, action);
                     int player2 = sg.getP2Successor(player, action);
                     player2Rewards.put(player2,reward);

                     double uvalue = getUValueP1(player, player2, reward);
                     double lvalue = getLValueP1( player, player2, reward);
                     maxu= Math.max(maxu,uvalue);
                     maxl= Math.max(maxl,lvalue);
                 }
                 uvaluesp1.put(player, maxu);
                 lvaluesp1.put(player,maxl);
             }

             for(int player : Player2) //player 2 is minimizer
             {
                 double maxu;
                 double minl;
                 IntSet actions = sg.getActionP2(player);
                 minl = Double.MAX_VALUE;
                 maxu = 0.0d;
                 for (int action : actions)
                 {
                     GameDistribution nextdist = sg.getP2distribution(player,action);
                     double uvalue = getUValueP2(player, nextdist);
                     double lvalue = getLValueP2(player, nextdist);
                     maxu = Math.max(maxu,uvalue);
                     minl = Math.min(minl,lvalue);
                 }
                 uvaluesp2.put(player, maxu);
                 lvaluesp2.put(player,minl);
             }
             for (int player : Player1)
             {

                 upperboundsP1_i.put(player, upperboundsP1_i1.get(player));
                 lowerboundsP1_i.put(player,lowerboundsP1_i1.get(player));
                 upperboundsP1_i1.put(player,uvaluesp1.get(player));
                 lowerboundsP1_i1.put(player,lvaluesp1.get(player));

             }


             for (int player : Player2)
             {

                 upperboundsP2_i.put(player, upperboundsP2_i1.get(player));
                 lowerboundsP2_i.put(player,lowerboundsP2_i1.get(player));
                 upperboundsP2_i1.put(player,uvaluesp2.get(player));
                 lowerboundsP2_i1.put(player,lvaluesp2.get(player));

             }

             endcondition = checkcondition(precision);
         }


     }

     private boolean checkcondition(double precision)
     {
         NatBitSet Player1 = sg.player1();
         NatBitSet Player2 = sg.player2();
         double max = 0.0;
         double min = Double.MAX_VALUE;
         for(int player : Player1)
         {
             double delta = rounded(upperboundsP1_i1.get(player) - upperboundsP1_i.get(player));
             min = Math.min(min,delta);
             max = Math.max(max,delta);
         }
         for(int player : Player2)
         {
             double delta = rounded(upperboundsP2_i1.get(player) - upperboundsP2_i.get(player));
             min = Math.min(min,delta);
             max = Math.max(max,delta);
         }
         if ((max - min) < precision)
             return true;
         max = 0.0;
         min = Double.MAX_VALUE;
         for(int player : Player1)
         {
             double delta = rounded(lowerboundsP1_i1.get(player) - lowerboundsP1_i.get(player));
             min = Math.min(min,delta);
             max = Math.max(max,delta);
         }
         for(int player : Player1)
         {
             double delta = rounded(lowerboundsP2_i1.get(player) - lowerboundsP2_i.get(player));
             min = Math.min(min,delta);
             max = Math.max(max,delta);
         }
         if ((max-min) < precision)
             return true;
         return false;

     }
     private double getUValueP1( int currstate, int nextstate, double reward )
     {
         double rounded = rounded(reward + (upperboundsP2_i1.get(nextstate) * tau * tau + tau * (1 - tau) * upperboundsP1_i1.get(currstate)) / tau);
         return rounded;

     }

     private double getLValueP1( int currstate, int nextstate, double reward )
     {
         double rounded = rounded(reward + (lowerboundsP2_i1.get(nextstate) * tau * tau + tau * (1 - tau) * lowerboundsP1_i1.get(currstate)) / tau);
         return rounded;

     }

     private double getUValueP2(int currstate, GameDistribution dist)
     {
         double reward = player2Rewards.get(currstate);
         int [] successors = dist.successors;
         double [] probabilities = dist.probabilities;
         double [] uppervals = new double[successors.length]; // An array for keepin all the upper values of the successors
         for(int i =0 ; i < successors.length; i++) {
             uppervals[i] = upperboundsP1_i1.get(successors[i]);
         }
         double uval = 0;
         for (int i = 0 ; i < successors.length ; i++ )
         {
             uval += probabilities[i] * uppervals[i] * tau * tau;
         }
         uval += (1-tau) * tau * upperboundsP2_i1.get(currstate);
         uval = uval / tau;
         uval += reward;
         return  rounded(uval);
     }

     private double getLValueP2(int currstate, GameDistribution dist)
     {
         double reward = player2Rewards.get(currstate);
         int [] successors = dist.successors;
         double [] probabilities = dist.probabilities;
         double [] lowervals = new double[successors.length]; // An array for keepin all the upper values of the successors
         for(int i =0 ; i < successors.length; i++) {
             lowervals[i] = lowerboundsP1_i1.get(successors[i]);
         }
         double lval = 0;
         for (int i = 0 ; i < successors.length ; i++ )
         {
             lval += probabilities[i] * lowervals[i] * tau * tau;
         }
         lval += (1-tau) * tau * lowerboundsP2_i1.get(currstate);
         lval = lval / tau;
         lval += reward;
         return  rounded(lval);
     }

     public void initialiseBounds()
     {
         NatBitSet Player1 = sg.player1();
         NatBitSet Player2 = sg.player2();
         for (int player : Player1)
         {
             upperboundsP1_i1.put(player, 0.0);
             lowerboundsP1_i1.put(player, 0.0);
         }
         for (int player : Player2)
         {
             upperboundsP2_i1.put(player,0.0);
             lowerboundsP2_i1.put(player, 0.0);
         }
     }

     private double rounded (double val)
     {
         return Math.round(val * 10000.0) / 10000.0;
     }

     public Bounds getBounds()
     {
         NatBitSet Player1 = sg.player1();
         NatBitSet Player2 = sg.player2();
         double mindiffl = Double.MAX_VALUE;
         double maxdiffu = 0.0d;
         for (int player : Player1)
         {
             double diffl = rounded(lowerboundsP1_i1.get(player) - lowerboundsP1_i.get(player));
             double diffu = rounded(upperboundsP1_i1.get(player) - upperboundsP1_i.get(player));
             mindiffl = Math.min(mindiffl,diffl);
             maxdiffu = Math.max(maxdiffu,diffu);
         }

         for (int player : Player2)
         {
             double diffl = lowerboundsP2_i1.get(player) - lowerboundsP2_i.get(player);
             double diffu = upperboundsP2_i1.get(player) - upperboundsP2_i.get(player);
             mindiffl = Math.min(mindiffl,diffl);
             maxdiffu = Math.max(maxdiffu,diffu);
         }
         lowerBound = rounded(mindiffl);
         upperBound = rounded(maxdiffu);
         return Bounds.of(lowerBound,upperBound);

     }

     private boolean isTimeout() {
         return System.currentTimeMillis() >= timeout;
     }




 }
