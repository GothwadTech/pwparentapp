#!/usr/bin/env python3
"""Turn JUnit XML test results into GitHub Actions `::error::` annotations.

The workflow logs are not always reachable from outside, but check-run annotations are, so a
failing unit test becomes visible without downloading the log bundle.
"""
import glob
import sys
import xml.etree.ElementTree as ET

paths = sorted(glob.glob("app/build/test-results/**/*.xml", recursive=True))
failures = 0
for path in paths:
    try:
        root = ET.parse(path).getroot()
    except Exception:
        continue
    for case in root.iter("testcase"):
        problem = case.find("failure")
        if problem is None:
            problem = case.find("error")
        if problem is None:
            continue
        failures += 1
        name = f"{case.get('classname')}.{case.get('name')}"
        message = (problem.get("message") or problem.text or "failed").strip()
        message = " ".join(message.split())[:900]
        print(f"::error title={name}::{message}")

if failures == 0:
    print("::error::Unit tests failed but no JUnit XML was found (task failed before running)")
    sys.exit(1)
print(f"{failures} failing test(s) reported")
