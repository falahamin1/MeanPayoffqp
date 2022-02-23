mdpConfigs = ["meanPayoff -m data/models/zeroconf_rewards.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.0002 --maxSuccessors 6 --iterSample 10000 --const N=40,K=10,reset=false --rewardModule reach",
              "meanPayoff -m data/models/sensors.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.05 --maxSuccessors 2 --iterSample 10000 --const K=3",
              "meanPayoff -m data/models/investor.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.016 --maxSuccessors 8 --iterSample 10000",
              "meanPayoff -m data/models/cs_nfail3.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.1 --maxSuccessors 2 --iterSample 10000",
              "meanPayoff -m data/models/consensus.2.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.5 --maxSuccessors 2 --iterSample 10000 -c K=2 --rewardModule custom",
              "meanPayoff -m data/models/ij.10.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.5 --maxSuccessors 2 --iterSample 10000",
              "meanPayoff -m data/models/ij.3.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.5 --maxSuccessors 2 --iterSample 10000",
              "meanPayoff -m data/models/pacman.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.08 --maxSuccessors 6 --iterSample 10000 -c MAXSTEPS=5",
              "meanPayoff -m data/models/wlan.0.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.0625 --maxSuccessors 16 --iterSample 10000 -c COL=0 --rewardModule default",
              "meanPayoff -m data/models/virus.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.1 --maxSuccessors 2 --iterSample 10000",
              "meanPayoff -m data/models/pnueli-zuck.3.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.5 --maxSuccessors 2 --iterSample 10000",
              "meanPayoff -m data/models/blackjack.prism --precision 0.01 --maxReward 1.5 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.076 --maxSuccessors 10 --iterSamples 10000",
              "meanPayoff -m data/models/counter.prism --precision 0.01 --maxReward 10 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.333 --maxSuccessors 2 --iterSamples 10000",
              "meanPayoff -m data/models/recycling.prism --precision 0.01 --maxReward 2 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.199 --maxSuccessors 2 --iterSamples 10000",
              "meanPayoff -m data/models/busyRing4.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.125 --iterSample 10000",
              "meanPayoff -m data/models/busyRingMC4.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.0625 --iterSample 10000"
              ]


ctmdpConfigs = ["meanPayoff -m data/ctmdpModels/DynamicPM-tt_3_qs_2_sctmdp.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.1516 --maxSuccessors 5 --iterSample 10000",

                "meanPayoff -m data/ctmdpModels/ErlangStages-k500_r10.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.05 --maxSuccessors 3 --iterSample 10000",
                "meanPayoff -m data/ctmdpModels/ErlangStages-k2000_r10.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.05 --maxSuccessors 3 --iterSample 10000",

                "meanPayoff -m data/ctmdpModels/PollingSystem-jt1_qs1_sctmdp.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.107 --maxSuccessors 3 --iterSample 10000",
                "meanPayoff -m data/ctmdpModels/PollingSystem-jt1_qs4_sctmdp.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.083 --maxSuccessors 5 --iterSample 10000",
                "meanPayoff -m data/ctmdpModels/PollingSystem-jt1_qs7_sctmdp.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.083 --maxSuccessors 5 --iterSample 10000",

                "meanPayoff -m data/ctmdpModels/QueuingSystem-lqs_1_rqs_1_jt_2_sctmdp.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.0265 --maxSuccessors 7 --iterSample 10000",
                "meanPayoff -m data/ctmdpModels/QueuingSystem-lqs_2_rqs_2_jt_3_sctmdp.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.0186 --maxSuccessors 9 --iterSample 10000",

                "meanPayoff -m data/ctmdpModels/SJS-procn_2_jobn_2_sctmdp.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.33 --maxSuccessors 2 --iterSample 10000",
                "meanPayoff -m data/ctmdpModels/SJS-procn_2_jobn_6_sctmdp.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.05 --maxSuccessors 3 --iterSample 10000",
                "meanPayoff -m data/ctmdpModels/SJS-procn_3_jobn_5_sctmdp.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.047 --maxSuccessors 4 --iterSample 10000",
                "meanPayoff -m data/ctmdpModels/SJS-procn_6_jobn_2_sctmdp.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.33 --maxSuccessors 2 --iterSample 10000",

                "meanPayoff -m data/ctmdpModels/toy.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.11 --maxSuccessors 2 --iterSample 10000"]

runConfigs = ctmdpConfigs
