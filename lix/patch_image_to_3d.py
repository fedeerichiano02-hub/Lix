from pathlib import Path

p = Path('build-app/app/src/main/java/com/example/llama/MainActivity.kt')
s = p.read_text()

needle = '        if (prompt.isEmpty() || !ready) return\n'
insert = '''        if (prompt.isEmpty() || !ready) return
        if (LixImageTo3D.isRequest(prompt)) {
            prefs.edit().putBoolean("pending_3d", true).putString("pending_3d_prompt", prompt).apply()
            input.setText("")
            pickImageForVision()
            return
        }
'''
if needle not in s:
    raise SystemExit('sendMessage insertion point not found')
s = s.replace(needle, insert, 1)

old = 'if (requestCode == 1002 && resultCode == RESULT_OK) { data?.data?.let { analyzeImage(it) }; return }'
new = '''if (requestCode == 1002 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val pending3d = prefs.getBoolean("pending_3d", false)
                val pendingPrompt = prefs.getString("pending_3d_prompt", "") ?: ""
                prefs.edit().remove("pending_3d").remove("pending_3d_prompt").apply()
                if (pending3d) LixImageTo3D.start(this, uri, pendingPrompt) else analyzeImage(uri)
            }
            return
        }'''
if old not in s:
    raise SystemExit('1002 branch not found')
s = s.replace(old, new, 1)

p.write_text(s)
print('Image-to-3D routing patched successfully')
