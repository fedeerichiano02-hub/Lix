from pathlib import Path
import re
p=Path('build-app/app/src/main/java/com/example/llama/MainActivity.kt'); s=p.read_text()
new_ui=r'''    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(14),dp(16),dp(12))
            setBackgroundColor(Color.rgb(10,12,16))
        }
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        header.addView(TextView(this).apply{
            text="Lix · Godot"; textSize=23f; setTextColor(Color.WHITE)
            setTypeface(typeface,android.graphics.Typeface.BOLD)
        },LinearLayout.LayoutParams(0,dp(48),1f))
        header.addView(TextView(this).apply{text="●";textSize=12f;setTextColor(Color.rgb(70,220,120));gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(34),dp(48)))
        root.addView(header)
        root.addView(TextView(this).apply{
            text="Asistente de desarrollo · solo herramientas para Godot"
            textSize=11f; setTextColor(Color.rgb(160,170,180)); setPadding(0,0,0,dp(12))
        })
        status=TextView(this).apply{
            text="Preparando Lix..."; textSize=11f; setTextColor(Color.rgb(185,195,205)); gravity=Gravity.CENTER_VERTICAL; setPadding(dp(14),0,dp(14),0)
            background=rounded(Color.rgb(20,23,28),14,Color.rgb(48,54,62))
        }
        root.addView(status,LinearLayout.LayoutParams(-1,dp(44)).apply{bottomMargin=dp(12)})
        val scrollView=ScrollView(this).apply{isFillViewport=true;overScrollMode=View.OVER_SCROLL_NEVER}
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        scrollView.addView(content)
        fun action(title:String,description:String,onClick:()->Unit){
            val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(10),dp(14),dp(10));background=rounded(Color.rgb(20,23,28),14,Color.rgb(50,55,64));setOnClickListener{onClick()}}
            card.addView(TextView(this).apply{text=title;textSize=15f;setTextColor(Color.WHITE);setTypeface(typeface,android.graphics.Typeface.BOLD)})
            card.addView(TextView(this).apply{text=description;textSize=10f;setTextColor(Color.rgb(155,165,176));setPadding(0,dp(4),0,0)})
            content.addView(card,LinearLayout.LayoutParams(-1,dp(70)).apply{bottomMargin=dp(8)})
        }
        action("👤  Crear personaje 3D","Pasar una imagen de referencia a GLB para usarlo en Godot."){open3DPicker()}
        action("🎞  Animar personaje","Abrir el flujo de rigging y animaciones para preparar movimientos del personaje."){
            try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.mixamo.com/")))}catch(_:Exception){toast("No se pudo abrir el navegador.")}
        }
        action("📁  Autorizar proyecto Godot","Elegí una carpeta para que Lix pueda leer y modificar el proyecto."){LixAutomation.handle(this,"elegir proyecto godot")}
        action("🧩  Trabajar sobre el proyecto","Dale una orden para implementar escenas, scripts, personajes, IA, inventario, combate y sistemas del juego."){
            input.requestFocus();input.setText("Trabajá sobre mi proyecto Godot: ");input.setSelection(input.text.length)
        }
        action("⚙  Ejecutar tarea en segundo plano","Dejá una tarea trabajando aunque cierres Lix."){queueGodotTask()}
        action("📦  Importar GLB/asset","Seleccioná un asset para analizarlo o incorporarlo al proyecto."){
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*"},4103)
        }
        content.addView(TextView(this).apply{
            text="Esta versión está dedicada al desarrollo del juego. Las funciones generales de la Lix anterior no aparecen en la interfaz."
            textSize=10f;setTextColor(Color.rgb(120,130,140));setPadding(dp(4),dp(8),dp(4),dp(16))
        })
        root.addView(scrollView,LinearLayout.LayoutParams(-1,0,1f))
        val composer=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        input=EditText(this).apply{
            hint="Ordená a Lix qué hacer en Godot...";textSize=12.5f;maxLines=3;setTextColor(Color.WHITE);setHintTextColor(Color.rgb(120,130,140));setPadding(dp(13),0,dp(8),0)
            background=rounded(Color.rgb(20,23,28),14,Color.rgb(52,58,68));isEnabled=false
        }
        composer.addView(input,LinearLayout.LayoutParams(0,dp(58),1f))
        send=TextView(this).apply{text="↑";textSize=22f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=rounded(Color.rgb(32,38,48),14,Color.rgb(65,75,88));setOnClickListener{sendMessage()}}
        composer.addView(send,LinearLayout.LayoutParams(dp(58),dp(58)).apply{marginStart=dp(8)})
        root.addView(composer,LinearLayout.LayoutParams(-1,dp(64)).apply{topMargin=dp(8)})
        setContentView(root)
        input.imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEND
        input.setOnEditorActionListener{_,id,_->if(id==android.view.inputmethod.EditorInfo.IME_ACTION_SEND){sendMessage();true}else false}
    }

    private fun queueGodotTask(){
        val prompt=input.text.toString().trim()
        if(prompt.isBlank()) { input.requestFocus(); toast("Escribí primero qué querés que Lix haga."); return }
        val id="godot_${System.currentTimeMillis()}"
        val data=androidx.work.Data.Builder().putString(LixBackgroundWorker.KEY_ID,id).putString(LixBackgroundWorker.KEY_PROMPT,prompt).build()
        val request=androidx.work.OneTimeWorkRequestBuilder<LixBackgroundWorker>().setInputData(data).addTag("lix_godot").build()
        androidx.work.WorkManager.getInstance(this).enqueue(request)
        LixBackgroundStore.upsert(this,LixBackgroundStore.Task(id,prompt,"queued"))
        input.setText("")
        toast("Tarea enviada. Lix puede seguir trabajando en segundo plano.")
    }

    private fun open3DPicker(){
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/*"},4102)
    }
'''
s,n=re.subn(r'    private fun buildUi\(\) \{.*?\n    private fun gradientCard\(',new_ui+'    private fun gradientCard(',s,flags=re.S)
if n!=1: raise SystemExit(f'buildUi replacement failed: {n}')
p.write_text(s)