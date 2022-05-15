# Create directories if not present
mkdir -p ./blackbox_results
mkdir -p ./greybox_results

# Blackbox with update method blackbox
./gradlew -p ./ run --args='meanPayoff -m data/models/zeroconf_rewards.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.0002 --maxSuccessors 6 --iterSample 10000 --const N=40,K=10,reset=false --rewardModule reach --informationLevel BLACKBOX --updateMethod BLACKBOX --outputPath ./blackbox_results/1'

# Blackbox with udpate method greybox
./gradlew -p ./ run --args='meanPayoff -m data/models/zeroconf_rewards.prism --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.0002 --maxSuccessors 6 --iterSample 10000 --const N=40,K=10,reset=false --rewardModule reach --informationLevel BLACKBOX --updateMethod GREYBOX --outputPath ./greybox_results/1'

# plot graph
python3 ./experimentScripts/plotGraphs.py --blackboxResultDir "./blackbox_results/" --greyboxResultDir "./greybox_results/" --resultDir "./individual_results/"
