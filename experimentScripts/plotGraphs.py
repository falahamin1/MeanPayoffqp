import os
import numpy as np
import matplotlib.pyplot as plt
import modelNames
import trueValues
import benchmarksUtil
import maxRewards

resultDir = os.path.abspath("./experimentScripts/experimentResults/")
plotsDir = os.path.join(resultDir, "plots")

blackbox_result_dir = os.path.abspath("./experimentScripts/experimentResults/BBHP/iteration7/")
greybox_result_dir = os.path.abspath("./experimentScripts/experimentResults/BGHP/iteration7/")
modelResults = {model: [None, None] for model in modelNames.model_names}

print(blackbox_result_dir)
print(greybox_result_dir)


def store_model_result(file_path, index):
    model_result = benchmarksUtil.parse_output_file(file_path)
    modelResults[model_result.model_name][index] = model_result


def parse_results_in_dir(result_dir, index):
    for file in os.listdir(result_dir):
        if not file.split(".")[0].isnumeric():
            continue
        store_model_result(os.path.join(result_dir, file), index)


def parse_blackbox_results():
    parse_results_in_dir(blackbox_result_dir, 0)


def parse_greybox_results():
    parse_results_in_dir(greybox_result_dir, 1)


parse_blackbox_results()
parse_greybox_results()
plt.rcParams.update({'font.size': 14})
# for file in os.listdir(resultDir):
#     if not file.split(".")[0].isnumeric():
#         continue
#     content = open(os.path.join(resultDir, file)).readlines()
#     grey = True
#     options = content[0].split(" -")
#     model = options[0].split("/")[-1].split(".prism")[0]
#     for option in options:
#         if "-updateMethod" in option:
#             if "GREY" in option:
#                 grey = True
#             elif "BLACK" in option:
#                 grey = False
#
#     modelResults[model][0] = content[1:]
    # if grey:
    #     modelResults[model][0] = content[1:]
    # else:
    #     modelResults[model][1] = content[1:]

for model in modelResults:
    blackResult = modelResults[model][0]
    if blackResult is None:
        continue
    true_model_value = trueValues.get_true_value(model) / maxRewards.get_max_reward(model)
    times = (np.array(blackResult.times))
    times -= times.min()
    lowerBounds = blackResult.lower_bounds
    scaled_lower_bound = np.array([x/maxRewards.get_max_reward(model) for x in lowerBounds])
    upperBounds = blackResult.upper_bounds
    scaled_upper_bound = np.array([x/maxRewards.get_max_reward(model) for x in upperBounds])

    plt.plot(times/60000.0, scaled_lower_bound, label="Lower Bounds (B): "+str(np.around(scaled_lower_bound[-1], 4)))
    plt.plot(times/60000.0, scaled_upper_bound, label="Upper Bounds (B): "+str(np.around(scaled_upper_bound[-1], 4)))
    plt.plot(times/60000.0, [true_model_value]*len(times), label="True Value: "+str(round(true_model_value, 4)), linestyle="dotted")
    lasttime = times[-1]

    greyResult = modelResults[model][1]
    times = (np.array(greyResult.times))
    times -= times.min()
# 	times = np.append(times, lasttime)
    lowerBounds = greyResult.lower_bounds
    scaled_lower_bound = np.array([x/maxRewards.get_max_reward(model) for x in lowerBounds])
# 	lowerBounds = np.append(lowerBounds, lowerBounds[-1])
    upperBounds = greyResult.upper_bounds
    scaled_upper_bound = np.array([x/maxRewards.get_max_reward(model) for x in upperBounds])
# 	upperBounds = np.append(upperBounds, upperBounds[-1])

    plt.plot(times/60000.0, scaled_lower_bound, label="Lower Bounds (G): "+str(np.around(scaled_lower_bound[-1], 4)))
    plt.plot(times/60000.0, scaled_upper_bound, label="Upper Bounds (G): "+str(np.around(scaled_upper_bound[-1], 4)))

    plt.legend()

    plt.xlabel("times (minutes)")
    plt.ylabel("mean payoff")
    model = model.replace(".", "-")
    print(os.path.join(plotsDir, model))
    plt.savefig(os.path.join(plotsDir, model))
    plt.close()
