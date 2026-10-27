# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The post-claim issue-form gate and its rejection feedback
(§AR-forge-dispatcher-decomposition, §FS-forge-run-requirements.3)."""


import hashlib
import sys
from dataclasses import dataclass
from typing import Optional
from utility_scripts.metadata_index import is_newer_than_latest_metadata_version
from utility_scripts.native_image_artifact import ARTIFACT_REPOSITORY_URLS
from utility_scripts.native_image_artifact import artifact_is_published
from utility_scripts.run_location import PHASE_CLAIM
from utility_scripts.run_location import STEP_CHECK_ISSUE_FORM
from utility_scripts.run_location import log_step_progress
from utility_scripts.run_location import pipeline_step
from utility_scripts.stage_logger import log_stage
from dispatcher.config import (
    FAILURE_PIPELINE_LABELS,
    ISSUE_FORM_REJECTION_MARKER_PREFIX,
    ISSUE_FORM_RULE_CURRENT_LATEST_VERSION,
    ISSUE_FORM_RULE_MAVEN_COORDINATES,
    ISSUE_FORM_RULE_NEWER_THAN_LATEST,
    ISSUE_FORM_RULE_PUBLISHED_ARTIFACT,
    ISSUE_FORM_RULE_SINGLE_WORKFLOW_LABEL,
    PIPELINE_LABELS,
)
from dispatcher.coordinates import (
    extract_coordinate_parts,
    load_current_metadata_version,
)
from dispatcher.fixture_support import is_fixture_testing_enabled
from dispatcher.issue_admin import (
    clear_issue_assignees,
    close_issue,
    get_issue_comments,
    post_issue_comment,
)
from dispatcher.issue_queue import get_issue_label_names

@dataclass(frozen=True)
class IssueFormRejection:
    """The one issue-form rule that failed, and the value that failed it."""

    rule: str
    offending_value: str
    requirement: str


@dataclass(frozen=True)
class IssueFormVerdict:
    """Outcome of the claim-held issue-form gate.

    Three-valued on purpose: a rule can fail, or the gate can be unable to
    decide because a remote repository did not answer. Only a failure rejects
    an issue; an undecided answer leaves it for a later cycle.
    §FS-forge-run-requirements.3
    """

    rejection: Optional[IssueFormRejection] = None
    undecided_reason: Optional[str] = None

    @property
    def accepted(self) -> bool:
        return self.rejection is None and self.undecided_reason is None


ISSUE_FORM_ACCEPTED = IssueFormVerdict()


@pipeline_step(
    PHASE_CLAIM,
    STEP_CHECK_ISSUE_FORM,
    operand=lambda arguments: f"issue #{arguments['issue']['number']}",
)
def check_issue_form(
        issue: dict,
        label: str,
        reachability_metadata_path: str,
) -> IssueFormVerdict:
    """Decide every issue-form rule from the payload and the repository.

    Runs inside the pinned issue-base worktree while the exclusive claim is held.
    The rules are decided one at a time and the gate stops at the first failure,
    so the verdict always names the rule and the value that failed it. The only
    rule that leaves the machine is decided last
    (§FS-forge-run-requirements.3, §root/PRCPL-verify-inputs).
    """
    issue_number = issue["number"]
    log_step_progress(
        PHASE_CLAIM,
        STEP_CHECK_ISSUE_FORM,
        f"Checking issue #{issue_number} against newest master in its pinned workspace",
    )

    workflow_labels = sorted(
        label_name for label_name in get_issue_label_names(issue) if label_name in PIPELINE_LABELS
    )
    if len(workflow_labels) > 1:
        return IssueFormVerdict(rejection=IssueFormRejection(
            rule=ISSUE_FORM_RULE_SINGLE_WORKFLOW_LABEL,
            offending_value=", ".join(workflow_labels),
            requirement=(
                "An issue must carry exactly one workflow label, because each one routes to a "
                "different driver working from different assumptions about what the issue asks "
                "for. Keep the single label that describes the work — one of "
                f"{', '.join(f'`{name}`' for name in sorted(PIPELINE_LABELS))} — and remove the rest."
            ),
        ))

    title = str(issue.get("title") or "")
    coordinate_parts = extract_coordinate_parts(title)
    if coordinate_parts is None:
        return IssueFormVerdict(rejection=IssueFormRejection(
            rule=ISSUE_FORM_RULE_MAVEN_COORDINATES,
            offending_value=title,
            requirement=(
                "The issue title must name the library as Maven coordinates "
                "`group:artifact:version`, for example "
                "`Add support for org.postgresql:postgresql:42.7.3`."
            ),
        ))

    group, artifact, requested_version = coordinate_parts
    coordinate = f"{group}:{artifact}:{requested_version}"

    if label in FAILURE_PIPELINE_LABELS:
        current_version = load_current_metadata_version(
            reachability_metadata_path,
            group,
            artifact,
            report_errors=False,
        )
        if current_version is None:
            return IssueFormVerdict(rejection=IssueFormRejection(
                rule=ISSUE_FORM_RULE_CURRENT_LATEST_VERSION,
                offending_value=f"{group}:{artifact}",
                requirement=(
                    f"A `{label}` issue repairs the move from the currently supported version to "
                    "the requested one, so `metadata/<group>/<artifact>/index.json` must already "
                    "carry an entry marked `\"latest\": true`. This artifact has none, so there is "
                    "no supported version to repair from — file a `library-new-request` instead."
                ),
            ))
        if not is_newer_than_latest_metadata_version(
                reachability_metadata_path,
                group,
                artifact,
                requested_version,
        ):
            return IssueFormVerdict(rejection=IssueFormRejection(
                rule=ISSUE_FORM_RULE_NEWER_THAN_LATEST,
                offending_value=requested_version,
                requirement=(
                    f"A `{label}` issue must request a version strictly above the currently "
                    f"supported `latest` version `{current_version}`. Request a newer version, or "
                    "file a `library-update-request` to add support at or below "
                    f"`{current_version}`."
                ),
            ))

    published = artifact_is_published(coordinate)
    if published is None:
        undecided_reason = (
            f"no configured artifact repository answered for {coordinate}; "
            "the issue form stays undecided and the issue is left for a later cycle"
        )
        log_stage("issue-form", f"Issue #{issue_number}: {undecided_reason}")
        return IssueFormVerdict(undecided_reason=undecided_reason)
    if not published:
        return IssueFormVerdict(rejection=IssueFormRejection(
            rule=ISSUE_FORM_RULE_PUBLISHED_ARTIFACT,
            offending_value=coordinate,
            requirement=(
                "The coordinate must be published in a repository the build resolves against: "
                f"{', '.join(ARTIFACT_REPOSITORY_URLS)}. None of them publishes a POM for it, so "
                "no run could resolve the library. Check the group, artifact, and version for "
                "typos."
            ),
        ))

    log_step_progress(
        PHASE_CLAIM,
        STEP_CHECK_ISSUE_FORM,
        f"Issue #{issue_number} accepted: {label} for {coordinate}",
    )
    return ISSUE_FORM_ACCEPTED


def build_issue_form_rejection_marker(rejection: IssueFormRejection) -> str:
    """Return the hidden comment marker keyed on the failed rule and its value."""
    key = hashlib.sha1(
        f"{rejection.rule}\n{rejection.offending_value}".encode("utf-8")
    ).hexdigest()[:12]
    return f"{ISSUE_FORM_REJECTION_MARKER_PREFIX} rule={rejection.rule} key={key} -->"


def build_issue_form_rejection_comment(rejection: IssueFormRejection) -> str:
    """Render the predefined comment the failed rule selects."""
    return (
        f"{build_issue_form_rejection_marker(rejection)}\n"
        f"Forge did not start a run for this issue: it fails the issue-form rule "
        f"`{rejection.rule}`.\n\n"
        f"Offending value: `{rejection.offending_value}`\n\n"
        f"{rejection.requirement}\n\n"
        "This issue is closed because nothing about it changes until someone edits it. "
        "Correct it and reopen it, or file a corrected issue."
    )


def issue_has_issue_form_rejection_comment(issue_number: int, rejection: IssueFormRejection) -> bool:
    """Return whether this exact rule and value were already reported on the issue."""
    marker = build_issue_form_rejection_marker(rejection)
    return any(
        marker in str(comment.get("body") or "")
        for comment in get_issue_comments(issue_number)
    )


def reject_issue_form(
        issue: dict,
        rejection: IssueFormRejection,
) -> bool:
    """Report the failed rule and close the issue while its claim is held.

    A form defect is an input defect outside Forge's generation boundary: no
    `human-intervention` label is applied and no branch is preserved. Closing is
    what takes the issue out of every queue it cannot leave on its own, and the
    comment marker keeps a reopened, unedited issue from collecting a second
    comment. The Forge assignee is cleared only after the issue is closed, so
    another worker cannot enter the rejection sequence
    (§FS-forge-run-requirements.3).
    """
    issue_number = issue["number"]
    log_stage(
        "issue-form",
        f"Rejecting issue #{issue_number}: rule '{rejection.rule}' failed on "
        f"'{rejection.offending_value}'",
    )
    try:
        if issue_has_issue_form_rejection_comment(issue_number, rejection):
            log_stage(
                "issue-form",
                f"Skipping rejection comment for issue #{issue_number}: "
                f"rule '{rejection.rule}' was already reported",
            )
        else:
            log_stage(
                "issue-form",
                f"Posting rejection comment to issue #{issue_number}: rule '{rejection.rule}'",
            )
            post_issue_comment(issue_number, build_issue_form_rejection_comment(rejection))
        close_issue(issue_number, f"issue-form rule '{rejection.rule}' failed")
    except Exception as exc:
        print(
            f"ERROR: Failed to reject issue #{issue_number} for issue-form rule "
            f"'{rejection.rule}': {exc!r}",
            file=sys.stderr,
        )
        return False

    if not is_fixture_testing_enabled():
        try:
            log_stage(
                "issue-close",
                f"Clearing Forge assignee from closed issue #{issue_number}",
            )
            clear_issue_assignees(issue_number)
        except Exception as exc:
            print(
                f"ERROR: Failed to clear Forge assignee from closed issue "
                f"#{issue_number}: {exc!r}",
                file=sys.stderr,
            )
    return True
