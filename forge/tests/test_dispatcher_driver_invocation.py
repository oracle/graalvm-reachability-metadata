# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class LibraryUpdateIssueTests(unittest.TestCase):
    def test_compact_preflight_reports_completed_decision(self) -> None:
        from utility_scripts import library_preparation_preflight as preflight_module

        with tempfile.TemporaryDirectory() as temp_dir:
            claimed_issue = SimpleNamespace(
                scratch_metrics_repo_path=temp_dir,
                preflight_info_path=temp_dir,
            )
            record = {
                "status": "completed",
                "action": "no_action",
                "issue_number": 1412,
                "library": "org.example:lib:1.0.0",
                "deterministic_setup": [],
            }
            stdout = io.StringIO()
            run_location.enter_phase(PHASE_SETUP)
            with contextlib.redirect_stdout(stdout):
                preflight_module._write_and_log_preflight(claimed_issue, record)
            run_location.reset_run_location()

        self.assertIn(
            "[setup] Library preflight completed for org.example:lib:1.0.0: no action (1/3)",
            stdout.getvalue(),
        )
        self.assertNotIn("Preflight decision", stdout.getvalue())

    def test_degraded_preflight_keeps_cause_and_log_in_compact_output(self) -> None:
        from utility_scripts import library_preparation_preflight as preflight_module

        with tempfile.TemporaryDirectory() as temp_dir:
            claimed_issue = SimpleNamespace(
                scratch_metrics_repo_path=temp_dir,
                preflight_info_path=temp_dir,
            )
            record = {
                "status": "degraded",
                "action": "no_action",
                "issue_number": 1412,
                "library": "org.example:lib:1.0.0",
                "deterministic_setup": [],
                "failure_reason": "Agent timed out",
            }
            stdout = io.StringIO()
            run_location.enter_phase(PHASE_SETUP)
            with contextlib.redirect_stdout(stdout):
                preflight_module._write_and_log_preflight(
                    claimed_issue, record, "/tmp/preflight-session.log",
                )
            run_location.reset_run_location()

        self.assertIn("Library preflight degraded for org.example:lib:1.0.0: Agent timed out", stdout.getvalue())
        # The log path reaches the operator's console; the persisted record never carries it.
        self.assertIn("preflight-session.log", stdout.getvalue())
        self.assertNotIn("session_log_path", record)

    def _preflight_record(self, response: dict) -> dict:
        from utility_scripts import library_preparation_preflight as preflight_module

        claimed_issue = SimpleNamespace(
            issue={"number": 1412},
            label="library-update-request",
            current_coordinates=None,
            new_version=None,
        )
        return preflight_module._completed_library_preflight_record(
            claimed_issue,
            {"library": "org.example:lib:1.0.0"},
            response,
            "gpt-5.6-sol",
            None,
            None,
            None,
            None,
        )

    def test_preflight_keeps_a_decision_that_merely_mentions_credentials(self) -> None:
        record = self._preflight_record({
            "action": "advisory_preparation",
            "summary": "The SFTP client needs a live endpoint to cover anything.",
            "deterministic_setup": [
                {"kind": "docker_image", "image": "atmoz/sftp:alpine", "slug": "sftp", "reason": "endpoint"},
            ],
            "agent_guidance": (
                "Configure DefaultSftpSessionFactory to connect to localhost on the mapped "
                "port, authenticate with those credentials, and tokenize the returned "
                "listing into a secret-free assertion."
            ),
            "risks": ["The container ships a throwaway token."],
        })

        self.assertEqual(record["status"], "completed")
        self.assertEqual(len(record["deterministic_setup"]), 1)
        self.assertIn("authenticate with those credentials", record["agent_guidance"])
        self.assertEqual(record["risks"], ["The container ships a throwaway token."])

    def test_preflight_degrades_on_a_requested_action_and_names_the_term(self) -> None:
        with self.assertRaises(ValueError) as raised:
            self._preflight_record({
                "action": "advisory_preparation",
                "summary": "Fetch the fixtures first.",
                "deterministic_setup": [],
                "agent_guidance": "Run curl -sSL https://example.invalid/fixtures.tar.gz before the tests.",
                "risks": [],
            })

        self.assertIn("unsafe preparation behavior: curl", str(raised.exception))

    def test_library_preflight_dispatches_without_a_strategy(self) -> None:
        claimed_issue = records.ClaimedIssue(
            issue={"number": 1412, "title": "Update org.example:lib:1.0.0"},
            label=forge_metadata.LABEL_LIBRARY_UPDATE,
            item_id="item-1",
            base_reachability_metadata_path="/tmp/reachability",
            worktree_path="/tmp/reachability-worktree",
            scratch_metrics_repo_path="/tmp/metrics-worktree",
            issue_coordinates="org.example:lib:1.0.0",
            preflight_info_path="/tmp/preflight-info",
        )

        with patch.object(
                driver_invocation,
                "run_preflight_decision",
                return_value="/tmp/preflight-info/.library_preparation_preflight.json",
        ) as preflight:
            driver_invocation.run_library_preparation_preflight(claimed_issue)

        # The setup role owns the backend and model; no bundle is consulted.
        self.assertEqual(
            set(preflight.call_args.kwargs),
            {"claimed_issue", "issue_body_provider"},
        )

    def test_library_preflight_runs_on_the_setup_role(self) -> None:
        """Preflight prepares a library, so FORGE_SETUP_* selects it."""
        from utility_scripts import library_preparation_preflight as preflight_module

        claimed_issue = SimpleNamespace(
            issue={"number": 1412, "title": "Update org.example:lib:1.0.0"},
            issue_coordinates="org.example:lib:1.0.0",
            worktree_path="/tmp/reachability-worktree",
            preflight_info_path="/tmp/preflight-info",
            current_coordinates=None,
            new_version=None,
            label="library-update-request",
        )
        with patch.object(
                preflight_module,
                "setup_agent_run",
                return_value=AgentRunResult(
                    0, "/tmp/preflight.log", False,
                    '{"action":"no_action","summary":"nothing needed"}',
                    input_tokens=11, output_tokens=7,
                ),
        ) as setup, patch.object(
                preflight_module, "get_setup_agent",
                return_value=AgentSelection(backend="pi", model="cheap-model"),
        ), patch.object(
                preflight_module, "build_library_preflight_input_bundle",
                return_value={"library": "org.example:lib:1.0.0"},
        ), patch.object(
                preflight_module, "_write_text_artifact",
        ), patch.object(
                preflight_module,
                "_write_and_log_preflight",
                side_effect=lambda _i, record, _log=None: record,
        ):
            record = preflight_module.run_library_preparation_preflight(
                claimed_issue=claimed_issue,
                issue_body_provider=lambda _n: "",
            )

        setup.assert_called_once()
        self.assertEqual(setup.call_args.kwargs["task_type"], "library-preparation-preflight")
        # The record names the model that ran, not a bundle's claim about it.
        self.assertEqual(record["model"], "cheap-model")
        self.assertEqual(record["input_tokens_used"], 11)
        self.assertEqual(record["output_tokens_used"], 7)
        # No path field survives into the committed metrics record.
        self.assertEqual(
            {"prompt_path", "raw_response_path", "session_log_path"} & set(record), set(),
        )

    def test_issue_lookup_does_not_request_body_for_generic_claiming(self) -> None:
        issue_payload = {
            "number": 1412,
            "title": "Update support for org.example:lib:1.0.0",
            "labels": [{"name": forge_metadata.LABEL_LIBRARY_UPDATE}],
            "assignees": [],
        }

        with patch.object(issue_queue, "gh_json", return_value=issue_payload) as gh_json:
            issue, label = forge_metadata.get_issue_by_number(1412)

        self.assertEqual(label, forge_metadata.LABEL_LIBRARY_UPDATE)
        self.assertNotIn("body", issue)
        self.assertNotIn("body", gh_json.call_args.args[-1])

    def test_claim_payload_does_not_request_body_for_generic_claiming(self) -> None:
        issue_payload = {
            "number": 1412,
            "title": "Update support for org.example:lib:1.0.0",
            "state": "OPEN",
            "labels": [{"name": forge_metadata.LABEL_LIBRARY_UPDATE}],
            "assignees": [],
        }

        with patch.object(issue_queue, "gh_json", return_value=issue_payload) as gh_json:
            issue = issue_queue.get_issue_claim_payload(1412)

        self.assertNotIn("body", issue)
        self.assertNotIn("body", gh_json.call_args.args[-1])

    def test_issue_body_fetch_is_explicit_for_reporter_metadata_context(self) -> None:
        with patch.object(issue_queue, "gh_json", return_value={"body": "Missing reflection metadata"}) as gh_json:
            body = issue_queue.get_issue_body(1412)

        self.assertEqual(body, "Missing reflection metadata")
        self.assertEqual(gh_json.call_args.args[-1], "body")

    def test_library_update_uses_title_coordinate_when_body_mentions_other_coordinates(self) -> None:
        issue = {
            "number": 1412,
            "title": "Update support for org.example:title-lib:1.2.3",
            "body": (
                "The failure also mentions org.other:body-lib:9.9.9 and "
                "com.acme:context:4.5.6 in the stack trace."
            ),
        }

        claim_metadata = claim_setup.build_claim_metadata(
            issue,
            forge_metadata.LABEL_LIBRARY_UPDATE,
            "/tmp/reachability",
        )

        self.assertEqual(claim_metadata, ("org.example:title-lib:1.2.3", None, None))

    def test_direct_repair_uses_latest_entry_as_failure_baseline(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            group = "io.netty"
            artifact = "netty-common"
            index_dir = os.path.join(repo, "metadata", group, artifact)
            os.makedirs(index_dir, exist_ok=True)
            with open(os.path.join(index_dir, "index.json"), "w", encoding="utf-8") as index_file:
                json.dump([
                    {
                        "metadata-version": "4.1.115.Final",
                        "tested-versions": ["4.1.115.Final", "4.1.130.Final"],
                    },
                    {
                        "latest": True,
                        "metadata-version": "5.0.0.Alpha1",
                        "tested-versions": ["5.0.0.Alpha1"],
                    },
                ], index_file)
            for version in ["4.1.115.Final", "5.0.0.Alpha1"]:
                os.makedirs(os.path.join(repo, "metadata", group, artifact, version), exist_ok=True)
                os.makedirs(os.path.join(repo, "tests", "src", group, artifact, version), exist_ok=True)
            issue = {
                "number": 9408,
                "title": "Fails native image run io.netty:netty-common:4.1.132.Final",
            }

            claim_metadata = claim_setup.build_claim_metadata(
                issue,
                forge_metadata.LABEL_NI_RUN_FAIL,
                repo,
            )

            self.assertEqual(
                claim_metadata,
                (
                    "io.netty:netty-common:4.1.132.Final",
                    "io.netty:netty-common:5.0.0.Alpha1",
                    "4.1.132.Final",
                ),
            )

    def test_extract_issue_requested_metadata_context_keeps_full_issue_body(self) -> None:
        body = """
        The reporter may describe the missing metadata in arbitrary prose.

        Related coordinate: org.other:body-lib:9.9.9

        ```json
        {"reflection":[{"type":"org.example.Missing"}]}
        ```

        native-image reports missing resource file config/app.properties.
        """

        context = claim_setup.extract_issue_requested_metadata_context(body)

        self.assertIn("arbitrary prose", context)
        self.assertIn("org.example.Missing", context)
        self.assertIn("missing resource file config/app.properties", context)
        self.assertIn("Related coordinate", context)

    def test_library_update_passes_issue_requested_metadata_context_to_workflow(self) -> None:
        claimed_issue = _claimed_issue(label=forge_metadata.LABEL_LIBRARY_UPDATE)

        with patch.object(pipeline_execution, "require_claimed_issue_worktree"), \
                patch.object(pipeline_execution, "run_library_preparation_preflight", return_value=None), \
                patch.object(pipeline_execution, "prepare_dynamic_access_chunking", return_value=None), \
                patch.object(
                    driver_invocation,
                    "get_issue_body",
                    return_value=(
                        "Caused by: org.graalvm.nativeimage.MissingReflectionRegistrationError: "
                        "Cannot reflectively invoke method 'public void org.example.Demo.setName(java.lang.String)'."
                    ),
                ) as issue_body, \
                patch.object(
                    pipeline_execution,
                    "select_library_update_route",
                    return_value=LibraryUpdateRoute(
                        selected_driver=ROUTE_IMPROVE_COVERAGE,
                        baseline_coordinates=None,
                        new_version="1.0.0",
                    ),
                ), \
                patch.object(driver_invocation, "run_improve_library_coverage_workflow", return_value=0) as workflow:
            self.assertTrue(pipeline_execution.invoke_pipeline(claimed_issue, "library_update_pi_gpt-5.6-sol", False))

        issue_body.assert_called_once_with(1412)
        workflow.assert_called_once()
        argv = workflow.call_args.args[0]
        self.assertIn("--issue-requested-metadata-context", argv)
        context = argv[argv.index("--issue-requested-metadata-context") + 1]
        self.assertIn("org.example.Demo.setName", context)

    def test_library_new_passes_issue_requested_metadata_context_to_workflow(self) -> None:
        claimed_issue = _claimed_issue(label=forge_metadata.LABEL_LIBRARY_NEW)

        with patch.object(pipeline_execution, "require_claimed_issue_worktree"), \
                patch.object(pipeline_execution, "run_library_preparation_preflight", return_value=None), \
                patch.object(pipeline_execution, "prepare_dynamic_access_chunking", return_value=None), \
                patch.object(
                    driver_invocation,
                    "get_issue_body",
                    return_value="org.example.Demo needs reflective construction.",
                ) as issue_body, \
                patch.object(driver_invocation, "run_add_new_library_support_workflow", return_value=0) as workflow:
            self.assertTrue(pipeline_execution.invoke_pipeline(claimed_issue, "basic_iterative_pi_gpt-5.4", False))

        issue_body.assert_called_once_with(1412)
        workflow.assert_called_once()
        argv = workflow.call_args.args[0]
        context = argv[argv.index("--issue-requested-metadata-context") + 1]
        self.assertIn("org.example.Demo", context)

    def test_routed_javac_repair_passes_issue_requested_metadata_context_to_workflow(self) -> None:
        claimed_issue = _claimed_issue(label=forge_metadata.LABEL_LIBRARY_UPDATE)

        with patch.object(pipeline_execution, "require_claimed_issue_worktree"), \
                patch.object(pipeline_execution, "run_library_preparation_preflight", return_value=None), \
                patch.object(pipeline_execution, "prepare_dynamic_access_chunking", return_value=None), \
                patch.object(
                    driver_invocation,
                    "get_issue_body",
                    return_value="KafkaStreams.topologyMetadata is missing.",
                ), \
                patch.object(
                    pipeline_execution,
                    "select_library_update_route",
                    return_value=LibraryUpdateRoute(
                        selected_driver=ROUTE_FIX_JAVAC,
                        baseline_coordinates="org.example:demo:0.9.0",
                        new_version="1.0.0",
                    ),
                ), \
                patch.object(driver_invocation, "run_fix_javac_workflow", return_value=0) as workflow:
            self.assertTrue(pipeline_execution.invoke_pipeline(claimed_issue, "library_update_pi_gpt-5.6-sol", False))

        argv = workflow.call_args.args[0]
        context = argv[argv.index("--issue-requested-metadata-context") + 1]
        self.assertIn("KafkaStreams.topologyMetadata", context)
