# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class SingleIssueProcessingTests(unittest.TestCase):
    def test_append_chunked_dynamic_access_workflow_args_passes_issue_context_for_first_run(self) -> None:
        claimed_issue = _claimed_issue()
        pipeline_argv = ["--coordinates", claimed_issue.issue_coordinates]

        dynamic_access.append_chunked_dynamic_access_workflow_args(pipeline_argv, claimed_issue, 4)

        self.assertEqual(
            pipeline_argv,
            [
                "--coordinates", claimed_issue.issue_coordinates,
                "--issue-number", "1412",
                "--chunk-class-count", "4",
            ],
        )

    def test_append_chunked_dynamic_access_workflow_args_omits_count_when_not_chunked(self) -> None:
        claimed_issue = _claimed_issue()
        pipeline_argv = ["--coordinates", claimed_issue.issue_coordinates]

        dynamic_access.append_chunked_dynamic_access_workflow_args(pipeline_argv, claimed_issue, None)

        self.assertEqual(
            pipeline_argv,
            [
                "--coordinates", claimed_issue.issue_coordinates,
                "--issue-number", "1412",
            ],
        )

    def test_prepare_dynamic_access_chunking_records_threshold_and_applies_label(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            claimed_issue = _claimed_issue()
            claimed_issue = records.ClaimedIssue(
                **{
                    **claimed_issue.__dict__,
                    "worktree_path": tmpdir,
                },
            )
            report = _dynamic_access_report([
                "org.example.A",
                "org.example.B",
                "org.example.C",
                "org.example.D",
                "org.example.E",
                "org.example.F",
            ])

            with patch.object(dynamic_access, "_prepare_new_library_dynamic_access_report"), \
                    patch.object(dynamic_access, "_generate_dispatcher_dynamic_access_report"), \
                    patch.object(dynamic_access, "_load_dispatcher_dynamic_access_report", return_value=report), \
                    patch.object(dynamic_access, "add_issue_label") as add_issue_label, \
                    patch.dict(os.environ, {"FORGE_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD": "5"}, clear=True):
                chunk_count = dynamic_access.prepare_dynamic_access_chunking(
                    claimed_issue,
                    "dynamic_access_main_sources_pi_gpt-5.6-sol",
                )

            report_path = dynamic_access_exhaust_report_path(
                tmpdir,
                claimed_issue.issue_coordinates,
            )
            self.assertEqual(chunk_count, 5)
            self.assertTrue(issue_queue.issue_has_label(claimed_issue.issue, config.LABEL_CHUNKED_DYNAMIC_ACCESS))
            add_issue_label.assert_called_once_with(1412, config.LABEL_CHUNKED_DYNAMIC_ACCESS)
            self.assertTrue(os.path.isfile(report_path))
            report_state = DynamicAccessExhaustReport.load(report_path)
            self.assertEqual(report_state.class_threshold, 5)
            self.assertEqual(report_state.current_chunk_class_count, 5)

    def test_prepare_dynamic_access_chunking_uses_remaining_class_count_for_final_chunk(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            claimed_issue = _claimed_issue()
            claimed_issue.issue["labels"] = [{"name": config.LABEL_CHUNKED_DYNAMIC_ACCESS}]
            claimed_issue = records.ClaimedIssue(
                **{
                    **claimed_issue.__dict__,
                    "worktree_path": tmpdir,
                },
            )
            report_state = DynamicAccessExhaustReport.create(
                coordinate=claimed_issue.issue_coordinates,
                issue_number=1412,
            )
            report_state.mark_completed("org.example.A")
            report_state.mark_completed("org.example.B")
            report_state.mark_completed("org.example.C")
            report_state.mark_completed("org.example.D")
            report_state.save(report_state.default_path(tmpdir))
            report = _dynamic_access_report([
                "org.example.A",
                "org.example.B",
                "org.example.C",
                "org.example.D",
                "org.example.E",
                "org.example.F",
            ])

            with patch.object(dynamic_access, "_prepare_new_library_dynamic_access_report"), \
                    patch.object(dynamic_access, "_generate_dispatcher_dynamic_access_report"), \
                    patch.object(dynamic_access, "_load_dispatcher_dynamic_access_report", return_value=report), \
                    patch.object(dynamic_access, "add_issue_label") as add_issue_label, \
                    patch.dict(os.environ, {"FORGE_DYNAMIC_ACCESS_CHUNK_CLASS_THRESHOLD": "5"}, clear=True):
                chunk_count = dynamic_access.prepare_dynamic_access_chunking(
                    claimed_issue,
                    "dynamic_access_main_sources_pi_gpt-5.6-sol",
                )

            report_state = DynamicAccessExhaustReport.load(report_state.default_path(tmpdir))
            self.assertEqual(chunk_count, 2)
            self.assertEqual(report_state.class_threshold, 5)
            self.assertEqual(report_state.current_chunk_class_count, 2)
            add_issue_label.assert_not_called()

    def test_chunked_dynamic_access_base_check_uses_pr_merge_commit_for_squash_merges(self) -> None:
        report_state = DynamicAccessExhaustReport.create(
            coordinate="org.example:lib:1.0.0",
            issue_number=1412,
        )
        report_state.latest_chunk_commit = "head-commit"
        report_state.latest_chunk_pull_request = 4242

        with patch.object(
                dynamic_access,
                "gh",
                return_value=subprocess.CompletedProcess(
                    ["gh"],
                    0,
                    stdout=json.dumps({"mergeCommit": {"oid": "squash-merge-commit"}}),
                ),
        ), \
                patch.object(
                    subprocess,
                    "run",
                    return_value=subprocess.CompletedProcess(["git"], 0),
                ) as run:
            dynamic_access.verify_chunked_dynamic_access_base_contains_published_commit(
                report_state,
                "/tmp/reachability-worktree",
            )

        run.assert_called_once_with(
            ["git", "merge-base", "--is-ancestor", "squash-merge-commit", "HEAD"],
            cwd="/tmp/reachability-worktree",
            check=False,
        )

    def test_process_single_issue_claims_without_chunk_artifact_override(self) -> None:
        issue = {
            "number": 1412,
            "title": "Add support for org.example:lib:1.0.0",
            "labels": [],
            "assignees": [],
        }

        with patch.object(forge_metadata, "validate_issue_processing_environment"), \
                patch.object(
                    forge_metadata,
                    "get_issue_by_number",
                    return_value=(issue, forge_metadata.LABEL_LIBRARY_NEW),
                ), \
                patch.object(
                    forge_metadata,
                    "claim_issue_for_processing",
                    return_value=_claimed_issue(),
                ) as claim_issue_for_processing, \
                patch.object(
                    forge_metadata,
                    "process_claimed_issue_lifecycle",
                    return_value=True,
                ):
            self.assertTrue(
                forge_metadata.process_single_issue(
                    1412,
                    "/tmp/reachability",
                    "/tmp/metrics",
                    None,
                    False,
                    "automation-user",
                )
            )

        claim_issue_for_processing.assert_called_once_with(
            issue,
            forge_metadata.LABEL_LIBRARY_NEW,
            "/tmp/reachability",
            "/tmp/metrics",
            "automation-user",
        )


class EnvironmentValidationTests(unittest.TestCase):
    def test_issue_processing_requires_dev_and_ci_graalvm_homes(self) -> None:
        with patch.object(env_config, "require_issue_graalvm_homes") as require_graalvm_homes:
            forge_metadata.validate_issue_processing_environment()

        require_graalvm_homes.assert_called_once_with()
        self.assertEqual(
            (
                config.DEV_GRAALVM_ENV_VAR,
                config.POST_GENERATION_GRAALVM_ENV_VAR,
                config.LATEST_EA_GRAALVM_ENV_VAR,
            ),
            host_graalvm_checks.ISSUE_GRAALVM_ENV_VARS,
        )

    def test_review_only_runs_do_not_require_graalvm(self) -> None:
        args = forge_metadata.parse_args(["--review-pr", "library-new-request"])

        requirements = queue_config.resolve_host_requirement_queues(args)

        self.assertFalse(requirements.issue_work)
        self.assertTrue(requirements.review_work)
        self.assertTrue(requirements.github_work)

    def test_issue_runs_require_graalvm_without_review_capabilities(self) -> None:
        args = forge_metadata.parse_args(["--issue-number", "9101"])

        requirements = queue_config.resolve_host_requirement_queues(args)

        self.assertTrue(requirements.issue_work)
        self.assertFalse(requirements.review_work)

    def test_fixture_runs_do_not_require_live_github_access(self) -> None:
        args = forge_metadata.parse_args(["--fixture-testing", "--issue-number", "9101"])

        requirements = queue_config.resolve_host_requirement_queues(args)

        self.assertTrue(requirements.issue_work)
        self.assertFalse(requirements.github_work)

    def test_work_queue_runs_derive_capabilities_from_enabled_queue_limits(self) -> None:
        args = forge_metadata.parse_args(["--run-work-queues"])
        disabled_issue_queues = {
            name: "0"
            for name in host_requirements.ISSUE_LIMIT_ENV_VARS
        }

        with patch.dict(os.environ, {**disabled_issue_queues, "FORGE_REVIEW_LIMIT": "1"}, clear=True):
            requirements = queue_config.resolve_host_requirement_queues(args)

        self.assertFalse(requirements.issue_work)
        self.assertTrue(requirements.review_work)

    def test_work_queue_host_gate_collects_every_enabled_strategy(self) -> None:
        args = forge_metadata.parse_args(["--run-work-queues"])
        environment = {
            name: "0"
            for name in host_requirements.ISSUE_LIMIT_ENV_VARS
        }
        environment.update({
            "FORGE_JAVAC_WORK_LIMIT": "1",
            "FORGE_JAVAC_STRATEGY_NAME": "dynamic_access_main_sources_codex_gpt-5.6-sol",
            "FORGE_WORK_LIMIT": "1",
            "FORGE_STRATEGY_NAME": "dynamic_access_main_sources_pi_gpt-5.6-sol",
        })

        with patch.dict(os.environ, environment, clear=True):
            strategy_names = queue_config.resolve_host_requirement_strategy_names(args)

        self.assertEqual(
            strategy_names,
            [
                "dynamic_access_main_sources_codex_gpt-5.6-sol",
                "dynamic_access_main_sources_pi_gpt-5.6-sol",
            ],
        )

    def test_every_work_starting_invocation_validates_host_requirements(self) -> None:
        with patch.object(queue_config, "ensure_host_requirements") as ensure, \
                patch.object(forge_metadata, "resolve_authenticated_user", return_value="forge-bot"), \
                patch.object(forge_metadata, "resolve_reachability_repo_root", return_value="/repo"), \
                patch.object(forge_metadata, "resolve_metrics_repo_root", return_value="/metrics"), \
                patch.object(forge_metadata, "run_pull_request_review_loop") as review_loop, \
                patch.object(sys, "argv", ["forge_metadata.py", "--review-pr", "library-new-request"]):
            forge_metadata.main()

        ensure.assert_called_once()
        self.assertEqual(
            host_requirements.QueueRequirements(issue_work=False, review_work=True, github_work=True),
            ensure.call_args.kwargs["requirements"],
        )
        self.assertNotIn("environment", ensure.call_args.kwargs)
        review_loop.assert_called_once()

    def test_host_requirements_check_the_selected_repository_not_the_forge_parent(self) -> None:
        checked_paths: list[tuple[str, str]] = []

        def record_gate(forge_dir: str, **kwargs: object) -> None:
            checked_paths.append((forge_dir, str(kwargs["repo_dir"])))

        with patch.object(queue_config, "ensure_host_requirements", side_effect=record_gate), \
                patch.object(forge_metadata, "resolve_authenticated_user", return_value="forge-bot"), \
                patch.object(
                    forge_metadata,
                    "resolve_reachability_repo_root",
                    return_value="/other/repo",
                ) as resolve_repo, \
                patch.object(forge_metadata, "resolve_metrics_repo_root", return_value="/other/repo/forge"), \
                patch.object(forge_metadata, "process_single_issue") as process_issue, \
                patch.object(sys, "argv", [
                    "forge_metadata.py",
                    "--issue-number", "1412",
                    "--reachability-metadata-path", "/other/repo",
                ]):
            forge_metadata.main()

        resolve_repo.assert_called_once_with("/other/repo")
        self.assertEqual([(forge_metadata.FORGE_DIR, "/other/repo")], checked_paths)
        self.assertEqual("/other/repo", process_issue.call_args.args[1])

    def test_cache_maintenance_does_not_validate_host_requirements(self) -> None:
        with patch.object(queue_config, "ensure_host_requirements") as ensure, \
                patch.object(forge_metadata, "clear_issue_caches") as clear_issue_caches, \
                patch.object(sys, "argv", ["forge_metadata.py", "--clear-issue-caches"]):
            forge_metadata.main()

        clear_issue_caches.assert_called_once()
        ensure.assert_not_called()
