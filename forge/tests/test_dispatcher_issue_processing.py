# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class WorkQueueSchedulerTests(unittest.TestCase):
    def test_work_queue_configs_allow_zero_limits_from_environment(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "2",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "3",
            "FORGE_STRATEGY_NAME": "custom-strategy",
            "FORGE_WORK_LABEL": forge_metadata.LABEL_LIBRARY_NEW,
        }

        with patch.dict(os.environ, env, clear=True):
            configs = forge_metadata.get_work_queue_configs_from_environment()

        self.assertEqual(
            [(config.label, config.limit, config.strategy_name, config.random_offset) for config in configs],
            [
                (forge_metadata.LABEL_JAVAC_FAIL, 0, None, False),
                (forge_metadata.LABEL_JAVA_RUN_FAIL, 2, None, False),
                (forge_metadata.LABEL_NI_RUN_FAIL, 0, None, False),
                (forge_metadata.LABEL_LIBRARY_UPDATE, 0, None, False),
                (forge_metadata.LABEL_LIBRARY_NEW, 3, "custom-strategy", False),
            ],
        )

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
                (forge_metadata.LABEL_LIBRARY_NEW, 0),
                (config.LABEL_PR_JAVAC_FIX, 2),
                (config.LABEL_PR_JAVA_RUN_FIX, 2),
                (config.LABEL_PR_NI_RUN_FIX, 2),
                (config.LABEL_PR_LIBRARY_UPDATE, 2),
                (config.LABEL_PR_CODE_COVERAGE, 4),
            ],
        )

    def test_random_work_offset_can_be_disabled_from_environment(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "1",
            "FORGE_RANDOM_WORK_OFFSET": "0",
        }

        with patch.dict(os.environ, env, clear=True):
            configs = forge_metadata.get_work_queue_configs_from_environment()

        self.assertFalse(configs[-1].random_offset)

    def test_random_work_offset_can_be_enabled_from_environment(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "1",
            "FORGE_RANDOM_WORK_OFFSET": "1",
        }

        with patch.dict(os.environ, env, clear=True):
            configs = forge_metadata.get_work_queue_configs_from_environment()

        self.assertTrue(configs[-1].random_offset)

    def test_random_work_offset_can_be_disabled_from_cli_override(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "1",
            "FORGE_RANDOM_WORK_OFFSET": "1",
        }

        with patch.dict(os.environ, env, clear=True):
            configs = forge_metadata.get_work_queue_configs_from_environment(
                random_offset_override=False,
            )

        self.assertFalse(configs[-1].random_offset)

    def test_run_work_queues_accepts_random_offset_flags(self) -> None:
        random_args = forge_metadata.parse_args(["--run-work-queues", "--random-offset"])
        no_random_args = forge_metadata.parse_args(["--run-work-queues", "--no-random-offset"])

        self.assertTrue(random_args.random_offset)
        self.assertFalse(no_random_args.random_offset)

    def test_take_blocked_issues_is_disabled_by_default(self) -> None:
        default_args = forge_metadata.parse_args(["--label", forge_metadata.LABEL_LIBRARY_NEW])
        override_args = forge_metadata.parse_args([
            "--label",
            forge_metadata.LABEL_LIBRARY_NEW,
            "--take-blocked-issues",
        ])

        self.assertFalse(default_args.take_blocked_issues)
        self.assertTrue(override_args.take_blocked_issues)

    def test_verbose_output_is_disabled_by_default(self) -> None:
        default_args = forge_metadata.parse_args(["--label", forge_metadata.LABEL_LIBRARY_NEW])
        verbose_args = forge_metadata.parse_args([
            "--label",
            forge_metadata.LABEL_LIBRARY_NEW,
            "--verbose",
        ])

        self.assertFalse(default_args.verbose)
        self.assertTrue(verbose_args.verbose)

    def test_issue_queue_modes_accept_priority_tiers(self) -> None:
        for priority in forge_metadata.PRIORITY_CHOICES:
            with self.subTest(priority=priority):
                work_queue_args = forge_metadata.parse_args([
                    "--run-work-queues",
                    "--priority",
                    priority,
                ])
                label_args = forge_metadata.parse_args([
                    "--label",
                    forge_metadata.LABEL_LIBRARY_NEW,
                    "--priority",
                    priority,
                ])

                self.assertEqual(work_queue_args.priority, priority)
                self.assertEqual(label_args.priority, priority)

    def test_issue_queue_modes_accept_user_requested_only_flag(self) -> None:
        work_queue_args = forge_metadata.parse_args(["--run-work-queues", "--user-requested-only"])
        label_args = forge_metadata.parse_args([
            "--label",
            forge_metadata.LABEL_LIBRARY_NEW,
            "--user-requested-only",
        ])

        self.assertTrue(work_queue_args.user_requested_only)
        self.assertTrue(label_args.user_requested_only)

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

    def test_process_work_queues_skips_zero_limit_queues(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "1",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "0",
            "FORGE_REVIEW_LIMIT": "0",
            "FORGE_JAVA_RUN_STRATEGY_NAME": "java-run-strategy",
        }

        with patch.dict(os.environ, env, clear=True), \
                patch.object(queue_config, "require_strategy_by_name") as require_strategy_by_name, \
                patch.object(issue_processing, "validate_issue_processing_environment") as validate_environment, \
                patch.object(issue_processing, "process_issues_with_label", return_value=0) as process_issues, \
                patch.object(issue_processing, "process_pull_requests_with_label") as process_reviews:
            forge_metadata.process_work_queues(
                "/tmp/reachability",
                "/tmp/metrics",
                "automation-user",
            )

        require_strategy_by_name.assert_called_once_with("java-run-strategy")
        validate_environment.assert_called_once()
        process_issues.assert_called_once_with(
            forge_metadata.LABEL_JAVA_RUN_FAIL,
            1,
            0,
            "/tmp/reachability",
            "/tmp/metrics",
            "java-run-strategy",
            False,
            "automation-user",
            forge_metadata.DEFAULT_PARALLELISM,
            user_requested_only=False,
            environment_already_validated=True,
        )
        process_reviews.assert_not_called()

    def test_process_work_queues_uses_random_offset_for_new_library_queue(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "1",
            "FORGE_REVIEW_LIMIT": "0",
            "FORGE_RANDOM_WORK_OFFSET": "1",
            "FORGE_STRATEGY_NAME": "custom-strategy",
            "FORGE_WORK_LABEL": forge_metadata.LABEL_LIBRARY_NEW,
        }

        with patch.dict(os.environ, env, clear=True), \
                patch.object(queue_config, "require_strategy_by_name"), \
                patch.object(issue_processing, "validate_issue_processing_environment"), \
                patch.object(
                    issue_processing, "resolve_random_issue_scan_offset", return_value=42
                ) as random_offset, \
                patch.object(issue_processing, "process_issues_with_label", return_value=0) as process_issues, \
                patch.object(issue_processing, "process_pull_requests_with_label") as process_reviews:
            forge_metadata.process_work_queues(
                "/tmp/reachability",
                "/tmp/metrics",
                "automation-user",
            )

        random_offset.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            user_requested_only=False,
        )
        process_issues.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            1,
            42,
            "/tmp/reachability",
            "/tmp/metrics",
            "custom-strategy",
            False,
            "automation-user",
            forge_metadata.DEFAULT_PARALLELISM,
            user_requested_only=False,
            environment_already_validated=True,
        )
        process_reviews.assert_not_called()

    def test_process_work_queues_passes_user_requested_only_to_issue_scans(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "1",
            "FORGE_REVIEW_LIMIT": "0",
            "FORGE_RANDOM_WORK_OFFSET": "1",
            "FORGE_USER_REQUESTED_ISSUES_ONLY": "1",
        }

        with patch.dict(os.environ, env, clear=True), \
                patch.object(issue_processing, "validate_issue_processing_environment"), \
                patch.object(
                    issue_processing, "resolve_random_issue_scan_offset", return_value=42
                ) as random_offset, \
                patch.object(issue_processing, "process_issues_with_label", return_value=0) as process_issues:
            forge_metadata.process_work_queues(
                "/tmp/reachability",
                "/tmp/metrics",
                "automation-user",
            )

        random_offset.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            user_requested_only=True,
        )
        process_issues.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            1,
            42,
            "/tmp/reachability",
            "/tmp/metrics",
            config.DEFAULT_WORK_QUEUE_STRATEGY_NAME,
            False,
            "automation-user",
            forge_metadata.DEFAULT_PARALLELISM,
            user_requested_only=True,
            environment_already_validated=True,
        )

    def test_process_work_queues_forwards_take_blocked_issues_override(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "1",
            "FORGE_REVIEW_LIMIT": "0",
            "FORGE_WORK_LABEL": forge_metadata.LABEL_LIBRARY_NEW,
        }

        with patch.dict(os.environ, env, clear=True), \
                patch.object(issue_processing, "validate_issue_processing_environment"), \
                patch.object(issue_processing, "process_issues_with_label", return_value=0) as process_issues:
            forge_metadata.process_work_queues(
                "/tmp/reachability",
                "/tmp/metrics",
                "automation-user",
                take_blocked_issues=True,
            )

        process_issues.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            1,
            0,
            "/tmp/reachability",
            "/tmp/metrics",
            config.DEFAULT_WORK_QUEUE_STRATEGY_NAME,
            False,
            "automation-user",
            forge_metadata.DEFAULT_PARALLELISM,
            user_requested_only=False,
            environment_already_validated=True,
            take_blocked_issues=True,
        )

    def test_process_work_queues_resolves_auth_for_review_only_queue(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "0",
            "FORGE_JAVA_RUN_WORK_LIMIT": "0",
            "FORGE_NI_RUN_WORK_LIMIT": "0",
            "FORGE_LIBRARY_UPDATE_WORK_LIMIT": "0",
            "FORGE_WORK_LIMIT": "0",
            "FORGE_REVIEW_LIMIT": "1",
            "FORGE_REVIEW_LABEL": forge_metadata.LABEL_LIBRARY_NEW,
        }

        with patch.dict(os.environ, env, clear=True), \
                patch.object(
                    issue_processing,
                    "resolve_authenticated_user",
                    return_value="automation-user",
                ) as resolve_authenticated_user, \
                patch.object(issue_processing, "process_pull_requests_with_label") as process_reviews:
            forge_metadata.process_work_queues(
                "/tmp/reachability",
                "/tmp/metrics",
                None,
            )

        resolve_authenticated_user.assert_called_once_with(None)
        process_reviews.assert_called_once_with(
            forge_metadata.LABEL_LIBRARY_NEW,
            1,
            "/tmp/reachability",
            "automation-user",
        )

    def test_process_work_queues_skips_remaining_work_when_shutdown_requested(self) -> None:
        env = {
            "FORGE_JAVAC_WORK_LIMIT": "1",
            "FORGE_REVIEW_LIMIT": "1",
            "FORGE_REVIEW_LABEL": forge_metadata.LABEL_LIBRARY_NEW,
        }

        with patch.dict(os.environ, env, clear=True), \
                patch.object(issue_processing, "is_shutdown_requested", return_value=True), \
                patch.object(issue_processing, "validate_issue_processing_environment") as validate_environment, \
                patch.object(issue_processing, "process_issues_with_label") as process_issues, \
                patch.object(issue_processing, "process_pull_requests_with_label") as process_reviews:
            forge_metadata.process_work_queues(
                "/tmp/reachability",
                "/tmp/metrics",
                "automation-user",
            )

        validate_environment.assert_not_called()
        process_issues.assert_not_called()
        process_reviews.assert_not_called()


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
