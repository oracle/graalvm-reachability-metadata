# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class WorkQueueSchedulerTests(unittest.TestCase):
    def test_default_review_queue_configs_include_benchmark_reviews(self) -> None:
        env = {
            "FORGE_REVIEW_LIMIT": "2",
            "FORGE_LIBRARY_REVIEW_LIMIT": "0",
            "FORGE_BENCHMARK_REVIEW_LIMIT": "4",
        }

        with patch.dict(os.environ, env, clear=True):
            configs = queue_config.get_review_queue_configs_from_environment()

        self.assertEqual(
            [(config.label, config.limit) for config in configs],
            [
                (dispatcher_config.LABEL_LIBRARY_NEW, 0),
                (config.LABEL_PR_JAVAC_FIX, 2),
                (config.LABEL_PR_JAVA_RUN_FIX, 2),
                (config.LABEL_PR_NI_RUN_FIX, 2),
                (config.LABEL_PR_LIBRARY_UPDATE, 2),
                (config.LABEL_PR_CODE_COVERAGE, 4),
            ],
        )

    def test_review_label_environment_overrides_default_review_queues(self) -> None:
        env = {
            "FORGE_REVIEW_LABEL": config.LABEL_PR_CODE_COVERAGE,
            "FORGE_REVIEW_LIMIT": "3",
            "FORGE_BENCHMARK_REVIEW_LIMIT": "0",
        }

        with patch.dict(os.environ, env, clear=True):
            configs = queue_config.get_review_queue_configs_from_environment()

        self.assertEqual(
            [(config.label, config.limit) for config in configs],
            [
                (config.LABEL_PR_CODE_COVERAGE, 3),
            ],
        )

class IssueProcessingImportTests(unittest.TestCase):
    def test_fresh_interpreter_resolves_thread_pool_executor(self) -> None:
        # A bare `import concurrent` leaves `concurrent.futures` unloaded unless another
        # module happens to import it first; the queue loop must not depend on that.
        probe = (
            "import dispatcher.issue_processing as processing; "
            "print(processing.concurrent.futures.ThreadPoolExecutor.__name__)"
        )
        result = subprocess.run(
            [sys.executable, "-c", probe],
            cwd=os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), "ThreadPoolExecutor")
