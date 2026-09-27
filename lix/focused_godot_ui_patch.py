from pathlib import Path

# MainActivity.kt now contains the complete focused Godot UI directly.
# Keep this workflow step intentionally non-destructive so an old UI patch
# cannot replace the working screen or remove required 3D/background hooks.
p = Path('build-app/app/src/main/java/com/example/llama/MainActivity.kt')
if not p.exists():
    raise SystemExit('MainActivity.kt not found')
print('Focused Godot UI already present; obsolete patch skipped.')
