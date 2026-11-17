"""Partition the entire API production scope without excluding tests or classes."""

import json
from pathlib import Path


def mutation_matrix(source):
    package = "com.github.theguuholi.rentcars"
    groups = sorted(path.name for path in source.iterdir() if path.is_dir() and any(path.rglob("*.java")))
    roots = sorted(f"{package}.{path.stem}*" for path in source.glob("*.java"))
    if not groups:
        raise ValueError("No API packages found")
    shards = [{"name": group, "targets": f"{package}.{group}.*"} for group in groups]
    if roots:
        shards[0]["targets"] += "," + ",".join(roots)
    return {"include": shards}


if __name__ == "__main__":
    print(json.dumps(mutation_matrix(Path("apps/api/src/main/java/com/github/theguuholi/rentcars"))))
