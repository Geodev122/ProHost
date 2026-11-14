#!/usr/bin/env python3
"""Print the unique error lines of a saved GitHub job-log result (the JSON the GitHub MCP `get_job_logs` tool writes to a file).

    python3 scripts/ci/errors.py <saved-result.json-or-.txt> [max_lines]

Keeps a session's context small: a 100 KB log becomes the handful of compiler / Gradle / test lines that matter.
"""
import json, re, sys

PAT = re.compile(r"(\be: .*|error: .*|##\[error\].*|FAILED.*|What went wrong.*|> .*[Ee]xception.*|MISSING .*|CHANGED .*|STALE .*)")
NOISE = ("deprecation-of-node-20", "::error title=", "\tat ", "TaskExecutionException", "CompilationErrorException")

def main() -> int:
    data = json.load(open(sys.argv[1]))
    limit = int(sys.argv[2]) if len(sys.argv) > 2 else 60
    jobs = data.get("logs") or [{"job_name": str(data.get("job_id", "job")), "logs_content": data.get("logs_content", "")}]
    seen, out = set(), 0
    for job in jobs:
        print("JOB", job.get("job_name"))
        for line in job.get("logs_content", "").split("\n"):
            m = PAT.search(line)
            if not m or any(n in line for n in NOISE):
                continue
            text = m.group(1).replace("file:///home/runner/work/ProHost/ProHost/", "")[:300]
            if text in seen:
                continue
            seen.add(text); out += 1
            if out <= limit:
                print(" ", text)
    if out > limit:
        print(f"... {out - limit} more")
    return 0

if __name__ == "__main__":
    sys.exit(main())
