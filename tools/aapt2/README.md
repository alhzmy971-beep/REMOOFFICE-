# aapt2 on ARM64 hosts — why unit tests stop before running

## Symptom

On an ARM64 (aarch64) Linux machine, `./gradlew :app:testDebugUnitTest` aborts
during `:app:processDebugResources` with something like:

```
> Task :app:processDebugResources FAILED
AAPT2 aapt2-8.x.x-linux Daemon #0: Unexpected error during link
/.../build-tools/36.1.0/aapt2: 1: Syntax error: ")" unexpected
```

The message is misleading. It is **not** a malformed `AndroidManifest.xml`, a
broken `res/` file, or a Kotlin/KAPT problem. It is the shell (`dash`) trying to
parse an ELF binary as a script, because the binary cannot be executed on this
CPU.

## Root cause

`aapt2` inside `$ANDROID_HOME/build-tools/<version>/` is an **ELF 64-bit
x86-64** executable:

```
$ file $ANDROID_HOME/build-tools/36.1.0/aapt2
... ELF 64-bit LSB pie executable, x86-64 ...
```

Google does not publish an official `linux-arm64` build of aapt2 in the Maven
artifact (`com.android.tools.build:aapt2:<version>-linux` is x86-64 only), so on
aarch64 the process fails at `execve` time. AGP reports it as a generic
"Unexpected error", and the test task never starts. **No test result is
produced — neither pass nor fail.** That is an environment failure, not
evidence about the code.

## Fix (automated)

```bash
bash tools/aapt2/install-aapt2-arm64.sh
./gradlew --stop
./gradlew :app:testDebugUnitTest
```

The script:

1. detects the host architecture and the architecture of the SDK `aapt2`;
2. exits immediately on x86-64 (nothing to fix) or when a native ARM64 `aapt2`
   is already present;
3. on ARM64 installs `qemu-user-static` + `binfmt-support` when missing;
4. generates `~/.aapt2-arm64/aapt2`, a wrapper that runs the x86-64 binary
   through `qemu-x86_64 -L /usr/x86_64-linux-gnu`;
5. writes `android.aapt2FromMavenOverride=<wrapper>` to
   `~/.gradle/gradle.properties`;
6. verifies the wrapper with `aapt2 version`.

It is idempotent and never modifies project sources.

## Manual alternative

If you prefer not to run the script, do the same by hand:

```bash
sudo apt-get install -y qemu-user-static binfmt-support
mkdir -p ~/.aapt2-arm64
cat > ~/.aapt2-arm64/aapt2 <<'EOF'
#!/bin/sh
exec /usr/bin/qemu-x86_64 -L /usr/x86_64-linux-gnu \
  "$ANDROID_HOME/build-tools/36.1.0/aapt2" "$@"
EOF
chmod +x ~/.aapt2-arm64/aapt2
echo 'android.aapt2FromMavenOverride='"$HOME"'/.aapt2-arm64/aapt2' >> ~/.gradle/gradle.properties
```

Then confirm:

```bash
~/.aapt2-arm64/aapt2 version
./gradlew :app:testDebugUnitTest
```

## Other options

| Option | Notes |
|---|---|
| Build on an x86-64 machine or CI runner | The bundled `android-release.yml` uses `ubuntu-latest`, which is x86-64, so CI is unaffected. |
| Use a native ARM64 `aapt2` | You must compile it from AOSP `frameworks/base/tools/aapt2`; then point `android.aapt2FromMavenOverride` at it. |
| Docker with `--platform linux/amd64` | Works if the container runtime supports emulation. |

## Verifying the environment is really the problem

```bash
file "$ANDROID_HOME/build-tools/36.1.0/aapt2"   # expect: x86-64
uname -m                                        # expect: aarch64
"$ANDROID_HOME/build-tools/36.1.0/aapt2" version  # expect: Exec format error
```

If `aapt2 version` prints a version, the tool is fine and the failure is
elsewhere.
