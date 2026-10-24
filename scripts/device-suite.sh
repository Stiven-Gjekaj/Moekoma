#!/bin/sh
# Runs the full device suite on the connected emulator or device, with the test server and the live sources.
# It prints the number of tests that ran and the number that failed. Run it on the release commit before a tag.
set -eu

cd "$(dirname "$0")/.."
results=app/build/outputs/androidTest-results/connected/debug

# The tests of the trackers and of Komga ask the test server at 127.0.0.1:8765 of the device.
python3 scripts/test-server.py >/dev/null 2>&1 &
server=$!
trap 'kill $server 2>/dev/null || true' EXIT
adb reverse tcp:8765 tcp:8765 >/dev/null

rm -rf "$results"
status=0
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.live=1 \
  -Pandroid.testInstrumentationRunnerArguments.server=1 || status=$?

python3 -I - "$results" <<'PY'
import glob, sys, xml.etree.ElementTree as E
seen, failed = set(), set()
for path in glob.glob(sys.argv[1] + "/*.xml"):
    for case in E.parse(path).iter("testcase"):
        name = case.get("classname") + "." + case.get("name")
        seen.add(name)
        if case.find("failure") is not None or case.find("error") is not None:
            failed.add(name)
print(f"{len(seen)} tests, {len(seen) - len(failed)} passed, {len(failed)} failed")
for name in sorted(failed):
    print("FAILED " + name)
PY
exit $status
