# This script runs experiments for InformationLevel GREYBOX, UpdateMethod GREYBOX for different variants of
# simulateMec options. Each variant is run in a new process.

# The output directory for each variant is named after the first letter of the option provided for InformationLevel,
# UpdateMethod and SimulateMec option. For example, for the following configuration
# informationLevel GREYBOX, updateMethod GREYBOX, simulateMec STANDARD
# result will be stored in the directory 'GGS', where first 'G' corresponds to informationLevel, second 'G' corresponds
# to updateMethod and the third 'S' corresponds to simulateMec value.

import os

import inputOptions

base_dir = 'experimentResults'
base_command = 'python3 runNExperiments.py --informationLevel GREYBOX --updateMethod GREYBOX'


class Configuration:
    def __init__(self, simulate_mec_config, delta_t_config, output_directory_config):
        self.simulate_mec_config = simulate_mec_config
        self.delta_t_config = delta_t_config
        self.output_directory_config = output_directory_config


def run_configuration(configuration):
    simulate_mec_param = inputOptions.simulate_mec_option + ' ' + configuration.simulate_mec_config
    deltat_method_param = inputOptions.deltat_method_option + ' ' + configuration.delta_t_config
    output_directory_param = inputOptions.output_directory_option + ' ' + configuration.output_directory_config
    os.system(base_command + ' ' + simulate_mec_param + ' ' + deltat_method_param + ' ' + output_directory_param)


def configuration_1():
    output_directory = base_dir + '/' + 'GGSP'
    return Configuration(inputOptions.simulate_mec_standard, inputOptions.deltat_method_p_min, output_directory)


def configuration_2():
    output_directory = base_dir + '/' + 'GGHP'
    return Configuration(inputOptions.simulate_mec_heuristic, inputOptions.deltat_method_p_min, output_directory)


def configuration_3():
    output_directory = base_dir + '/' + 'GGCP'
    return Configuration(inputOptions.simulate_mec_cheat, inputOptions.deltat_method_p_min, output_directory)


def configuration_4():
    output_directory = base_dir + '/' + 'GGSM'
    return Configuration(inputOptions.simulate_mec_standard, inputOptions.deltat_method_max_successors, output_directory)


def configuration_5():
    output_directory = base_dir + '/' + 'GGHM'
    return Configuration(inputOptions.simulate_mec_heuristic, inputOptions.deltat_method_max_successors, output_directory)


def configuration_6():
    output_directory = base_dir + '/' + 'GGCM'
    return Configuration(inputOptions.simulate_mec_cheat, inputOptions.deltat_method_max_successors, output_directory)


run_configuration(configuration_2())
# all_configurations = [configuration_1(), configuration_2(), configuration_3(), configuration_4(), configuration_5(), configuration_6()]
#
#
# pool = Pool(processes=3)
# pool.map(run_configuration, all_configurations)
# pool.close()
# pool.join()
