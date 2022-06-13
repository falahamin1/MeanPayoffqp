from maxRewards import get_max_reward


class QPModelResult:
    def __init__(self, model_name, times, lower_bounds, upper_bounds, qp_result, num_explored_states, missing_probability, iteration_number):
        self.model_name = model_name
        self.times = times
        self.lower_bounds = lower_bounds
        self.upper_bounds = upper_bounds
        self.qp_result = qp_result
        self.num_explored_states = num_explored_states
        self.missing_probability = missing_probability
        self.iteration_number = iteration_number

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
    qp_result = list(map(float, content[3].split()))
    scaled_qp_result = [x/get_max_reward(model_name) for x in qp_result]
    explored_states = int(content[3])

    missing_probability = None
    if len(content) > 3:
        missing_probability = float(content[3])

    return QPModelResult(model_name, times, scaled_lower_bounds, scaled_upper_bounds, scaled_qp_result, explored_states, missing_probability, iteration_number)