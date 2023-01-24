from maxRewards import get_max_reward
import os

class ModelResult:
    def __init__(self, model_name, times, lower_bounds, upper_bounds, num_explored_states, missing_probability, iteration_number, update_MEC_time, update_MEC_visits):
        self.model_name = model_name
        self.times = times
        self.lower_bounds = lower_bounds
        self.upper_bounds = upper_bounds
        self.num_explored_states = num_explored_states
        self.missing_probability = missing_probability
        self.iteration_number = iteration_number
        self.update_MEC_time = update_MEC_time
        self.update_MEC_visits = update_MEC_visits

    def get_update_mec_time(self):
        return self.update_MEC_time/1000

    def get_update_mec_visits(self):
                return self.update_MEC_visits

    def get_runtime(self):
        time_taken_millis = self.times[-1] - self.times[0]
        return time_taken_millis/1000

    def get_bounds(self):
        return self.lower_bounds[-1], self.upper_bounds[-1]

    def get_bounds_diff(self):
        return self.upper_bounds[-1] - self.lower_bounds[-1]


def parse_output_file(file_name, iteration_number=0):
    file_stream = open(file_name)
    content = file_stream.readlines()
    file_stream.close()

    options = content[0].split(" -")
    model_name = options[0].split("/")[-1].split(".prism")[0]
    content = content[1:]
    times = list(map(float, content[0].split()))
    lower_bound = list(map(float, content[1].split()))
    scaled_lower_bounds = [x/get_max_reward(model_name) for x in lower_bound]
    upper_bound = list(map(float, content[2].split()))
    scaled_upper_bounds = [x/get_max_reward(model_name) for x in upper_bound]
    update_MEC_time = float(content[3])
    update_MEC_visits = int(content[4])
    explored_states = int(content[5])

    missing_probability = None
    if len(content) > 3:
        missing_probability = float(content[3])

    return ModelResult(model_name, times, scaled_lower_bounds, scaled_upper_bounds, explored_states, missing_probability, iteration_number, update_MEC_time, update_MEC_visits)


def accumulate_results(resultDirectory):
    # Now store all the information in variables
    benchmark_info = {}
    num_iterations = 0
    for directory in os.listdir(resultDirectory):
        if not os.path.isdir(os.path.join(resultDirectory, directory)):
            continue

        files = os.listdir(os.path.join(resultDirectory, directory))

        for file in files:
            if not file.split(".")[0].isnumeric():
                continue

            file_path = os.path.join(resultDirectory, directory, file)
            model_result = parse_output_file(file_path, num_iterations)

            if model_result.model_name not in benchmark_info:
                benchmark_info[model_result.model_name] = []

            benchmark_info[model_result.model_name].append(model_result)

        num_iterations = num_iterations + 1

    return benchmark_info


def get_average_values(model_result_list):
    average_lower_bound = 0
    average_upper_bound = 0
    average_run_time = 0
    average_states_explored = 0
    average_updateMEC = 0
    average_updateMEC_visits = 0
    for model_result in model_result_list:
        average_lower_bound += model_result.lower_bounds[-1]
        average_upper_bound += model_result.upper_bounds[-1]
        average_run_time += model_result.get_runtime()
        average_states_explored += model_result.num_explored_states
        average_updateMEC += model_result.get_update_mec_time()
        average_updateMEC_visits += model_result.get_update_mec_visits()


    average_lower_bound /= len(model_result_list)
    average_upper_bound /= len(model_result_list)
    average_run_time /= len(model_result_list)
    average_states_explored /= len(model_result_list)
    average_updateMEC /= len(model_result_list)
    average_updateMEC_visits /= len(model_result_list)


    return average_lower_bound, average_upper_bound, average_run_time, average_states_explored, average_updateMEC, average_updateMEC_visits
