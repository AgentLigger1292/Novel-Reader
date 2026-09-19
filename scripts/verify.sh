#!/usr/bin/env bash
# verify.sh — the single verify gate for novel-reader (contract in AGENTS.md).
# Prints a machine-parseable block:
#   STATUS: success | error
#   SUMMARY: <one line>
#   NEXT: <first fix action, only on failure>
#   REPORT: <test report path, when tests ran>
# Exit codes: 0 pass · 1 unit-test failure · 2 compile/build failure.
set -u
cd "$(dirname "$0")/.."

QUICK=0
[ "${1:-}" = "--quick" ] && QUICK=1

GRADLE=./gradlew
TEST_RESULTS_DIR=app/build/test-results/testDebugUnitTest
TEST_REPORT=app/build/reports/tests/testDebugUnitTest/index.html

fail_compile() {
  echo "STATUS: error"
  echo "SUMMARY: compilation/packaging failed (see gradle output above)"
  echo "NEXT: fix the errors listed above, rerun bash scripts/verify.sh"
  exit 2
}

fail_tests() {
  classes=$(grep -lE 'failures="[1-9]|errors="[1-9]' "$TEST_RESULTS_DIR"/TEST-*.xml 2>/dev/null \
    | sed -E 's|.*/TEST-(.*)\.xml|\1|' | head -3 | tr '\n' ' ')
  echo "STATUS: error"
  echo "SUMMARY: unit tests failed"
  if [ -n "$classes" ]; then
    echo "NEXT: inspect failing test class(es): $classes"
  else
    echo "NEXT: open $TEST_REPORT for the failing tests"
  fi
  echo "REPORT: $TEST_REPORT"
  exit 1
}

echo "[verify] compiling..."
"$GRADLE" :app:compileDebugKotlin --console=plain -q || fail_compile

echo "[verify] unit tests..."
"$GRADLE" :app:testDebugUnitTest --console=plain || fail_tests

if [ "$QUICK" -eq 1 ]; then
  echo "STATUS: success"
  echo "SUMMARY: compile + unit tests passed (quick mode, no APK packaged)"
  exit 0
fi

echo "[verify] packaging APK..."
"$GRADLE" :app:assembleDebug --console=plain -q || fail_compile

tests=$(grep -ho 'tests="[0-9]*"' "$TEST_RESULTS_DIR"/TEST-*.xml 2>/dev/null \
  | grep -o '[0-9]*' | awk '{s+=$1} END {print s+0}')
echo "STATUS: success"
echo "SUMMARY: $tests unit tests passed; APK: app/build/outputs/apk/debug/app-debug.apk"
echo "REPORT: $TEST_REPORT"
exit 0
