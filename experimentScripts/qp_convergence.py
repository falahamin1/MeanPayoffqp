import os
import numpy as np
import matplotlib.pyplot as plt
import modelNames
import trueValues
import qpBenchmarksUtil as benchmarksUtil
import maxRewards

# import os
# print("PYTHONPATH:", os.environ['PYTHONPATH'].split(os.pathsep))
# print("PATH:", os.environ.get('PATH'))
# exit()

modelResults = {model: [None, None] for model in modelNames.model_names}

resultDir = "/"
plotsDir = os.path.join(resultDir, "plots")

print(resultDir)
print(plotsDir)

# Create results dir if not present
os.makedirs(plotsDir, exist_ok=True)


def store_model_result(file_path):
    model_result = benchmarksUtil.parse_output_file(file_path)
    modelResults[model_result.model_name][0] = model_result


store_model_result(os.path.abspath("../temp.txt"))

for model in modelResults:
    blackboxResult = modelResults[model][0]
    if blackboxResult is None:
        continue

    true_model_value = trueValues.get_true_value(model) / maxRewards.get_max_reward(model)
    times = (np.array(blackboxResult.times))
    times -= times.min()
    lowerBounds = blackboxResult.lower_bounds
    upperBounds = blackboxResult.upper_bounds

    plt.plot(times/60000.0, lowerBounds, label="Lower Bounds (B): "+str(np.around(lowerBounds[-1], 4)))
    plt.plot(times/60000.0, upperBounds, label="Upper Bounds (B): "+str(np.around(upperBounds[-1], 4)))
    plt.plot(times/60000.0, [true_model_value]*len(times), label="True Value: "+str(round(true_model_value, 4)), linestyle="dotted")
    lasttime = times[-1]

    qp_result = blackboxResult.qp_result
    plt.plot(times/60000.0, qp_result, label="Quadratic Program (QP): "+str(np.around(lowerBounds[-1], 4)))
    # greyResult = modelResults[model][1]
    # times = (np.array(greyResult.times))
    # times -= times.min()
    # lowerBounds = greyResult.lower_bounds
    # upperBounds = greyResult.upper_bounds
    #
    # plt.plot(times/60000.0, lowerBounds, label="Lower Bounds (G): "+str(np.around(lowerBounds[-1], 4)))
    # plt.plot(times/60000.0, upperBounds, label="Upper Bounds (G): "+str(np.around(upperBounds[-1], 4)))

    plt.legend()

    plt.xlabel("times (minutes)")
    plt.ylabel("mean payoff")
    model = model.replace(".", "-")
    plt.title(model)
    print(os.path.join(plotsDir, model))
    plt.savefig(os.path.join(plotsDir, model))
    plt.close()
