import argparse
import os
import inputOptions
import modelConfigurations
from ParallelRange import get_thread_allocations
from multiprocessing import Pool


def find_curr_max_dir():
    files = os.listdir(resultDir)
    max_file = 0
    for file in files:
        if file.split(".")[0].isnumeric():
            max_file = max(int(file), max_file)

    return max_file


def run_benchmark(index):
    global runConfigs
    global resultDir
    global baseVal
    global gradle_exec

    rel_output_path = os.path.join(resultDir, str(index+1+baseVal))
    absolute_path = os.path.abspath(rel_output_path)
    run_config = runConfigs[index] + " --outputPath " + absolute_path
    cmd_line = gradle_exec + " --args='" + run_config + "'"
    print(cmd_line)
    os.system(cmd_line)


def run_benchmarks_in_range(parallel_range):
    for index in range(parallel_range.start, parallel_range.end + 1):
        run_benchmark(index)


def run_benchmarks_sequentially():
    global runConfigs

    for index in range(len(runConfigs)):
        run_benchmark(index)


def run_benchmarks():
    global input_values
    global runConfigs

    if input_values.number_of_threads == 1:
        run_benchmarks_sequentially()
        return

    parallel_ranges = get_thread_allocations(input_values.number_of_threads, len(runConfigs))
    pool = Pool(processes=input_values.number_of_threads)
    pool.map(run_benchmarks_in_range, parallel_ranges)
    pool.close()
    pool.join()


parser = argparse.ArgumentParser()
inputOptions.add_basic_input_options(parser)
input_values = inputOptions.parse_user_input(parser.parse_args())

# Benchmark type
runConfigs = None
if input_values.is_ctmdp:
    runConfigs = modelConfigurations.ctmdpConfigs
else:
    runConfigs = modelConfigurations.mdpConfigs

# Information level
for i in range(len(runConfigs)):
    runConfigs[i] += " --informationLevel " + input_values.information_level

# Update method
if input_values.update_method == inputOptions.update_method_both:
    for i in range(len(runConfigs)):
        runConfigs.append(runConfigs[i] + " --updateMethod " + inputOptions.black_box_value)
elif input_values.update_method:
    for i in range(len(runConfigs)):
        runConfigs[i] += " --updateMethod " + input_values.update_method


# get error probability
if input_values.get_error_probability:
    for i in range(len(runConfigs)):
        runConfigs[i] += " --getErrorProbability"


# # Simulate Mec
# if input_values.simulate_mec:
#     for i in range(len(runConfigs)):
#         runConfigs[i] += " --simulateMec " + input_values.simulate_mec

# add --qp for running in qp mode and adding a timeout for the qp execution (temporary)
for i in range(len(runConfigs)):
    runConfigs[i] += "--qp"



resultDir = input_values.output_directory + '/'
if not os.path.exists(resultDir):
    os.makedirs(resultDir, exist_ok=True)

dir_path = os.path.dirname(os.path.realpath(__file__))
gradle_exec = dir_path + "/../gradlew -p " + dir_path + "/../ run"
baseVal = find_curr_max_dir()

run_benchmarks()
