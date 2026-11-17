import os
import pathlib
import re
import sys

path = pathlib.Path(sys.argv[1])
image = sys.argv[2]
if not re.fullmatch(r"sha256:[a-f0-9]{64}", image):
    raise SystemExit("Expected a resolved local image ID")
content = re.sub(r"(?m)^APP_IMAGE=.*$", "APP_IMAGE=" + image, path.read_text())
temporary = path.with_name(path.name + ".tmp")
fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, "w", newline="\n") as f:
    f.write(content)
os.replace(temporary, path)
