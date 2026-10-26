# CI templates

These workflow definitions are kept as templates. They are not active GitHub
Actions workflows. Enabling a template means copying it into `.github/workflows/`
with a credential authorized to manage workflows.

The independent API is distributed through **JitPack**. The root `jitpack.yml`
selects Java 21 and runs:

```sh
./gradlew -p api publishToMavenLocal --console=plain
```

This uses the standalone `api/` SDK project and does not compile the unfinished
game runtime. The JitPack coordinate is
`com.github.Kush-ShuL:OpenComputer-Reloaded:<tag>`; it contains only the API.
`api-8.0.0-alpha.1` is a proposed validation tag, not a claim of a successful
JitPack build or a stable API release. Local Maven builds instead publish
`io.github.kush-shul:opencomputer-reloaded-api:<api_version>`.

For an API release, create the tag and a GitHub Release with no attached assets.
JitPack supplies the SDK artifacts. Do not upload JARs or Maven archives to that
Release, and do not publish the API to GitHub Packages. Keep the upstream
copyright, license files and NOTICE in the SDK artifacts.

`build.yml` checks the full mod and the standalone SDK. `publish.yml` is a
validation-only template for API tags: its historical filename does not mean it
publishes packages or uploads release assets. These templates need only
`contents: read`; publishing credentials are not required.

Consumers use `compileOnly` and install the corresponding full mod separately in
the development game and player environment. They must not shade or redistribute
a second copy of the API inside an integration mod. See [API.md](../API.md).
