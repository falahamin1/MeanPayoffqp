import os
import shutil
from multiprocessing import Pool
import benchmarksUtil
import inputOptions
import argparse
from ParallelRange import get_thread_allocations


def get_exec_command_from_input():
    global input_values
    dir_path = os.path.dirname(os.path.realpath(__file__))
    command = 'python3 ' + dir_path + '/runExperiments.py'
    if input_values.information_level:
        command += ' ' + inputOptions.information_level_option + ' ' + input_values.information_level
    if input_values.update_method:
        command += ' ' + inputOptions.update_method_option + ' ' + input_values.update_method
    if input_values.simulate_mec:
        command += ' ' + inputOptions.simulate_mec_option + ' ' + input_values.simulate_mec
    if input_values.get_error_probability:
        command += ' ' + inputOptions.get_error_probability_option
    if input_values.delta_t_method:
        command += ' ' + inputOptions.deltat_method_option + ' ' + input_values.delta_t_method
    if input_values.is_ctmdp:
        command += ' ' + inputOptions.ctmdp_benchmarks_option
    if input_values.lower_bound:
       command += ' ' + inputOptions.lower_bound_option+ ' ' + input_values.lower_bound
    if input_values.upper_bound:
           command += ' ' + inputOptions.upper_bound_option+ ' ' + input_values.upper_bound
    return command


def run_benchmark_iteration(i):
    global input_values
    global exec_command
    global number_of_threads_in_exec

    output_directory_option = inputOptions.output_directory_option + ' ' + input_values.output_directory + '/' + f'iteration{i}'
    n_threads_option = inputOptions.number_of_threads_option + ' ' + str(number_of_threads_in_exec)
    os.system(exec_command + ' ' + output_directory_option + ' ' + n_threads_option)


def run_benchmarks(n):
    for i in range(n):
        run_benchmark_iteration(i)


# For running iterations in parallel
def run_benchmarks_range(parallel_range):
    for i in range(parallel_range.start, parallel_range.end + 1):
        run_benchmark_iteration(i)


def run_benchmarks_in_parallel(n):
    global input_values
    parallel_ranges = get_thread_allocations(input_values.number_of_threads, n)

    pool = Pool(processes=input_values.number_of_threads)
    pool.map(run_benchmarks_range, parallel_ranges)
    pool.close()
    pool.join()


def schedule_and_run_benchmarks(n):
    global input_values
    global number_of_threads_in_exec

    if input_values.number_of_threads == 1 or n == 1:
        run_benchmarks(n)
        return

    number_of_threads_in_exec = 1
    run_benchmarks_in_parallel(n)


def result_comparator(model_result):
    return model_result.get_bounds_diff()


def write_model_result(result_file, model_result):
    result_file.write('Execution time: ' + str(model_result.get_runtime()) + '\n')
    result_file.write('Lower bound: ' + str(model_result.lower_bounds[-1]) + '\n')
    result_file.write('Upper bound: ' + str(model_result.upper_bounds[-1]) + '\n')
    result_file.write('Iteration number: ' + str(model_result.iteration_number) + '\n')
    result_file.write('Num states explored: ' + str(model_result.num_explored_states) + '\n')
    result_file.write('Time spent in updateMEC: ' + str(model_result.update_MEC_time) + '\n')
    result_file.write('Number of visits in updateMEC: ' + str(model_result.update_MEC_visits) + '\n')
    result_file.write('\n')
    result_file.write('\n')
    result_file.write('\n')


def write_model_results(model_name, model_result_list, result_directory):
    print(model_name)
    result_file_name = result_directory + model_name + '.txt'

    with open(result_file_name, 'w') as result_file:
        for model_result in model_result_list:
            write_model_result(result_file, model_result)

        (al, au, ar, av_states, updateMEC, updateMEC_visits) = benchmarksUtil.get_average_values(model_result_list)
        precision = au - al
        result_file.write('Average Lower Bound: ' + str(al) + '\n')
        result_file.write('Average Upper Bound: ' + str(au) + '\n')
        result_file.write('Average Run time: ' + str(ar) + '\n')
        result_file.write('Precision: ' + str(precision) + '\n')
        result_file.write('Average number of states explored: ' + str(av_states) + '\n')
        result_file.write('Average time spent in updateMEC: ' + str(updateMEC) + '\n')
        result_file.write('Average visits to updateMEC: ' + str(updateMEC_visits) + '\n')


def write_results(results, result_directory):
    for model_name, model_result_list in results.items():
        model_result_list.sort(key=result_comparator)
        write_model_results(model_name, model_result_list, result_directory)


def remove_old_results():
    is_dir_exists = os.path.isdir(resultDirectory)
    if is_dir_exists:
        shutil.rmtree(resultDirectory)


# Handling input
parser = argparse.ArgumentParser()
inputOptions.add_basic_input_options(parser)
inputOptions.add_n_experiments_option(parser)
arguments = parser.parse_args()
input_values = inputOptions.parse_user_input(arguments)
number_of_experiments = arguments.nExperiments


resultDirectory = input_values.output_directory + '/'
remove_old_results()
exec_command = get_exec_command_from_input()
number_of_threads_in_exec = input_values.number_of_threads
schedule_and_run_benchmarks(number_of_experiments)
benchmarkInfo = benchmarksUtil.accumulate_results(resultDirectory)
write_results(benchmarkInfo, resultDirectory)
print("writing results in " + resultDirectory)