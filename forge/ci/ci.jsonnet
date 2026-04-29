{
  local common = import "../../ci/ci_common/common.jsonnet",
  local utils = import "../../ci/ci_common/common-utils.libsonnet",

  local schedule_job(name, run_steps) = common.disable_proxies + {

    name: name,

    packages+: {
      python3: "==3.12.3",
      "pip:jsonschema": "==4.6.1",
    },

    setup+: [
      ["cd", "metadata-forge"],
      ['set-export', 'PYTHONPATH', '.'],
    ],

    run+: run_steps,
    targets: ["tier3"],
    timelimit: "30:00",
  },

  local linux_amd64_jdkLatest = common.linux_amd64,

  local builds = [
    (linux_amd64_jdkLatest + schedule_job(
      "gate-metadata-forge-style-linux-amd64",
      [["bash", "-c", "pylint --rcfile .pylintrc `find . -maxdepth 1 -type d`"]],
    )) {
      guard+: {
        includes+: ["metadata-forge/**"],
        excludes+: ["**.md", "**.json"],
      },
    },
    (linux_amd64_jdkLatest + schedule_job(
      "gate-metadata-forge-schema-validation-linux-amd64",
      [
        ["python3", "utility_scripts/schema_validator.py", "strategy", "strategies/predefined_strategies.json"],
        ["python3", "utility_scripts/schema_validator.py", "benchmark_suite", "benchmarks/benchmark_suite.json"],
      ],
    )) + {timelimit: "15:00"} {
      guard+: {
        includes+: ["metadata-forge/utility_scripts/schema_validator.py", "metadata-forge/benchmarks", "metadata-forge/strategies", "metadata-forge/schemas"],
      },
    },
  ],

  builds: utils.add_defined_in(builds, std.thisFile),
}
