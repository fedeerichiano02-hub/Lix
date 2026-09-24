from pathlib import Path

# MainActivity.kt already contains the production UI.
# Kept as a no-op for workflow compatibility.
Path("build-app/app/src/main/java/com/example/llama/MainActivity.kt").read_text()
