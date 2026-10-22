# Orion media helpers — source and verification

The Windows-only media integration uses two first-party .NET Framework 4.8 executables:

| File inside `assets/orion-client/media/` | Complete source |
| --- | --- |
| `OrionThumbnail.exe` | `src/main/resources/assets/orion-client/orion-thumbnail.cs` |
| `OrionVolume.exe` | `src/main/resources/assets/orion-client/orion-volume.cs` |

Both sources are included in every mod JAR. A complete standalone build kit is also
included under `META-INF/orion-media-source/`: extract that directory and run its
`native/media/build.ps1`. These sources belong to Orion Client and remain under the
repository's license. No third-party native executable or DLL is redistributed.

## Build and reproduce

On Windows, install .NET SDK **8.0.421** and run from the repository root:

```powershell
./native/media/build.ps1
./gradlew.bat build
```

Gradle invokes the helper build automatically when needed. Java **25** is required
for the mod; the Gradle wrapper pins **9.6.0**. Players need only Windows with
.NET Framework 4.8, not a C# compiler, .NET SDK or Windows SDK.

For a mod build on another OS, obtain the verified helper resources from the
Windows workflow and pass `-PmediaHelpersDir=/absolute/path/to/mediaResources` to
Gradle. The directory must preserve `assets/orion-client/media/` and all three
generated files; Gradle fails rather than silently shipping a mod without helpers.

`global.json` pins the compiler. `packages.lock.json` pins all NuGet dependency
versions and content hashes; restore runs in locked mode against nuget.org.
Deterministic compilation, normalized source paths and LF source files allow
byte-for-byte comparison of the executables. Each helper is rebuilt twice and
the build fails if SHA-256 differs. Output:

`build/generated/mediaResources/assets/orion-client/media/`

The directory contains both executables and `SHA256SUMS`; Gradle packages those
exact files. Compare these hashes with the same directory extracted from the mod
JAR. `.github/workflows/build.yml` automates the complete build, extracts the
sources from the release JAR, rebuilds them at another path, compares their hashes
with the packaged binaries, and uploads the mod, source archive and checksums.

Compile-time Microsoft reference packages (not shipped as DLLs):

- [Microsoft.NETFramework.ReferenceAssemblies.net48 1.0.3](https://www.nuget.org/packages/Microsoft.NETFramework.ReferenceAssemblies.net48/1.0.3)
- [Microsoft.Windows.SDK.Contracts 10.0.26100.1](https://www.nuget.org/packages/Microsoft.Windows.SDK.Contracts/10.0.26100.1)
- Transitive packages and hashes are listed in `packages.lock.json`; their runtime
  assets are excluded. Windows supplies the runtime APIs.

## Runtime behavior

Java copies the two bundled executables into a fresh temporary directory and runs
them off the render thread. It never compiles C#, invokes a compiler, downloads a
helper, requests elevation, or reuses an executable left by an earlier run. The
temporary executables are scheduled for deletion on normal JVM exit. Missing
helpers disable the corresponding feature and are logged; there is no compilation
fallback.

`OrionThumbnail.exe` reads the current Windows media session and writes its cover
to the path passed by Java. `OrionVolume.exe` reads the Windows master volume or
sets it to a scalar argument from 0 to 1. Neither helper uses the network. The
existing embedded PowerShell scripts query/control media sessions using Windows
APIs; `Add-Type -AssemblyName` loads a system assembly and does not compile source.

## Modrinth submission

Publish the updated source commit (or give ModrinthModeration access to the private
repository), run **Verified release build** for that commit/tag, and submit the JAR
from its `orion-client-verified` artifact. Give moderators the commit URL, workflow
run URL, `orion-media-source.zip` and `media-SHA256SUMS`. Do not submit an older JAR
or claim a local build came from GitHub Actions.

Suggested moderation reply after the workflow has passed and the new file is uploaded:

> Removed all runtime compilation of the media helpers. The mod now includes two
> prebuilt helpers whose complete sources and reproducible build kit are embedded
> in the JAR under META-INF/orion-media-source/. Our Verified release build workflow
> rebuilds the packaged sources and verifies byte-for-byte SHA-256 equality with
> the packaged executables. Please see the submitted commit and workflow run links,
> source archive and checksum file. No helper downloads or compilation occur on
> players' systems.
