<!--
  Title and body template for pull requests opened automatically from a push to a
  branch listed in the `MAINTAINER_PR_BRANCHES` variable
  (.github/workflows/maintainer-open-pr.yml).

  The first line below that is neither blank nor an HTML comment becomes the PR
  title; everything after it becomes the PR body.

  Placeholders:
    {{SUBJECT}}  subject of the first commit the branch adds to the base branch
    {{BRANCH}}   pushed branch name
    {{AUTHOR}}   login of the account that pushed
    {{COMMITS}}  bullet list of every commit subject on the branch
-->
{{SUBJECT}}

## What this does

<!--
  Motivation first: the problem or the "before" state, then what this change
  does about it. Replace this comment — `gh pr edit --body-file -` works, and
  editing the body does not change the PR author.
-->

## Commits

{{COMMITS}}

---

Authored by @{{AUTHOR}} on `{{BRANCH}}`, opened by `graalvmbot` so the author can review it.
