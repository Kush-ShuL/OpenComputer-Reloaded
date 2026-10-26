# CI templates

These workflow definitions are kept as templates. They are not active GitHub
Actions workflows. The current GitHub CLI credential can publish repository
content and release attachments, but GitHub rejected workflow creation because
the credential does not have the `workflow` scope.

The independent API is published as prerelease attachments through the GitHub
CLI. Its Maven distribution is also available as a release archive. Publishing
to GitHub Packages remains configured in Gradle but is not implied by a release
attachment.

A maintainer may enable these templates under `.github/workflows/` using a
credential authorized to manage workflows. `publish.yml` then uses the scoped
Actions `GITHUB_TOKEN` to publish only the independent API package.
