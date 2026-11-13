#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# REMOOFFICE - aapt2 fix for ARM64 Linux hosts
#
# Why this exists
# ---------------
# The Android SDK build-tools package ships `aapt2` as an ELF **x86-64**
# executable.  On an ARM64 host (aarch64) the kernel cannot execute it.  Some
# toolchains then fall back to invoking it through a shell, which produces the
# confusing, non-actionable error:
#
#     /.../aapt2: 1: Syntax error: ")" unexpected
#
# (that message comes from `dash`/`sh` trying to parse a binary, not from a
# broken resource file).  Unit tests still need `processDebugResources`, which
# runs aapt2, so `testDebugUnitTest` aborts before a single test executes.
#
# What this script does
# ---------------------
#   * detects the host architecture;
#   * on x86-64 it does nothing (the SDK binary already works);
#   * on ARM64 it installs `qemu-user-static` + binfmt support and creates a
#     tiny wrapper that transparently runs the x86-64 aapt2 under
#     `qemu-x86_64`;
#   * writes `android.aapt2FromMavenOverride=<wrapper>` to
#     ~/.gradle/gradle.properties so AGP uses the wrapper for every build;
#   * verifies the wrapper by running `aapt2 version`.
#
# Usage
# -----
#   bash tools/aapt2/install-aapt2-arm64.sh
#   ./gradlew :app:testDebugUnitTest
#
# The script is idempotent and never touches project sources.
# ---------------------------------------------------------------------------
set -euo pipefail

log()  { printf '[aapt2-fix] %s\n' "$*"; }
fail() { printf '[aapt2-fix] ERROR: %s\n' "$*" >&2; exit 1; }

HOST_ARCH="$(uname -m)"
log "host architecture: ${HOST_ARCH}"

# --- locate the SDK ---------------------------------------------------------
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "${SDK}" ]; then
  for candidate in "$HOME/Android/Sdk" /workspace/android-sdk /opt/android-sdk /usr/lib/android-sdk; do
    [ -d "${candidate}" ] && SDK="${candidate}" && break
  done
fi
[ -n "${SDK}" ] && [ -d "${SDK}" ] || fail "Android SDK not found. Set ANDROID_HOME."
log "Android SDK: ${SDK}"

AAPT2=""
if [ -d "${SDK}/build-tools" ]; then
  # highest version first
  while IFS= read -r v; do
    if [ -x "${SDK}/build-tools/${v}/aapt2" ]; then
      AAPT2="${SDK}/build-tools/${v}/aapt2"; break
    fi
  done < <(ls -1 "${SDK}/build-tools" | sort -Vr)
fi
[ -n "${AAPT2}" ] || fail "aapt2 not found under ${SDK}/build-tools/*/aapt2"
log "aapt2 binary: ${AAPT2}"

# --- inspect the binary -----------------------------------------------------
if command -v file >/dev/null 2>&1; then
  BIN_DESC="$(file -b "${AAPT2}")"
else
  BIN_DESC="$(head -c 20 "${AAPT2}" | tr -d '\0' || true)"
fi
log "aapt2 description: ${BIN_DESC}"

case "${BIN_DESC}" in
  *"ELF 64-bit"*"x86-64"*)  BIN_ARCH=x86_64 ;;
  *"ELF 64-bit"*"aarch64"*|*"ELF 64-bit"*"ARM aarch64"*) BIN_ARCH=arm64 ;;
  *"ELF"*)                  BIN_ARCH=other-elf ;;
  *)                        BIN_ARCH=not-elf ;;
esac
log "aapt2 binary architecture: ${BIN_ARCH}"

if [ "${BIN_ARCH}" = "not-elf" ]; then
  fail "The aapt2 file is not an ELF binary (corrupted or partial download).
       Delete '${SDK}/build-tools' and re-run 'sdkmanager \"build-tools;36.1.0\"'."
fi

# --- decide whether a fix is needed ----------------------------------------
case "${HOST_ARCH}" in
  x86_64|amd64)
    if [ "${BIN_ARCH}" = "x86_64" ]; then
      log "x86-64 host with x86-64 aapt2 - nothing to do."
      "${AAPT2}" version || fail "aapt2 does not run even on x86-64; reinstall build-tools."
      exit 0
    fi
    fail "Host is x86-64 but aapt2 is ${BIN_ARCH}; reinstall build-tools."
    ;;
  aarch64|arm64)
    if [ "${BIN_ARCH}" = "arm64" ]; then
      log "Native ARM64 aapt2 detected - nothing to do."
      "${AAPT2}" version || fail "native aapt2 does not run."
      exit 0
    fi
    ;;
  *)
    fail "Unsupported host architecture: ${HOST_ARCH}"
    ;;
