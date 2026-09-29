from pathlib import Path

# Build-time UI patcher. Keep this script intentionally small and syntax-safe.
# The Godot UI changes are already present in MainActivity.kt; this step only
# verifies that the target file exists and leaves it unchanged.
p = Path('build-app/app/src/main/java/com/example/llama/MainActivity.kt')
if not p.exists():
    raise FileNotFoundError(f'Godot UI target not found: {p}')

s = p.read_text(encoding='utf-8')
if 'Godot' not in s and 'GODOT' not in s:
    raise RuntimeError('Expected Godot UI markers were not found in MainActivity.kt')

p.write_text(s, encoding='utf-8')
print('simplify_godot_ui.py: OK - existing Godot UI preserved')
