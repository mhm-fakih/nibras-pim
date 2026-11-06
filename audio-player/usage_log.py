"""The suite's usage log: one line per action in ~/.local/share/nibras/usage/<app>.log (or
$NIBRAS_USAGE_DIR) — local time, action, count — added up once a day by the backend's App usage
indicators. Kept to the standard library so the player stays standalone."""

import os
from datetime import datetime

APP = "audio-player"


def record_usage(action: str, count: int = 1, app: str = APP) -> None:
    """Append one usage line. Never raises — counting must not get in the way of the player."""
    if count <= 0:
        return
    try:
        folder = os.path.expanduser(os.environ.get("NIBRAS_USAGE_DIR") or "~/.local/share/nibras/usage")
        os.makedirs(folder, exist_ok=True)
        with open(os.path.join(folder, f"{app}.log"), "a", encoding="utf-8") as f:
            f.write(f"{datetime.now():%Y-%m-%dT%H:%M:%S}\t{action}\t{count}\n")
    except OSError:
        pass