esac

# --- ARM64 host, x86-64 binary: use qemu-user ------------------------------
log "ARM64 host with x86-64 aapt2 -> setting up qemu-x86_64 translation."

QEMU="$(command -v qemu-x86_64-static || command -v qemu-x86_64 || true)"
if [ -z "${QEMU}" ]; then
  log "qemu-x86_64 not found; installing qemu-user-static + binfmt-support."
  if command -v apt-get >/dev/null 2>&1; then
    SUDO=""
    [ "$(id -u)" -ne 0 ] && SUDO="sudo"
    ${SUDO} apt-get update -qq
    ${SUDO} DEBIAN_FRONTEND=noninteractive apt-get install -y -qq qemu-user-static binfmt-support
  else
    fail "Please install qemu-user-static manually and re-run."
  fi
  QEMU="$(command -v qemu-x86_64-static || command -v qemu-x86_64 || true)"
fi
[ -n "${QEMU}" ] || fail "qemu-x86_64 still not available after install."
log "qemu: ${QEMU}"

WRAP_DIR="${HOME}/.aapt2-arm64"
mkdir -p "${WRAP_DIR}"
WRAPPER="${WRAP_DIR}/aapt2"

# -L points qemu at the x86-64 loader/libs so the dynamically linked aapt2
# finds ld-linux-x86-64.so.2 and libc.
QEMU_LIBDIR=""
for d in /usr/x86_64-linux-gnu /usr/lib/x86_64-linux-gnu /lib/x86_64-linux-gnu; do
  [ -d "$d" ] && QEMU_LIBDIR="$d" && break
done

cat > "${WRAPPER}" <<EOF
#!/bin/sh
# Generated by REMOOFFICE tools/aapt2/install-aapt2-arm64.sh
# Runs the x86-64 Android SDK aapt2 on an ARM64 host through qemu-user.
exec "${QEMU}" ${QEMU_LIBDIR:+-L ${QEMU_LIBDIR}} "${AAPT2}" "\$@"
EOF
chmod +x "${WRAPPER}"
log "wrapper created: ${WRAPPER}"

# --- verify ----------------------------------------------------------------
if ! "${WRAPPER}" version >/dev/null 2>&1; then
  fail "wrapper failed to run aapt2. Try: ${QEMU} -L ${QEMU_LIBDIR:-/usr/x86_64-linux-gnu} ${AAPT2} version"
fi
log "wrapper OK: $("${WRAPPER}" version | head -1)"

# --- persist the Gradle override ------------------------------------------
GRADLE_PROPS="${HOME}/.gradle/gradle.properties"
mkdir -p "$(dirname "${GRADLE_PROPS}")"
touch "${GRADLE_PROPS}"
if grep -q '^android\.aapt2FromMavenOverride=' "${GRADLE_PROPS}" 2>/dev/null; then
  # replace existing value
  tmp="$(mktemp)"
  sed "s|^android\.aapt2FromMavenOverride=.*|android.aapt2FromMavenOverride=${WRAPPER}|" \
      "${GRADLE_PROPS}" > "${tmp}" && mv "${tmp}" "${GRADLE_PROPS}"
  log "updated android.aapt2FromMavenOverride in ${GRADLE_PROPS}"
else
  {
    echo ""
    echo "# Added by REMOOFFICE tools/aapt2/install-aapt2-arm64.sh"
    echo "# ARM64 host: run the x86-64 SDK aapt2 through qemu-user."
    echo "android.aapt2FromMavenOverride=${WRAPPER}"
  } >> "${GRADLE_PROPS}"
  log "wrote android.aapt2FromMavenOverride to ${GRADLE_PROPS}"
fi

cat <<EOF

[aapt2-fix] Done.

Next steps:
  ./gradlew --stop
  ./gradlew :app:testDebugUnitTest

If you build inside a container that does not read ~/.gradle/gradle.properties,
pass the property explicitly:
  ./gradlew -Pandroid.aapt2FromMavenOverride=${WRAPPER} :app:testDebugUnitTest
EOF
