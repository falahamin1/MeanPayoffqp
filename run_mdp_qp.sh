nExperiments=3
nThreads=3

while getopts n:t: flag; do
  case "${flag}" in
  n) nExperiments=${OPTARG} ;;
  t) nThreads=${OPTARG} ;;
  *) exit 1 ;;
  esac
done

# Run MDP blackbox with update method blackbox
python3 ./experimentScripts/runNExperiments_qp.py --informationLevel BLACKBOX --updateMethod BLACKBOX --nExperiments "${nExperiments}" --nThreads "${nThreads}" --outputDirectory "./mdp_blackbox_results_qp"

# Run MDP blackbox with update method greybox
python3 ./experimentScripts/runNExperiments_qp.py --informationLevel BLACKBOX --updateMethod GREYBOX --nExperiments "${nExperiments}" --nThreads "${nThreads}" --outputDirectory "./mdp_greybox_results_qp"

# Run plot graphs
python3 ./experimentScripts/plotGraphs.py --blackboxResultDir "./mdp_blackbox_results_qp/iteration0" --greyboxResultDir "./mdp_greybox_results_qp/iteration0" --resultDir "./results"

# Run table generator
python3 ./experimentScripts/tablegenerator.py --blackboxResultDir "./mdp_blackbox_results_qp" --greyboxResultDir "./mdp_greybox_results_qp" --resultDir "./results"

