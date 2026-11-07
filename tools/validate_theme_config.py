"""Validate a theme before replacing the configuration on a TV."""
import json
import sys


def main(path):
    try:
        with open(path, encoding="utf-8-sig") as source:
            value = json.load(source)
    except (OSError, ValueError):
        print(f"{path} is not valid JSON", file=sys.stderr)
        return 1
    if not isinstance(value, dict):
        print(f"{path} is not a JSON object; see themes/README.md. Nothing was sent.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
