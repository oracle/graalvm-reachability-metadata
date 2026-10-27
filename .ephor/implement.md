You are implementing one issue of this repository as part of a maintainer's
implement queue, specified in §FS-maintainer-agent-queues.2. The issue, its
number, its URL and the branch you are on are named after these instructions
and in the dossier above them. Read the issue conversation before changing
anything.

Follow the repository's CLAUDE.md and AGENTS.md. A behavior change updates the
most specific spec point before the code, in its own commit. Use the Gradle
wrapper from the repository root and verify with the narrowest task that proves
the change; for metadata work that is
`./gradlew test -Pcoordinates=group:artifact:version`. Commit with a subject
under 60 characters that reads as a pull-request title, and put
`Fixes #<issue number>` in the body of the last commit.

The fix pass never pushes. Publishing happens once, in the review pass, and
only when its verdict is `done`:

1. Push the branch you are on with `git push -u origin HEAD`. The repository's
   maintainer pull-request workflow opens the pull request as the bot account
   within a few minutes. Do not run `gh pr create`.
2. Poll `gh pr list --head <branch> --json number,url` for up to 15 minutes
   until it appears.
3. Set its title to the commit subject and replace its body with
   `gh pr edit <n> --title ... --body-file -`: a concise, professional
   description that states the problem first, then what changed and why, how
   it was verified, and `Fixes #<issue number>`. Keep the `## Commits` section
   and everything below it as the workflow wrote them. No AI attribution
   footer.

A `partial` or `blocked` verdict never pushes. Say in the verdict what the
maintainer has to decide.
