package privateadmin.internal

import android.app.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity:ComponentActivity() {
 private val api=SupabaseApi()
 private var posterUrlField:EditText?=null
 private var posterPreview:ImageView?=null
 private var uploadedPosterBytes:ByteArray?=null
 private val posterPicker=registerForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)task{val mime=contentResolver.getType(uri)?:throw IllegalStateException("Cannot identify image type.");val ext=when(mime){"image/jpeg"->"jpg";"image/png"->"png";"image/webp"->"webp";else->throw IllegalStateException("Use JPG, PNG or WebP image.")};val bytes=contentResolver.openInputStream(uri)?.use{it.readBytes()}?:throw IllegalStateException("Unable to read selected image.");if(bytes.size>8*1024*1024)throw IllegalStateException("Image is larger than 8 MB.");val path="campaign-${System.currentTimeMillis()}-${(100000..999999).random()}.$ext";val url=api.uploadAdPoster(path,bytes,mime);posterUrlField?.setText(url);uploadedPosterBytes=bytes;posterPreview?.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size));posterPreview?.visibility=View.VISIBLE;toast("Poster uploaded. Preview updated. Tap Save My Advertisement.")}}
 private lateinit var dao:LocalDao
 private lateinit var sync:SyncEngine
 private val dataInitMutex=Mutex()
 private var lang=UiLanguage.EN
 private var body:LinearLayout?=null
 private fun tr(a:String,b:String,c:String)=uiText(a,b,c,lang)
 private fun dp(value:Int):Int=(value*resources.displayMetrics.density+0.5f).toInt()
 private class ClockGlyphView(context:android.content.Context):View(context){
  private val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
  override fun onDraw(canvas:android.graphics.Canvas){
   super.onDraw(canvas)
   val cx=width/2f;val cy=height/2f;val radius=minOf(width,height)*0.43f
   p.style=android.graphics.Paint.Style.FILL;p.color=0xffeaf2fb.toInt();canvas.drawCircle(cx,cy,radius,p)
   p.style=android.graphics.Paint.Style.STROKE;p.strokeWidth=resources.displayMetrics.density*1.8f;p.color=0xff245b8f.toInt();canvas.drawCircle(cx,cy,radius,p)
   for(i in 0 until 12){val a=Math.PI*i/6.0;val outer=radius*0.78f;val inner=radius*(if(i%3==0)0.57f else 0.68f);p.strokeWidth=resources.displayMetrics.density*(if(i%3==0)1.8f else 1.1f);canvas.drawLine(cx+Math.sin(a).toFloat()*inner,cy-Math.cos(a).toFloat()*inner,cx+Math.sin(a).toFloat()*outer,cy-Math.cos(a).toFloat()*outer,p)}
   p.strokeCap=android.graphics.Paint.Cap.ROUND;p.strokeWidth=resources.displayMetrics.density*2.5f
   canvas.drawLine(cx,cy,cx,cy-radius*0.48f,p)
   canvas.drawLine(cx,cy,cx+radius*0.38f,cy+radius*0.20f,p)
   p.style=android.graphics.Paint.Style.FILL;canvas.drawCircle(cx,cy,resources.displayMetrics.density*2.2f,p)
  }
 }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);onBackPressedDispatcher.addCallback(this,object:androidx.activity.OnBackPressedCallback(true){override fun handleOnBackPressed(){goBack()}});dao=LocalDb.get(this).dao();sync=SyncEngine(api,dao);lang=runCatching{UiLanguage.valueOf(getPreferences(0).getString("lang","EN")!!)}.getOrDefault(UiLanguage.EN)
  val p=getPreferences(0);if(p.contains("access")){api.session=Session(p.getString("access","")!!,p.getString("refresh","")!!,p.getLong("expiry",0));home()}else login()
 }
 private val pageStack=java.util.ArrayDeque<View>()
 private fun findBody(v:View):LinearLayout?{if(v is LinearLayout && v.tag=="admin_page_body")return v;if(v is android.view.ViewGroup)for(i in 0 until v.childCount)findBody(v.getChildAt(i))?.let{return it};return null}
 private fun goBack(){if(pageStack.isNotEmpty()){setContentView(pageStack.removeLast());body=(findViewById<View>(android.R.id.content) as? android.view.ViewGroup)?.getChildAt(0)?.let{findBody(it)};return};AlertDialog.Builder(this).setMessage("Do you want to exit Bustime Operations?").setNegativeButton("Cancel",null).setPositiveButton("Exit"){_,_->finish()}.show()}
 private fun screen(title:String,back:Boolean=true,subtitle:String=""){
  val previous=(findViewById<View>(android.R.id.content) as? android.view.ViewGroup)?.getChildAt(0);if(previous!=null&&back)pageStack.addLast(previous)
  val root=LinearLayout(this).apply{orientation=1;setPadding(dp(24),dp(16),dp(24),dp(24));setBackgroundColor(Color.WHITE)}
  root.addView(TextView(this).apply{text=title;textSize=24f;typeface=android.graphics.Typeface.DEFAULT_BOLD;setTextColor(0xff161b22.toInt());setPadding(0,0,0,dp(8))})
  if(subtitle.isNotBlank())root.addView(TextView(this).apply{text=subtitle;textSize=14f;setTextColor(0xff535e6e.toInt());setPadding(0,0,0,dp(12))})
  if(back)root.addView(toolbarAction(tr("‹  Back","‹  वापस","‹  ਵਾਪਸ")){goBack()},LinearLayout.LayoutParams(dp(112),dp(42)).apply{bottomMargin=dp(12)})
  val scroll=ScrollView(this).apply{isFillViewport=true};body=LinearLayout(this).apply{orientation=1;tag="admin_page_body"};scroll.addView(body,android.widget.FrameLayout.LayoutParams(-1,-2));root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
 }
 private fun text(s:String,size:Int=16,bold:Boolean=false)=TextView(this).apply{text=s;textSize=size.toFloat();setTextColor(0xff161b22.toInt());if(bold)typeface=android.graphics.Typeface.DEFAULT_BOLD;setPadding(0,2,0,2)}
 private fun add(v:View){body?.addView(v,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=8})}
 private fun panelCard(title:String,subtitle:String="",onClick:()->Unit):View {
  val card=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12));background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=dp(12).toFloat();setStroke(dp(1),0xffe0e6ed.toInt())};isClickable=true;isFocusable=true;setOnClickListener{onClick()}}
  card.addView(text(title,15,true).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END},LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=dp(12)})
  if(subtitle.isNotBlank())card.addView(text(subtitle,12,false).apply{gravity=Gravity.END;maxLines=3;setTextColor(0xff535e6e.toInt())},LinearLayout.LayoutParams(dp(112),-2))
  return card
 }
 private fun countLabel(value:Int,unit:String)=text("$value $unit",12,true).apply{setTextColor(0xff64748b.toInt())}
 private fun button(s:String,primary:Boolean=true,fn:()->Unit)=Button(this).apply{
  text=s;isAllCaps=false;textSize=14f;minHeight=dp(48);setPadding(dp(16),dp(8),dp(16),dp(8))
  typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)
  setTextColor(if(primary)Color.WHITE else 0xffb42318.toInt())
  background=GradientDrawable().apply{setColor(if(primary)0xff245b8f.toInt() else 0xfffff1ee.toInt());cornerRadius=dp(13).toFloat();if(!primary)setStroke(dp(1),0xfff2c7c1.toInt())}
  elevation=dp(2).toFloat();stateListAnimator=null;setOnClickListener{fn()}
 }
 private fun homeTile(label:String,color:Int,fn:()->Unit)=Button(this).apply{
  text=label;isAllCaps=false;textSize=14f;minHeight=dp(58);gravity=Gravity.CENTER
  typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)
  setTextColor(Color.WHITE);setPadding(dp(10),dp(10),dp(10),dp(10))
  background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(color,Color.rgb(Color.red(color)*8/10,Color.green(color)*8/10,Color.blue(color)*8/10))).apply{cornerRadius=dp(15).toFloat()}
  elevation=dp(2).toFloat();stateListAnimator=null;setOnClickListener{fn()}
 }
 private fun toolbarAction(label:String,fn:()->Unit)=Button(this).apply{
  text=label;isAllCaps=false;textSize=13f;minHeight=dp(42);setTextColor(0xff245b8f.toInt())
  background=GradientDrawable().apply{setColor(0xffeaf2fb.toInt());cornerRadius=dp(12).toFloat();setStroke(dp(1),0xffc8d9ec.toInt())}
  setOnClickListener{fn()}
 }
 private fun input(h:String,v:String="")=EditText(this).apply{hint=h;setText(v);textSize=16f}
 private fun task(fn:suspend()->Unit){lifecycleScope.launch{try{fn()}catch(e:Exception){toast(e.message?: "Request failed")}}}
 private suspend fun prepareData():Long=dataInitMutex.withLock{SnapshotSeeder.seed(assets,dao);sync.sync()}
 private fun toast(s:String){Toast.makeText(this,s,Toast.LENGTH_LONG).show()}
 private fun login(){pageStack.clear();screen("Sign in",false);body?.addView(ImageView(this).apply{setImageResource(R.drawable.admin_logo);scaleType=ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(dp(104),dp(104)));add(text("Bustime Admin",24,true));add(text("Private owner access",18,true));val email=input("Email");val pass=input("Password");pass.inputType=129;add(email);add(pass);add(button("Sign in"){task{val s=api.signIn(email.text.toString().trim(),pass.text.toString());getPreferences(0).edit().putString("access",s.access).putString("refresh",s.refresh).putLong("expiry",s.expiresAt).apply();home()}})}
 private var adConfig=JSONObject()
 private fun home(selectedTab:String="existing"){pageStack.clear();screen("Bustime Operations",false)
  val language=Spinner(this);language.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("English","हिन्दी","ਪੰਜਾਬੀ"));language.setSelection(lang.ordinal);language.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:android.widget.AdapterView<*>?){};override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,pos:Int,id:Long){lang=UiLanguage.values()[pos];getPreferences(0).edit().putString("lang",lang.name).apply()}};add(language)
  val tabs=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  fun tab(label:String,key:String){
   val active=selectedTab==key
   val b=Button(this).apply{text=label;isAllCaps=false;textSize=12f;minHeight=dp(46);setPadding(dp(4),dp(6),dp(4),dp(6));setTextColor(if(active)Color.WHITE else 0xff245b8f.toInt());background=GradientDrawable().apply{setColor(if(active)0xff245b8f.toInt() else 0xffeaf2fb.toInt());cornerRadius=dp(10).toFloat();if(!active)setStroke(dp(1),0xffc8d9ec.toInt())};setOnClickListener{home(key)}}
   tabs.addView(b,LinearLayout.LayoutParams(0,dp(48),1f).apply{setMargins(dp(3),dp(4),dp(3),dp(10))})
  }
  tab("Existing Data","existing");tab("Advertisement","advertisement");tab("Analytics & Stats","analytics")
  add(tabs)
  if(selectedTab=="existing"){
   val menu=GridLayout(this).apply{columnCount=2;useDefaultMargins=false}
   val tiles=listOf(
    Triple(tr("Sync now","अभी सिंक करें","ਹੁਣੇ ਸਿੰਕ ਕਰੋ"),0xff2463a6.toInt(),{task{toast("Synced · revision "+prepareData())}}),
    Triple(tr("Route List","रूट सूची","ਰੂਟ ਸੂਚੀ"),0xff147d75.toInt(),{routeList()}),
    Triple(tr("Bus Stop List","बस स्टॉप सूची","ਬੱਸ ਸਟਾਪ ਸੂची"),0xff3973b9.toInt(),{placeList()}),
    Triple(tr("Recycle Bin","रिसायकल बिन","ਰੀਸਾਈਕਲ ਬਿਨ"),0xff68758b.toInt(),{recycle()}),
    Triple(tr("Review Center","रिव्यू सेंटर","ਰੀਵਿਊ ਸੈਂਟਰ"),0xff8057a6.toInt(),{reviews()}),
    Triple(tr("History","हिस्ट्री","ਇਤਿਹਾਸ"),0xffb36a35.toInt(),{history(true)}),
    Triple(tr("Add Route","रूट जोड़ें","ਰੂਟ ਜੋੜੋ"),0xff16845f.toInt(),{editRoute(null)}),
    Triple(tr("Correction Route","रूट सुधारें","ਰੂਟ ਸੋਧੋ"),0xff277b9b.toInt(),{correctionRouteList()}),
    Triple(tr("Add Bus Stop","बस स्टॉप जोड़ें","ਬੱਸ ਸਟਾਪ ਜੋੜੋ"),0xff4b7c4a.toInt(),{editPlace(null)})
   )
   tiles.forEachIndexed{index,item->val tile=homeTile(item.first,item.second,item.third);menu.addView(tile,GridLayout.LayoutParams().apply{width=0;height=dp(64);columnSpec=GridLayout.spec(index%2,1f);rowSpec=GridLayout.spec(index/2);setMargins(dp(4),dp(5),dp(4),dp(5))})}
   add(menu)
   task{prepareData()}
  }else if(selectedTab=="advertisement"){
   advertisementTab()
  }else{
   analyticsTab()
  }
  add(button(tr("Check for Update","अपडेट चेक करें","ਅੱਪਡੇਟ ਚੈੱਕ ਕਰੋ"),true){checkForUpdate()})
  add(button(tr("Sign out","साइन आउट","ਸਾਈਨ ਆਉਟ"),false){api.session=null;getPreferences(0).edit().clear().apply();login()})
 }

 private fun checkForUpdate(){
  task {
   toast("Checking for updates…")
   val release=withContext(kotlinx.coroutines.Dispatchers.IO){
    val connection=(URL("https://api.github.com/repos/amarjeetmaan15/Admin/releases/latest").openConnection() as HttpURLConnection)
    connection.connectTimeout=15000;connection.readTimeout=20000
    connection.setRequestProperty("Accept","application/vnd.github+json")
    connection.setRequestProperty("User-Agent","Bustime-Admin-Updater")
    try {
     if(connection.responseCode !in 200..299) throw IllegalStateException("Update check failed (HTTP "+connection.responseCode+").")
     JSONObject(connection.inputStream.bufferedReader().use{it.readText()})
    } finally { connection.disconnect() }
   }
   val assets=release.optJSONArray("assets")?:JSONArray()
   var apkUrl=""
   for(i in 0 until assets.length()){
    val asset=assets.optJSONObject(i)?:continue
    if(asset.optString("name").endsWith(".apk",true)){apkUrl=asset.optString("browser_download_url");if(apkUrl.isNotBlank())break}
   }
   if(apkUrl.isBlank())throw IllegalStateException("No APK is attached to the latest release.")
   if(!apkUrl.startsWith("https://github.com/amarjeetmaan15/Admin/releases/download/"))throw IllegalStateException("Invalid update download address.")
   val latestCode=Regex("build-(\\d+)",RegexOption.IGNORE_CASE).find(release.optString("tag_name"))?.groupValues?.get(1)?.toLongOrNull()?:0L
   val installedInfo=packageManager.getPackageInfo(packageName,0)
   @Suppress("DEPRECATION")
   val installedCode=if(android.os.Build.VERSION.SDK_INT>=28)installedInfo.longVersionCode else installedInfo.versionCode.toLong()
   if(latestCode<=installedCode){toast("You're using the latest version.");return@task}
   toast("New update found. Downloading…")
   val apk=withContext(kotlinx.coroutines.Dispatchers.IO){
    val connection=(URL(apkUrl).openConnection() as HttpURLConnection)
    connection.connectTimeout=15000;connection.readTimeout=60000;connection.instanceFollowRedirects=true
    try {
     if(connection.responseCode !in 200..299)throw IllegalStateException("APK download failed (HTTP "+connection.responseCode+").")
     val file=File(cacheDir,"admin-update.apk")
     connection.inputStream.use{input->file.outputStream().use{output->input.copyTo(output)}}
     file
    } finally {connection.disconnect()}
   }
   val uri=FileProvider.getUriForFile(this@MainActivity,packageName+".fileprovider",apk)
   if(android.os.Build.VERSION.SDK_INT>=26&&!packageManager.canRequestPackageInstalls()){
    startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName")))
    toast("Allow app installs, then tap Check for Update again.")
    return@task
   }
   val install=Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
   startActivity(install)
  }
 }


 private fun sectionTitle(title:String,subtitle:String=""){add(text(title,18,true));if(subtitle.isNotBlank())add(text(subtitle,12).apply{setTextColor(0xff64748b.toInt())})}
 private fun adToggle(label:String,checked:Boolean,onChange:(Boolean)->Unit):Switch {val v=Switch(this).apply{text=label;isChecked=checked;textSize=14f;setPadding(dp(4),dp(8),dp(4),dp(8));setOnCheckedChangeListener{_,b->onChange(b)}};add(v);return v}
 private fun adField(label:String,hint:String,value:String=""):EditText {add(text(label,13,true));return input(hint,value).also{add(it)}}
 private fun adSpinner(label:String,items:List<String>,selected:Int):Spinner {add(text(label,13,true));val v=Spinner(this);v.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,items);v.setSelection(selected.coerceIn(0,items.lastIndex));add(v);return v}
 private fun advertisementTab(){
  sectionTitle("Advertisement","Manage the in-app campaign poster and Start.io ad placements. Settings are shared with the app.")
  val tabs=LinearLayout(this).apply{orientation=0}
  tabs.addView(button("↻ Refresh settings",false){task{adConfig=api.loadAdSettings();home("advertisement")}},LinearLayout.LayoutParams(0,dp(46),1f))
  add(tabs)
  task{adConfig=api.loadAdSettings();renderAdvertisement()}
 }
 private fun renderAdvertisement(){
  body?.removeViews(2,(body?.childCount?:2)-2)
  val my=adConfig.optJSONObject("my")?:JSONObject()
  val start=adConfig.optJSONObject("startio")?:JSONObject()
  sectionTitle("My Advertisement","A dismissible promotional poster shown inside the app.")
  var enabled=my.optBoolean("enabled",false)
  adToggle("Enable campaign",enabled){enabled=it}
  val category=adSpinner("Campaign category",listOf("General promotion","Bus Route","Bus Operator / Book Bus"),when(my.optString("category","general")){"bus_route"->1;"bus_operator"->2;else->0})
  val poster=adField("Poster image URL","https://...",my.optString("poster_url"))
  posterUrlField=poster
  add(button("Upload poster photo",false){posterPicker.launch("image/*")})
  add(text("Recommended: 1080 × 1350 px (4:5), JPG, PNG or WebP, maximum 8 MB.",12).apply{setTextColor(0xff64748b.toInt())})
  val preview=ImageView(this).apply{adjustViewBounds=true;scaleType=ImageView.ScaleType.FIT_CENTER;visibility=if(poster.text.isBlank())View.GONE else View.VISIBLE;setBackgroundColor(0xfff1f5f9.toInt());setPadding(dp(8),dp(8),dp(8),dp(8))}
  posterPreview=preview
  add(preview)
  if(poster.text.isNotBlank())task{runCatching{withContext(kotlinx.coroutines.Dispatchers.IO){java.net.URL(poster.text.toString()).openStream().use{android.graphics.BitmapFactory.decodeStream(it)}}}.getOrNull()?.let{preview.setImageBitmap(it)}}
  if(poster.text.isNotBlank())add(button("Delete uploaded poster",false){confirm("Delete the saved campaign poster and clear its URL?"){task{api.deleteAdPoster(poster.text.toString().trim());poster.setText("");preview.setImageDrawable(null);preview.visibility=View.GONE;uploadedPosterBytes=null;toast("Poster deleted and campaign URL cleared.")}}})
  val startDate=adField("Campaign start date (YYYY-MM-DD)","2026-11-22",my.optString("start_date"))
  val endDate=adField("Campaign end date (YYYY-MM-DD)","2026-12-31",my.optString("end_date"))
  val delay=adSpinner("Close button delay",listOf("2 seconds","3 seconds","4 seconds"),(my.optInt("close_delay",3)-2).coerceIn(0,2))
  sectionTitle("Campaign actions","Choose which action buttons appear with the poster.")
  var checkRoute=my.optBoolean("check_route_enabled",false)
  var call=my.optBoolean("call_enabled",false)
  var link=my.optBoolean("link_enabled",false)
  adToggle("Show route / booking button",checkRoute){checkRoute=it}
  val targetId=adField("Target listing ID (optional)","Route or operator ID",my.optString("target_id",my.optString("route_id")))
  val phone=adField("Call phone number","+91...",my.optString("phone"))
  adToggle("Show Call button",call){call=it}
  val url=adField("Custom link URL","https://...",my.optString("url"))
  val linkLabel=adField("Custom link button label","Learn more",my.optString("link_label","Learn more"))
  adToggle("Show custom link button",link){link=it}
  add(button("Save My Advertisement",true){task{
   val posterValue=poster.text.toString().trim()
   if(enabled&&posterValue.isBlank())throw IllegalStateException("Upload or enter a poster URL before enabling the campaign.")
   if(startDate.text.isNotBlank()&&endDate.text.isNotBlank()&&endDate.text.toString()<startDate.text.toString())throw IllegalStateException("Campaign end date must be on/after start date.")
   if(checkRoute&&targetId.text.isBlank())throw IllegalStateException("Choose a listed target for the selected campaign category.")
   if(call&&phone.text.isBlank())throw IllegalStateException("Enter a phone number for Call.")
   if(link&&!url.text.toString().trim().matches(Regex("^https?://.+",RegexOption.IGNORE_CASE)))throw IllegalStateException("Enter a valid http:// or https:// URL.")
   val m=JSONObject(my.toString()).put("enabled",enabled).put("category",listOf("general","bus_route","bus_operator")[category.selectedItemPosition]).put("poster_url",poster.text.toString().trim()).put("start_date",startDate.text.toString().trim()).put("end_date",endDate.text.toString().trim()).put("close_delay",delay.selectedItemPosition+2).put("check_route_enabled",checkRoute).put("target_id",targetId.text.toString().trim()).put("route_id",targetId.text.toString().trim()).put("call_enabled",call).put("phone",phone.text.toString().trim()).put("link_enabled",link).put("url",url.text.toString().trim()).put("link_label",linkLabel.text.toString().trim())
   adConfig.put("my",m);api.saveAdSettings(adConfig);toast("My Advertisement settings saved")
  }})
  add(View(this).apply{setBackgroundColor(0xffe2e8f0.toInt())})
  sectionTitle("Start.io Ads","Configure the Start.io app ID and decide where ad placements may appear.")
  val appId=adField("Start.io App ID","Enter Start.io App ID",start.optString("app_id"))
  var banner=start.optBoolean("banner_enabled",false);var mainBanner=start.optBoolean("main_banner_enabled",false);var bookBanner=start.optBoolean("book_bus_banner_enabled",false)
  var native=start.optBoolean("native_enabled",false);var resultsNative=start.optBoolean("results_native_enabled",false);var full=start.optBoolean("fullscreen_enabled",false)
  adToggle("Enable banner ads",banner){banner=it}
  adToggle("Main screen banner",mainBanner){mainBanner=it}
  adToggle("Book Bus screen banner",bookBanner){bookBanner=it}
  adToggle("Enable native ads",native){native=it}
  adToggle("Native ads in search results",resultsNative){resultsNative=it}
  adToggle("Enable full-screen ads",full){full=it}
  val busEvery=adField("Show full-screen ad after how many bus actions","4",start.optInt("bus_every",4).toString())
  val fullEvery=adField("Full-screen frequency counter","5",start.optInt("fullscreen_every",5).toString())
  add(text("Note: These settings only configure placements. Ads will serve only after the Android app build includes the matching Start.io SDK and placement IDs.",12).apply{setTextColor(0xff9a5b13.toInt())})
  add(button("Save Start.io Settings",true){task{
   val st=JSONObject(start.toString()).put("app_id",appId.text.toString().trim()).put("banner_enabled",banner).put("main_banner_enabled",mainBanner).put("book_bus_banner_enabled",bookBanner).put("native_enabled",native).put("results_native_enabled",resultsNative).put("fullscreen_enabled",full).put("bus_every",busEvery.text.toString().toIntOrNull()?.coerceIn(1,100)?:4).put("fullscreen_every",fullEvery.text.toString().toIntOrNull()?.coerceIn(1,100)?:5)
   adConfig.put("startio",st);api.saveAdSettings(adConfig);toast("Start.io settings saved")
  }})
  add(text("Last saved configuration is stored in Supabase and shared with the Web Admin.",11).apply{setTextColor(0xff64748b.toInt())})
 }
 private fun analyticsTab(){
  sectionTitle("Analytics & Stats","Live report from recorded app events in Supabase. Select a period and refresh.")
  val period=Spinner(this);period.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("Today","Last 7 days","Last 30 days","All recorded"));add(text("Report period",13,true));add(period)
  add(button("↻ Refresh analytics"){task{renderAnalytics(period.selectedItemPosition)}})
  task{renderAnalytics(1)}
 }
 private fun renderAnalytics(period:Int){
  task{
   val all=api.analyticsEvents()
   val now=System.currentTimeMillis()
   val days=when(period){0->1;1->7;2->30;else->0}
   val cutoff=if(days==0)0L else now-days*86400000L
   val allRows=mutableListOf<JSONObject>()
   for(i in 0 until all.length()){val row=all.optJSONObject(i);if(row!=null)allRows.add(row)}
   val events=mutableListOf<JSONObject>()
   for(row in allRows){val time=runCatching{java.time.Instant.parse(row.optString("created_at")).toEpochMilli()}.getOrDefault(0L);if(time>=cutoff)events.add(row)}
   val installs=allRows.filter{it.optString("event_name")=="app_install"}.map{it.optString("session_id")}.distinct().size
   val sessions=events.map{it.optString("session_id")}.filter{it.isNotBlank()}.distinct().size
   fun count(name:String):Int=events.count{it.optString("event_name")==name}
   val busSearch=count("bus_route_search")+count("bus_stand_search")
   val detail=count("route_detail_view")
   val appOpen=count("app_open")
   val grid=GridLayout(this).apply{columnCount=2}
   val stats: List<Pair<String,String>> = listOf("App installations" to installs.toString(),"App sessions" to sessions.toString(),"App opens" to appOpen.toString(),"Bus searches" to busSearch.toString(),"Route details viewed" to detail.toString(),"Recorded events" to events.size.toString())
   for(i in stats.indices){val p=stats[i];val c=LinearLayout(this).apply{orientation=1;setPadding(dp(13),dp(12),dp(13),dp(12));background=GradientDrawable().apply{setColor(0xfff4f7fb.toInt());cornerRadius=dp(12).toFloat();setStroke(dp(1),0xffdce5ef.toInt())};addView(text(p.second,24,true).apply{setTextColor(0xff245b8f.toInt())});addView(text(p.first,12,true).apply{setTextColor(0xff475569.toInt())})};grid.addView(c,GridLayout.LayoutParams().apply{width=0;columnSpec=GridLayout.spec(i%2,1f);rowSpec=GridLayout.spec(i/2);setMargins(dp(4),dp(4),dp(4),dp(4))})}
   body?.removeViews(7,(body?.childCount?:7)-7);add(grid)
   sectionTitle("Activity by event")
   val types=events.groupingBy{it.optString("event_name")}.eachCount().entries.sortedByDescending{it.value}
   if(types.isEmpty())add(text("No recorded events in this period.",13)) else for(entry in types){add(panelCard(entry.key.replace('_',' ').replaceFirstChar{c->c.uppercase()},entry.value.toString()+" events"){})}
   sectionTitle("Most viewed routes")
   val routeCounts=mutableMapOf<String,Int>();val standCounts=mutableMapOf<String,Int>()
   for(row in events){if(row.optString("event_name")=="route_detail_view"){val id=row.optJSONObject("properties")?.optString("route_id").orEmpty();if(id.isNotBlank())routeCounts[id]=(routeCounts[id]?:0)+1};if(row.optString("event_name")=="bus_stand_search"){val id=row.optJSONObject("properties")?.optString("stand_id").orEmpty();if(id.isNotBlank())standCounts[id]=(standCounts[id]?:0)+1}}
   if(routeCounts.isEmpty())add(text("No route-detail events recorded.",12)) else for(entry in routeCounts.entries.sortedByDescending{it.value}.take(8)){add(panelCard(entry.key,entry.value.toString()+" views"){})}
   sectionTitle("Most searched bus stands")
   if(standCounts.isEmpty())add(text("No bus-stand searches recorded.",12)) else for(entry in standCounts.entries.sortedByDescending{it.value}.take(8)){add(panelCard(entry.key,entry.value.toString()+" searches"){})}
   sectionTitle("Data coverage")
   val first=allRows.lastOrNull()?.optString("created_at").orEmpty()
   add(text("Available records: "+allRows.size+" events · First recorded: "+first.take(10)+" · Latest: "+(allRows.firstOrNull()?.optString("created_at")?.take(10)?: "—"),11))
   add(text("Ad earnings are not included: Start.io revenue reporting/API has not been connected. Install counts represent recorded app_install IDs and may count reinstalls.",11).apply{setTextColor(0xff64748b.toInt())})
  }
 }
 private fun placeName(p:PlaceRow):String{val n=JSONObject(p.names);return n.optString("en").ifBlank{n.optString("hi").ifBlank{n.optString("pa").ifBlank{p.id}}}}
 private fun normalizePlaceText(value:String)=value.trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+")," ")
 private fun resolvePlaceByName(value:String,places:List<PlaceRow>):PlaceRow?{
  val wanted=normalizePlaceText(value)
  if(wanted.isBlank())return null
  return places.firstOrNull{p->
   val names=JSONObject(p.names);val payload=JSONObject(p.payload)
   val candidates=mutableListOf(p.id,names.optString("en"),names.optString("hi"),names.optString("pa"),placeName(p))
   val aliases=payload.optJSONArray("aliases")
   if(aliases!=null)for(i in 0 until aliases.length())candidates.add(aliases.optString(i))
   candidates.any{it.isNotBlank()&&normalizePlaceText(it)==wanted}
  }
 }
 private fun routeList(){screen(tr("Route List","रूट सूची","ਰੂਟ ਸੂਚੀ"));val q=input("Search routes");add(q);task{prepareData();showRoutes("")};q.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){lifecycleScope.launch{showRoutes(s.toString())}};override fun afterTextChanged(e:android.text.Editable?){}})}
 private suspend fun showRoutes(q:String){val all=dao.allRoutes();val places=dao.allPlaces();val rows=all.filter{r->val j=JSONObject(r.payload);val names=mutableListOf<String>(r.id,r.fromId,r.toId,placeName(places.firstOrNull{it.id==r.fromId}?:PlaceRow(r.fromId,"place","{}")),placeName(places.firstOrNull{it.id==r.toId}?:PlaceRow(r.toId,"place","{}")));val a=j.optJSONArray("stops")?:JSONArray();for(i in 0 until a.length()){val id=a.optJSONObject(i)?.optString("place")?:a.optString(i);names.add(id);places.firstOrNull{it.id==id}?.let{names.add(placeName(it))}};q.isBlank()||names.any{it.contains(q,true)}||j.optString("service").contains(q,true)};body?.removeViews(1,(body?.childCount?:1)-1);add(countLabel(rows.size,"routes"));if(rows.isEmpty())add(text("No matching routes",14));rows.forEach{r->val j=JSONObject(r.payload);val a=placeName(places.firstOrNull{it.id==r.fromId}?:PlaceRow(r.fromId,"place","{}"));val b=placeName(places.firstOrNull{it.id==r.toId}?:PlaceRow(r.toId,"place","{}"));add(panelCard("$a  →  $b",listOf(r.time?.let{"Time: $it"},j.optString("service").takeIf{it.isNotBlank()}).filterNotNull().joinToString("  ·  ")){routeDetail(r)})}}
 private fun routeDetail(r:RouteRow){
  task{
   val j=JSONObject(r.payload);val places=dao.allPlaces()
   val from=placeName(places.firstOrNull{it.id==r.fromId}?:PlaceRow(r.fromId,"place","{}"))
   val to=placeName(places.firstOrNull{it.id==r.toId}?:PlaceRow(r.toId,"place","{}"))
   val stops=mutableListOf<Pair<String,String>>();stops.add(r.fromId to j.optString("time"))
   val arr=j.optJSONArray("stops")?:JSONArray();for(i in 0 until arr.length()){val x=arr.optJSONObject(i);if(x!=null)stops.add(x.optString("place") to x.optString("time"))}
   stops.add(r.toId to j.optString("arrive").takeUnless{it.isBlank()||it=="null"}.orEmpty())
   screen("$from → $to",true)
   val hero=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(14),dp(16),dp(14));background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(0xff0c2038.toInt(),0xff153456.toInt(),0xff255589.toInt())).apply{cornerRadius=dp(16).toFloat()}}
   hero.addView(TextView(this).apply{text="$from → $to";textSize=20f;typeface=android.graphics.Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);setPadding(0,0,0,dp(6))})
   val service=j.optString("service").ifBlank{j.optString("routeName")}
   if(service.isNotBlank())hero.addView(TextView(this).apply{text=service;textSize=14f;setTextColor(0xffdce9f8.toInt())})
   if(r.time!=null)hero.addView(TextView(this).apply{text="Departs at "+r.time;textSize=14f;setTextColor(0xffffdf9b.toInt());setPadding(0,dp(5),0,0)})
   add(hero)
   val timeline=LinearLayout(this).apply{orientation=1;setPadding(dp(12),dp(12),dp(12),dp(12));background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=dp(14).toFloat();setStroke(dp(1),0xffe6dfd4.toInt())}}
   stops.forEachIndexed{idx,p->
    val place=places.firstOrNull{it.id==p.first}
    val name=place?.let{placeName(it)}?:p.first
    val row=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(7),0,dp(7))}
    val nodeColor=when(idx){0->0xff137a55.toInt();stops.lastIndex->0xffe05a1e.toInt();else->0xff255589.toInt()}
    val node=TextView(this).apply{text=(idx+1).toString();gravity=Gravity.CENTER;textSize=12f;typeface=android.graphics.Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE);background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(nodeColor)}}
    row.addView(node,LinearLayout.LayoutParams(dp(30),dp(30)).apply{marginEnd=dp(12)})
    row.addView(TextView(this).apply{text=name;textSize=if(idx==0||idx==stops.lastIndex)17f else 15f;typeface=if(idx==0||idx==stops.lastIndex)android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT;setTextColor(nodeColor)},LinearLayout.LayoutParams(0,-2,1f))
    if(p.second.isNotBlank())row.addView(TextView(this).apply{text=p.second;textSize=13f;typeface=android.graphics.Typeface.MONOSPACE;setTextColor(nodeColor)})
    timeline.addView(row)
    if(idx<stops.lastIndex)timeline.addView(View(this).apply{setBackgroundColor(0xffd5cbbf.toInt())},LinearLayout.LayoutParams(dp(2),dp(12)).apply{leftMargin=dp(14)})
   }
   add(timeline)
   add(button("Correct this route"){editRoute(r)})
   val actions=LinearLayout(this).apply{orientation=0}
   actions.addView(button("Copy Route"){editRoute(r,true)},LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=dp(4)})
   actions.addView(button("Return Route"){editRoute(r,false,true)},LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=dp(4)})
   add(actions)
   add(button("Delete route",false){confirm("Move this route to Recycle Bin?"){task{val res=publishChange("route",r.id,"delete",j);toast("Published successfully · revision "+res.optLong("revision"));prepareData();routeList()}}})
  }
 }
 private suspend fun publishChange(type:String,id:String,op:String,payload:JSONObject)=api.publish(JSONObject().put("action","publish").put("changes",JSONArray().put(JSONObject().put("entity_type",type).put("entity_id",id).put("operation",op).put("payload",payload))))

 private fun correctionRouteList(){routeListForCorrection()}
 private fun routeListForCorrection(){screen("Correction Route");val q=input("Search routes");add(q);task{prepareData();renderRouteRows(q.text.toString(),true)};q.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){lifecycleScope.launch{renderRouteRows(s.toString(),true)}};override fun afterTextChanged(e:android.text.Editable?){}})}
 private suspend fun renderRouteRows(q:String,correction:Boolean){val all=dao.allRoutes();val places=dao.allPlaces();val rows=all.filter{r->val j=JSONObject(r.payload);val names=mutableListOf<String>(r.id,r.fromId,r.toId,placeName(places.firstOrNull{it.id==r.fromId}?:PlaceRow(r.fromId,"place","{}")),placeName(places.firstOrNull{it.id==r.toId}?:PlaceRow(r.toId,"place","{}")));val a=j.optJSONArray("stops")?:JSONArray();for(i in 0 until a.length()){val id=a.optJSONObject(i)?.optString("place")?:a.optString(i);names.add(id);places.firstOrNull{it.id==id}?.let{names.add(placeName(it))}};q.isBlank()||names.any{it.contains(q,true)}||j.optString("service").contains(q,true)};body?.removeViews(1,(body?.childCount?:1)-1);add(countLabel(rows.size,"routes"));rows.forEach{r->val a=placeName(places.firstOrNull{it.id==r.fromId}?:PlaceRow(r.fromId,"place","{}"));val b=placeName(places.firstOrNull{it.id==r.toId}?:PlaceRow(r.toId,"place","{}"));add(panelCard(a+" → "+b,r.time?:("No time · "+r.id)){if(correction)editRoute(r)else routeDetail(r)})}}
 private fun editRoute(old:RouteRow?,copy:Boolean=false,reverse:Boolean=false,prefill:List<JSONObject>?=null,reviewRequest:Pair<String,JSONObject>?=null,prefillService:String?=null){screen(if(old==null)"Add Route" else "Correction Route");task{prepareData();val places=dao.allPlaces();if(places.size<2)throw IllegalStateException("Add at least two bus stops first.");val oldJson=old?.let{JSONObject(it.payload)};val drafts=mutableListOf<JSONObject>();if(prefill!=null){drafts.addAll(prefill)}else if(oldJson!=null){val ids=mutableListOf<String>();ids.add(if(reverse)oldJson.optString("to") else oldJson.optString("from"));val arr=oldJson.optJSONArray("stops")?:JSONArray();for(i in 0 until arr.length()){ids.add(arr.optJSONObject(i)?.optString("place")?:arr.optString(i))};ids.add(if(reverse)oldJson.optString("from") else oldJson.optString("to"));if(reverse)ids.reverse();ids.forEachIndexed{idx,id->val p=places.firstOrNull{it.id==id};val tm=if(copy||reverse)"" else when(idx){0->oldJson.optString("time").takeUnless{it=="null"}.orEmpty();ids.lastIndex->oldJson.optString("arrive").takeUnless{it=="null"}.orEmpty();else->arr.optJSONObject(idx-1)?.optString("time")?.takeUnless{it=="null"}.orEmpty()};drafts.add(JSONObject().put("place_id",id).put("name",p?.let{placeName(it)}?:id).put("time",tm))}}else{drafts.add(newStopDraft());drafts.add(newStopDraft())}
 val service=input("Service / bus name (optional)",prefillService ?: (if(copy)"" else reviewRequest?.second?.optString("service_name")?.takeIf{it.isNotBlank()}?:oldJson?.optString("service")?:""));val box=LinearLayout(this).apply{orientation=1};add(text("Add times where known. At least one stop must have a time.",13));add(box)
 fun renderRows(){
  box.removeAllViews()
  val actionStyle=GradientDrawable().apply{setColor(0xffdce9f8.toInt());setStroke(dp(1),0xffc0d7f0.toInt());cornerRadius=dp(14).toFloat()}
  val addStart=button("＋ Add Start"){drafts.add(0,newStopDraft());renderRows()}
  addStart.background=actionStyle;box.addView(addStart,LinearLayout.LayoutParams(-1,dp(44)).apply{bottomMargin=dp(10)})
  drafts.forEachIndexed{idx,d->
   val first=idx==0;val last=idx==drafts.lastIndex
   val row=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(6),dp(6),dp(6));minimumHeight=dp(58);background=GradientDrawable().apply{setColor(0xfff7f4ee.toInt());setStroke(dp(1),0xffe6dfd4.toInt());cornerRadius=dp(16).toFloat()}}
   val badgeColor=if(first)0xff137a55.toInt() else if(last)0xffe05a1e.toInt() else 0xffdce9f8.toInt()
   val badgeText=if(first||last)Color.WHITE else 0xff153456.toInt()
   row.addView(TextView(this).apply{text=(idx+1).toString();gravity=Gravity.CENTER;textSize=12f;typeface=android.graphics.Typeface.MONOSPACE;setTextColor(badgeText);background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(badgeColor)}},LinearLayout.LayoutParams(dp(28),dp(28)).apply{marginEnd=dp(8)})
   val name=AutoCompleteTextView(this).apply{hint=if(first)"Start stop" else if(last)"Destination" else "Stop name";threshold=1;textSize=14f;setSingleLine(true);setPadding(dp(2),0,dp(4),0);setBackgroundColor(Color.TRANSPARENT);setText(d.optString("name"),false)}
   val choices=places.filter{p->drafts.none{it!==d&&it.optString("place_id")==p.id}}
   val choiceNames=choices.map{placeName(it)}
   name.setAdapter(ArrayAdapter(this,android.R.layout.simple_dropdown_item_1line,choiceNames))
   name.setOnItemClickListener{parent,_,pos,_->
    val selectedName=parent.getItemAtPosition(pos)?.toString().orEmpty()
    val p=choices.firstOrNull{placeName(it)==selectedName}
    if(p==null){d.put("place_id",JSONObject.NULL).put("name",selectedName)}
    else d.put("place_id",p.id).put("name",placeName(p))
   }
   name.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){if(d.optString("name")!=s.toString())d.put("place_id",JSONObject.NULL).put("name",s.toString())};override fun afterTextChanged(e:android.text.Editable?){}})
   row.addView(name,LinearLayout.LayoutParams(0,dp(44),1f))
   val currentTime=d.optString("time").takeUnless{it.isBlank()||it=="null"}
   val time=LinearLayout(this).apply{
    orientation=0;gravity=Gravity.CENTER
    setPadding(dp(5),dp(4),dp(5),dp(4))
    background=GradientDrawable().apply{setColor(if(currentTime==null)0xfff4f8fd.toInt() else 0xffeaf2fb.toInt());setStroke(dp(1),if(currentTime==null)0xffa9c3df.toInt() else 0xffb9cfe6.toInt());cornerRadius=dp(12).toFloat()}
    if(currentTime==null)addView(ClockGlyphView(this@MainActivity),LinearLayout.LayoutParams(dp(27),dp(27)))
    else addView(TextView(this@MainActivity).apply{text=currentTime;textSize=13f;typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL);gravity=Gravity.CENTER;setTextColor(0xff153456.toInt())},LinearLayout.LayoutParams(-1,-1))
    contentDescription=currentTime?: "Set time"
    setOnClickListener{val cal=java.util.Calendar.getInstance();val parts=(currentTime?: "").split(":");val h=parts.getOrNull(0)?.toIntOrNull()?:cal.get(11);val m=parts.getOrNull(1)?.toIntOrNull()?:cal.get(12);android.app.TimePickerDialog(this@MainActivity,{_,hh,mm->val value=String.format(java.util.Locale.ROOT,"%02d:%02d",hh,mm);d.put("time",value);renderRows()},h,m,true).apply{setButton(-3,"Clear"){_,_->d.put("time","");renderRows()}}.show()}
   }
   row.addView(time,LinearLayout.LayoutParams(dp(74),dp(44)).apply{marginStart=dp(5)})
   row.addView(TextView(this).apply{text="+";textSize=20f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=GradientDrawable().apply{setColor(0xff137a55.toInt());cornerRadius=dp(10).toFloat()};setOnClickListener{drafts.add(idx+1,newStopDraft());renderRows()}},LinearLayout.LayoutParams(dp(34),dp(36)).apply{marginStart=dp(5)})
   row.addView(TextView(this).apply{text="⋮";textSize=20f;gravity=Gravity.CENTER;setTextColor(0xff535e6e.toInt());setOnClickListener{AlertDialog.Builder(this@MainActivity).setItems(arrayOf("Move up","Move down","Remove")){_,which->when(which){0->if(idx>0){val x=drafts.removeAt(idx);drafts.add(idx-1,x)};1->if(idx<drafts.lastIndex){val x=drafts.removeAt(idx);drafts.add(idx+1,x)};2->if(drafts.size>2)drafts.removeAt(idx)else toast("At least two stops required")};renderRows()}.show()}},LinearLayout.LayoutParams(dp(28),dp(38)).apply{marginStart=dp(2)})
   box.addView(row,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,dp(4))})
  }
  val addEnd=button("＋ Add Stop at End"){drafts.add(newStopDraft());renderRows()};addEnd.background=actionStyle;box.addView(addEnd,LinearLayout.LayoutParams(-1,dp(44)).apply{topMargin=dp(10)})
 }
 renderRows();add(text("Service / Bus Name",13,true));add(service);add(button("Preview Route  →"){if(drafts.size<2||drafts.any{it.optString("name").isBlank()}||drafts.none{it.optString("time").matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))}){toast("Enter all stops and at least one valid time");return@button};screen("Route Preview",true,"Preview the complete route before publishing.")
 val previewCard=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(14),dp(16),dp(14));background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(0xff0c2038.toInt(),0xff153456.toInt(),0xff255589.toInt())).apply{cornerRadius=dp(16).toFloat()}}
 previewCard.addView(TextView(this).apply{text=drafts.first().optString("name")+" → "+drafts.last().optString("name");textSize=19f;typeface=android.graphics.Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)})
 if(service.text.isNotBlank())previewCard.addView(TextView(this).apply{text=service.text.toString();textSize=14f;setTextColor(0xffdce9f8.toInt())})
 add(previewCard)
 drafts.forEachIndexed{idx,d->val line=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=dp(12).toFloat();setStroke(dp(1),0xffe6dfd4.toInt())}}
  val color=if(idx==0)0xff137a55.toInt() else if(idx==drafts.lastIndex)0xffe05a1e.toInt() else 0xff255589.toInt()
  line.addView(TextView(this).apply{text=(idx+1).toString();gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(color)}},LinearLayout.LayoutParams(dp(28),dp(28)).apply{marginEnd=dp(10)})
  line.addView(text(d.optString("name"),15,true),LinearLayout.LayoutParams(0,-2,1f))
  line.addView(text(d.optString("time").ifBlank{"—"},12,true).apply{setTextColor(color)})
  add(line)
 }
 add(toolbarAction("‹  Back to Edit"){editRoute(old,copy,reverse,drafts.map{JSONObject(it.toString())},reviewRequest,service.text.toString())})
 val stableRouteId=old?.id?.takeIf{!copy&&!reverse}?:("r_"+java.util.UUID.randomUUID().toString().replace("-",""))
 add(button("Publish Route  ✓"){publishDraftRoute(old,copy,reverse,drafts,service.text.toString(),reviewRequest,stableRouteId)})})
 }}
 private fun newStopDraft()=JSONObject().put("place_id",JSONObject.NULL).put("name","").put("time","")
 private fun publishDraftRoute(old:RouteRow?,copy:Boolean,reverse:Boolean,drafts:List<JSONObject>,service:String,reviewRequest:Pair<String,JSONObject>?=null,stableRouteId:String){
  if(routePublishInFlight)return
  routePublishInFlight=true
  task{
   try{
    val places=dao.allPlaces()
    val re=Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    if(drafts.size<2)throw IllegalStateException("At least two stops are required.")
    if(drafts.any{it.optString("name").isBlank()})throw IllegalStateException("Enter a name for every stop.")
    val createdPlaces=linkedMapOf<String,JSONObject>()
    val resolved=mutableListOf<PlaceRow>()
    drafts.forEachIndexed{index,d->
     val stopName=d.optString("name").trim()
     val existing=resolvePlaceByName(stopName,places)
      ?:places.firstOrNull{it.id==d.optString("place_id")}
     val place=existing?:run{
      val normalized=normalizePlaceText(stopName)
      val duplicate=resolved.firstOrNull{p->
       val names=JSONObject(p.names);listOf(names.optString("en"),names.optString("hi"),names.optString("pa"),p.id).any{it.isNotBlank()&&normalizePlaceText(it)==normalized}
      }
      if(duplicate!=null)duplicate else{
       val retryId=d.optString("place_id").takeIf{it.isNotBlank()&&it!="null"}
       val id=retryId?:("place_"+stopName.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]+"),"_").trim('_').take(65).ifBlank{"place"}+"_"+java.util.UUID.randomUUID().toString().replace("-","").take(8))
       val language=when{
        stopName.any{Character.UnicodeScript.of(it.code)==Character.UnicodeScript.DEVANAGARI}->"hi"
        stopName.any{Character.UnicodeScript.of(it.code)==Character.UnicodeScript.GURMUKHI}->"pa"
        else->"en"
       }
       val names=JSONObject().put(language,stopName)
       val placePayload=JSONObject().put("id",id).put("kind","place").put("name",names).put("aliases",JSONArray())
       createdPlaces[id]=placePayload
       d.put("place_id",id)
       PlaceRow(id,"place",names.toString(),payload=placePayload.toString())
      }
     }
     d.put("place_id",place.id)
     resolved.add(place)
    }
    val ids=resolved.map{it.id}
    if(ids.distinct().size<2)throw IllegalStateException("A route must contain at least two different bus stops.")
    if(drafts.none{re.matches(it.optString("time"))})throw IllegalStateException("At least one valid time is required.")
    val payload=JSONObject(old?.payload?:"{}").put("id",stableRouteId).put("from",ids.first()).put("to",ids.last())
     .put("time",drafts.first().optString("time").takeIf{re.matches(it)}?:JSONObject.NULL)
     .put("arrive",drafts.last().optString("time").takeIf{re.matches(it)}?:JSONObject.NULL)
     .put("service",service)
     .put("stops",JSONArray().apply{resolved.drop(1).dropLast(1).forEachIndexed{idx,p->put(JSONObject().put("place",p.id).put("time",drafts[idx+1].optString("time").takeIf{v->re.matches(v)}?:JSONObject.NULL))}})
    val changes=JSONArray()
    createdPlaces.forEach{(id,placePayload)->changes.put(JSONObject().put("entity_type","place").put("entity_id",id).put("operation","upsert").put("payload",placePayload))}
    changes.put(JSONObject().put("entity_type","route").put("entity_id",stableRouteId).put("operation","upsert").put("payload",payload))
    val res=api.publish(JSONObject().put("action","publish").put("changes",changes))
    var approvalWarning=""
    if(reviewRequest!=null){
     try{api.publish(JSONObject().put("action","review-status").put("table",reviewRequest.first).put("id",reviewRequest.second.optString("id")).put("status","approved"))}
     catch(e:Exception){approvalWarning=" Route is published, but request approval failed. Reopen Review Center; do not publish the route again."}
    }
    toast((if(reviewRequest!=null)"Route published" else "Published")+" · revision "+res.optLong("revision")+approvalWarning)
    try{prepareData()}catch(_:Exception){}
    if(reviewRequest!=null&&approvalWarning.isBlank())reviews()else if(reviewRequest==null)home()
   }finally{routePublishInFlight=false}
  }
 }
 private fun editReviewRoute(table:String,item:JSONObject){
  task{
   prepareData()
   val places=dao.allPlaces()
   val isCorrection=table=="correction_submissions"
   val raw=item.optJSONArray(if(isCorrection)"proposed_stops" else "stops")?:JSONArray()
   val drafts=mutableListOf<JSONObject>()
   fun addDraft(pidValue:String,nameValue:String,timeValue:String){
    val pid=pidValue.takeUnless{it.isBlank()||it=="null"}.orEmpty()
    val name=nameValue.takeUnless{it=="null"}.orEmpty()
    fun normalized(value:String)=value.trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+")," ")
    val place=places.firstOrNull{it.id==pid}?:places.firstOrNull{p->
     val names=JSONObject(p.names);val payload=JSONObject(p.payload);val aliases=payload.optJSONArray("aliases")
     val candidates=mutableListOf(names.optString("en"),names.optString("hi"),names.optString("pa"))
     if(aliases!=null)for(i in 0 until aliases.length())candidates.add(aliases.optString(i))
     candidates.any{it.isNotBlank()&&normalized(it)==normalized(name)}
    }
    val display=place?.let{placeName(it)}?:name.ifBlank{pid}
    if(display.isNotBlank()&&display!="null")drafts.add(JSONObject().put("place_id",place?.id?:JSONObject.NULL).put("name",display).put("time",timeValue.takeUnless{it=="null"}.orEmpty()))
   }
   for(i in 0 until raw.length()){
    val o=raw.optJSONObject(i)
    if(o==null)addDraft(raw.optString(i),raw.optString(i),"")
    else {
     val pid=o.optString("place").takeUnless{it.isBlank()||it=="null"}?:o.optString("place_id").takeUnless{it.isBlank()||it=="null"}.orEmpty()
     val names=o.optJSONObject("names")?:JSONObject()
     val name=o.optString("name").ifBlank{o.optString("place_name").ifBlank{names.optString("en").ifBlank{names.optString("hi").ifBlank{names.optString("pa")}}}}
     addDraft(pid,name,o.optString("time"))
    }
   }
   val old=if(isCorrection)dao.allRoutes().firstOrNull{it.id==item.optString("route_id")}else null
   if(drafts.size<2&&old!=null){
    val j=JSONObject(old.payload);val ids=mutableListOf<String>();ids.add(j.optString("from"));val a=j.optJSONArray("stops")?:JSONArray()
    for(i in 0 until a.length()){val s=a.optJSONObject(i);if(s!=null)ids.add(s.optString("place"))}
    ids.add(j.optString("to"));drafts.clear()
    ids.forEachIndexed{idx,id->val p=places.firstOrNull{it.id==id};val tm=when(idx){0->j.optString("time").takeUnless{it=="null"}.orEmpty();ids.lastIndex->j.optString("arrive").takeUnless{it=="null"}.orEmpty();else->a.optJSONObject(idx-1)?.optString("time")?.takeUnless{it=="null"}.orEmpty()};addDraft(id,p?.let{placeName(it)}?:id,tm)}
   }
   if(drafts.size<2){
    val from=item.optString("from_text").ifBlank{item.optString("from_id")}
    val to=item.optString("to_text").ifBlank{item.optString("to_id")}
    if(drafts.isEmpty()&&from.isNotBlank())addDraft(item.optString("from_id"),from,item.optString("start_time"))
    if(drafts.size<2&&to.isNotBlank())addDraft(item.optString("to_id"),to,"")
   }
   if(drafts.size<2)throw IllegalStateException("This request does not contain enough route details to open the editor.")
   editRoute(old,prefill=drafts,reviewRequest=table to item)
  }
 }
 private fun placeList(){screen("Bus Stop List");val q=input("Search stops");add(q);task{prepareData();showPlaces("")};q.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){lifecycleScope.launch{showPlaces(s.toString())}};override fun afterTextChanged(e:android.text.Editable?){}})}
 private suspend fun showPlaces(q:String){val rows=dao.allPlaces().filter{it.id.contains(q,true)||it.names.contains(q,true)};body?.removeViews(1,(body?.childCount?:1)-1);add(countLabel(rows.size,"bus stops"));if(rows.isEmpty())add(text("No matching bus stops",14));rows.forEach{p->val names=JSONObject(p.names);val meta=listOf(names.optString("hi"),names.optString("pa"),"ID: ${p.id}").filter{it.isNotBlank()}.joinToString("  ·  ");add(panelCard(placeName(p),meta){screen(placeName(p));add(text("Bus stop details",13,true));add(text("English: "+names.optString("en").ifBlank{"—"}));add(text("Hindi: "+names.optString("hi").ifBlank{"—"}));add(text("Punjabi: "+names.optString("pa").ifBlank{"—"}));add(text("Place ID: "+p.id,12));add(button("Correction / Edit Stop"){editPlace(p)});add(button("Delete this stop",false){task{val used=dao.allRoutes().filter{r->val j=JSONObject(r.payload);j.optString("from")==p.id||j.optString("to")==p.id||(j.optJSONArray("stops")?.let{a->(0 until a.length()).any{a.optJSONObject(it)?.optString("place")==p.id}}?:false)};if(used.isNotEmpty())toast("Cannot delete this stop: "+used.joinToString{it.id})else confirm("Move this stop to Recycle Bin?"){task{val payload=JSONObject(p.payload);val res=publishChange("place",p.id,"delete",payload);toast("Published successfully · revision "+res.optLong("revision"));prepareData();placeList()}}}})})}}
 private fun editPlace(old:PlaceRow?,prefillKind:Int?=null,prefillEn:String?=null,prefillHi:String?=null,prefillPa:String?=null){
  screen(if(old==null)"Add Bus Stop" else "Correction Bus Stop")
  add(text("Select place type",13,true));val kind=Spinner(this);val labels=listOf("Bus Stand","Village","City","Other Place")
  kind.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,labels)
  kind.setSelection(prefillKind ?: when(old?.kind){"stand"->0;"village"->1;"city"->2;"other"->3;else->3});add(kind)
  val n=JSONObject(old?.names?:"{}");val en=input("English name",prefillEn ?: n.optString("en"));val hi=input("Hindi name",prefillHi ?: n.optString("hi"));val pa=input("Punjabi name",prefillPa ?: n.optString("pa"))
  add(text("English",13,true));add(en);add(text("Hindi",13,true));add(hi);add(text("Punjabi",13,true));add(pa)
  add(button("Preview"){if(en.text.isBlank()&&hi.text.isBlank()&&pa.text.isBlank()){toast("Enter at least one name");return@button};screen("Bus Stop Preview");add(text(labels[kind.selectedItemPosition],12,true));add(text("English: "+en.text.toString().ifBlank{"—"},16,true));add(text("Hindi: "+hi.text.toString().ifBlank{"—"}));add(text("Punjabi: "+pa.text.toString().ifBlank{"—"}));add(button("Back to Edit"){editPlace(old,kind.selectedItemPosition,en.text.toString(),hi.text.toString(),pa.text.toString())});add(button("Publish"){savePlace(old,kind.selectedItemPosition,en.text.toString(),hi.text.toString(),pa.text.toString())})})
 }
 private fun savePlace(old:PlaceRow?,kindIndex:Int,en:String,hi:String,pa:String){task{
  if(en.isBlank()&&hi.isBlank()&&pa.isBlank())throw IllegalStateException("At least one name is required")
  val source=en.ifBlank{hi.ifBlank{pa}};val slug=source.lowercase().replace(Regex("[^a-z0-9]+"),"_").trim('_').take(65)
  val baseId=slug.ifBlank{"place"}
  val id=old?.id?:(baseId+"_"+java.util.UUID.randomUUID().toString().replace("-","").take(8))
  val payload=JSONObject(old?.payload?:"{}");val names=payload.optJSONObject("name")?:JSONObject()
  if(en.isNotBlank())names.put("en",en);if(hi.isNotBlank())names.put("hi",hi);if(pa.isNotBlank())names.put("pa",pa)
  val kind=listOf("stand","village","city","other")[kindIndex]
  payload.put("id",id).put("kind",kind).put("name",names)
  val res=publishChange("place",id,"upsert",payload);toast("Published successfully · revision "+res.optLong("revision"));prepareData();placeList()
 }}
 private var historyItems=mutableListOf<JSONObject>()
 private var historyHasMore=false
 private var historyState:JSONObject?=null
 private var historyList:LinearLayout?=null
 private var historyMonitorList:LinearLayout?=null
 private var historyStatus:TextView?=null
 private var historyLoadButton:Button?=null
 private var historyLoading=false
 private var historyMonitorLoading=false
 private var historyMonitorStatusValue="pending"
 private var historyMonitorState:JSONObject?=null
 private var historyMonitorError=""
 private var historyFilterOperation=""
 private var historyFilterType=""
 private var historyFilterSource=""
 private var historyFilterSearch=""
 private var historyFilterFrom=""
 private var historyFilterTo=""
 private var routePublishInFlight=false
 private fun history(reset:Boolean){
  if(reset){historyItems.clear();historyHasMore=false;historyState=null}
  screen("History",true,"Permanent record of published Place and Route changes")
  val actions=LinearLayout(this).apply{orientation=0}
  actions.addView(toolbarAction("↻  Refresh All"){history(true)},LinearLayout.LayoutParams(0,dp(42),1f).apply{marginEnd=dp(8)})
  actions.addView(toolbarAction("Latest first"){history(true)},LinearLayout.LayoutParams(0,dp(42),1f))
  add(actions)

  add(text("DEVICE SYNC MONITORING",15,true))
  add(text("Device sync health and failed changes reported by installed Bustime devices.",12).apply{setTextColor(0xff64748b.toInt())})
  val monitorBar=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
  val monitorStatus=Spinner(this)
  monitorStatus.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("Pending errors","All errors","Resolved","Superseded"))
  monitorStatus.setSelection(when(historyMonitorStatusValue){"all"->1;"resolved"->2;"superseded"->3;else->0})
  monitorBar.addView(monitorStatus,LinearLayout.LayoutParams(0,dp(46),1f).apply{marginEnd=dp(8)})
  monitorBar.addView(toolbarAction("Refresh Monitor"){historyMonitorStatusValue=listOf("pending","all","resolved","superseded")[monitorStatus.selectedItemPosition];loadHistoryMonitor()},LinearLayout.LayoutParams(0,dp(42),1f))
  add(monitorBar)
  historyMonitorList=LinearLayout(this).apply{orientation=1}
  add(historyMonitorList!!)

  add(text("FILTER HISTORY",15,true))
  fun filterSpinner(values:List<String>,selected:Int):Spinner{
   val s=Spinner(this);s.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,values);s.setSelection(selected.coerceIn(0,values.lastIndex));return s
  }
  val operation=filterSpinner(listOf("All actions","Add / Update","Delete"),when(historyFilterOperation){"upsert"->1;"delete"->2;else->0})
  val type=filterSpinner(listOf("All types","Route","Place"),when(historyFilterType){"route"->1;"place"->2;else->0})
  val source=filterSpinner(listOf("All sources","Web Admin","Android App","Legacy"),when(historyFilterSource){"web_admin"->1;"android_app"->2;"legacy"->3;else->0})
  add(text("Action",12,true));add(operation)
  add(text("Record type",12,true));add(type)
  add(text("Source",12,true));add(source)
  val search=input("Search by Route / Place ID",historyFilterSearch)
  add(text("Record ID",12,true));add(search)
  val from=input("YYYY-MM-DD",historyFilterFrom)
  val to=input("YYYY-MM-DD",historyFilterTo)
  add(text("From date",12,true));add(from)
  add(text("To date",12,true));add(to)
  val filterActions=LinearLayout(this).apply{orientation=0}
  filterActions.addView(button("Apply Filters",true){
   historyFilterOperation=listOf("","upsert","delete")[operation.selectedItemPosition]
   historyFilterType=listOf("","route","place")[type.selectedItemPosition]
   historyFilterSource=listOf("","web_admin","android_app","legacy")[source.selectedItemPosition]
   historyFilterSearch=search.text.toString().trim()
   historyFilterFrom=from.text.toString().trim()
   historyFilterTo=to.text.toString().trim()
   historyItems.clear();historyHasMore=false;historyState=null;loadHistoryPage(true)
  },LinearLayout.LayoutParams(0,dp(46),1f).apply{marginEnd=dp(6)})
  filterActions.addView(button("Clear Filters",false){
   historyFilterOperation="";historyFilterType="";historyFilterSource="";historyFilterSearch="";historyFilterFrom="";historyFilterTo=""
   history(true)
  },LinearLayout.LayoutParams(0,dp(46),1f).apply{marginStart=dp(6)})
  add(filterActions)

  historyStatus=text("Loading history…",13).apply{setTextColor(0xff64748b.toInt())}
  add(historyStatus!!)
  historyList=LinearLayout(this).apply{orientation=1}
  add(historyList!!)
  loadHistoryPage(reset)
  loadHistoryMonitor()
 }
 private fun loadHistoryPage(reset:Boolean){
  if(historyLoading)return
  historyLoading=true
  historyLoadButton?.isEnabled=false
  historyStatus?.text=if(reset)"Loading latest history…" else "Loading older history…"
  lifecycleScope.launch{
   try{
    val req=JSONObject().put("limit",200)
    if(!reset){
     val last=historyItems.lastOrNull()
     if(last!=null){
      val revision=last.optLong("revision")
      if(revision>0){
       req.put("before_revision",revision)
       req.put("before_entity_type",last.optString("entity_type"))
       req.put("before_entity_id",last.optString("entity_id"))
      }
     }
    }
    if(historyFilterOperation.isNotBlank())req.put("filter_operation",historyFilterOperation)
    if(historyFilterType.isNotBlank())req.put("filter_entity_type",historyFilterType)
    if(historyFilterSource.isNotBlank())req.put("filter_source",historyFilterSource)
    if(historyFilterSearch.isNotBlank())req.put("filter_search",historyFilterSearch)
    if(historyFilterFrom.isNotBlank())req.put("filter_from",historyFilterFrom)
    if(historyFilterTo.isNotBlank())req.put("filter_to",historyFilterTo)
    val result=api.history(req)
    val rows=result.optJSONArray("items")?:JSONArray()
    val known=historyItems.map{historyIdentity(it)}.toHashSet()
    for(i in 0 until rows.length()){
     val row=rows.getJSONObject(i)
     if(known.add(historyIdentity(row)))historyItems.add(row)
    }
    historyHasMore=result.optBoolean("has_more")
    historyState=result.optJSONObject("state")
    renderHistoryRows()
   }catch(e:Exception){
    historyStatus?.text="Could not load history: "+(e.message?: "Request failed")
    val retry=button("Retry"){loadHistoryPage(reset)}
    historyList?.removeAllViews()
    historyList?.addView(retry,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
   }finally{
    historyLoading=false
    historyLoadButton?.isEnabled=historyHasMore
   }
  }
 }
 private fun loadHistoryMonitor(){
  if(historyMonitorLoading)return
  historyMonitorLoading=true
  historyMonitorList?.removeAllViews()
  historyMonitorList?.addView(text("Loading device sync information…",12))
  lifecycleScope.launch{
   try{
    val result=api.history(JSONObject().put("action","sync-monitor").put("status",historyMonitorStatusValue))
    historyMonitorState=result
    historyMonitorError=""
   }catch(e:Exception){historyMonitorError=e.message?: "Request failed"}
   finally{historyMonitorLoading=false;renderHistoryMonitor()}
  }
 }
 private fun renderHistoryMonitor(){
  val list=historyMonitorList?:return
  list.removeAllViews()
  val state=historyMonitorState
  if(historyMonitorError.isNotBlank()){
   list.addView(text("Could not load Sync Monitoring: $historyMonitorError",12).apply{setTextColor(0xffb42318.toInt())})
   return
  }
  if(state==null){list.addView(text("No sync monitor data available.",12));return}
  val devices=state.optJSONArray("devices")?:JSONArray()
  val errors=state.optJSONArray("errors")?:JSONArray()
  val serverRevision=state.optJSONObject("state")?.optLong("revision")?:0L
  val pending=(0 until errors.length()).count{errors.optJSONObject(it)?.optString("status")=="pending"}
  val failed=(0 until devices.length()).count{!devices.optJSONObject(it)?.optString("last_error").isNullOrBlank()}
  list.addView(text("Devices: ${devices.length()}  ·  Pending errors: $pending  ·  Devices with errors: $failed  ·  Server revision: $serverRevision",12,true))
  list.addView(text("DEVICES",12,true).apply{setPadding(0,dp(8),0,dp(3))})
  if(devices.length()==0)list.addView(text("No device sync reports received yet.",12))
  for(i in 0 until devices.length()){
   val d=devices.optJSONObject(i)?:continue
   val row=LinearLayout(this).apply{orientation=1;setPadding(dp(10),dp(8),dp(10),dp(8));background=GradientDrawable().apply{setColor(0xfff5f7fa.toInt());cornerRadius=dp(10).toFloat()}}
   row.addView(text("Device: "+d.optString("device_id","—"),12,true))
   row.addView(text("App: "+d.optString("app_version","—")+"  ·  Last sync revision: "+d.optLong("last_success_revision"),11))
   row.addView(text("Last seen: "+historyDate(d.optString("last_seen_at"))+"  ·  Error: "+d.optString("last_error").ifBlank{"—"},11))
   list.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(6)})
  }
  list.addView(text("FAILED CHANGE RECORDS",12,true).apply{setPadding(0,dp(8),0,dp(3))})
  if(errors.length()==0)list.addView(text("No matching sync errors.",12))
  for(i in 0 until errors.length()){
   val e=errors.optJSONObject(i)?:continue
   val device=e.optString("device_id")
   val revision=e.optLong("revision")
   val entityType=e.optString("entity_type")
   val entityId=e.optString("entity_id")
   val row=LinearLayout(this).apply{orientation=1;setPadding(dp(10),dp(8),dp(10),dp(8));background=GradientDrawable().apply{setColor(0xfffff7ed.toInt());cornerRadius=dp(10).toFloat()}}
   row.addView(text(e.optString("status").uppercase()+" · Revision $revision",12,true))
   row.addView(text("Device: $device  ·  $entityType · $entityId",11))
   row.addView(text(e.optString("error_code")+": "+e.optString("error_message"),11))
   row.addView(text("Attempts: "+e.optInt("attempts")+"  ·  Next retry: "+historyDate(e.optString("next_retry_at")),11))
   if(e.optString("status")=="pending")row.addView(button("Retry now"){task{
    api.history(JSONObject().put("action","sync-retry").put("device_id",device).put("revision",revision).put("entity_type",entityType).put("entity_id",entityId))
    toast("Retry scheduled.")
    loadHistoryMonitor()
   }})
   list.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(6)})
  }
 }
 private fun historyIdentity(row:JSONObject):String=listOf(row.optString("id").ifBlank{row.optString("revision")+"|"+row.optString("entity_type")+"|"+row.optString("entity_id")+"|"+row.optString("operation")+"|"+row.optString("created_at")},row.optJSONObject("before")?.toString().orEmpty(),row.optJSONObject("payload")?.toString().orEmpty()).joinToString("|")
 private fun historyIdentity(row:JSONObject):String=listOf(row.optString("revision"),row.optString("entity_type"),row.optString("entity_id"),row.optString("operation"),row.optString("created_at"),row.optString("id"),row.optJSONObject("before")?.toString().orEmpty(),row.optJSONObject("payload")?.toString().orEmpty()).joinToString("|")
 private fun historyChanged(row:JSONObject):String{
  if(row.optString("operation")=="delete")return "Moved to Recycle Bin"
  val before=row.optJSONObject("before")?:return "New or first version in available history"
  val after=row.optJSONObject("payload")?:JSONObject()
  val keys=(before.keys().asSequence().toList()+after.keys().asSequence().toList()).distinct()
  val changed=keys.filter{before.opt(it).toString()!=after.opt(it).toString()}
  return if(changed.isEmpty())"Re-published · no field changes detected" else changed.joinToString(", ")
 }
 private fun historyLabel(row:JSONObject):String{
  val p=row.optJSONObject("payload")?:row.optJSONObject("before")?:JSONObject()
  if(row.optString("entity_type")=="route")return p.optString("from","?")+" → "+p.optString("to","?")+p.optString("time").takeIf{it.isNotBlank()}?.let{" · "+it}.orEmpty()
  val n=p.optJSONObject("name")?:JSONObject()
  return n.optString("en").ifBlank{n.optString("hi").ifBlank{n.optString("pa").ifBlank{row.optString("entity_id")}}}
 }
 private fun historySource(row:JSONObject):String=when(row.optString("source").lowercase()){
  "web_admin"->"Web Admin"
  "android_app"->"Android App"
  "legacy",""->"Legacy / Unknown"
  else->row.optString("source").replace('_',' ').replaceFirstChar{it.uppercase()}
 }
 private fun historyDate(value:String):String=runCatching{
  java.text.SimpleDateFormat("dd MMM yyyy · hh:mm a",java.util.Locale("en","IN")).apply{
   timeZone=java.util.TimeZone.getTimeZone("Asia/Kolkata")
  }.format(java.util.Date(java.time.Instant.parse(value).toEpochMilli()))
 }.getOrDefault(value.ifBlank{"Date unavailable"})
 private fun historyPill(label:String,bg:Int,fg:Int):TextView=text(label,11,true).apply{
  setTextColor(fg);gravity=Gravity.CENTER;setPadding(dp(9),dp(5),dp(9),dp(5))
  background=GradientDrawable().apply{setColor(bg);cornerRadius=dp(20).toFloat()}
 }
 private fun renderHistoryRows(){
  val list=historyList?:return
  list.removeAllViews()
  val state=historyState
  val revision=state?.optLong("revision")?:0L
  val published=state?.optString("published_at").orEmpty()
  historyStatus?.text=if(state!=null)"Current revision $revision · Last published "+historyDate(published) else historyItems.size.toString()+" history records"
  if(historyItems.isEmpty())list.addView(text("No history records found.",14))
  historyItems.forEach{row->
   val revisionNo=row.optLong("revision")
   val type=row.optString("entity_type","record").replace('_',' ').uppercase()
   val operation=row.optString("operation","unknown").replace('_',' ').uppercase()
   val source=historySource(row)
   val sourceColors=when(source){"Web Admin"->0xffe8f1ff.toInt() to 0xff245b8f.toInt();"Android App"->0xffe7f7ef.toInt() to 0xff147d55.toInt();else->0xfff1f3f5.toInt() to 0xff596579.toInt()}
   val card=LinearLayout(this).apply{
    orientation=1;setPadding(dp(14),dp(13),dp(14),dp(13))
    background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=dp(14).toFloat();setStroke(dp(1),0xffdfe6ef.toInt())}
   }
   val top=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
   top.addView(text("Revision #"+revisionNo,14,true),LinearLayout.LayoutParams(0,-2,1f))
   top.addView(historyPill(source,sourceColors.first,sourceColors.second))
   card.addView(top)
   card.addView(text(historyDate(row.optString("created_at")),12).apply{setTextColor(0xff64748b.toInt());setPadding(0,dp(5),0,dp(8))})
   card.addView(text(historyLabel(row),16,true))
   card.addView(text(type+"  ·  "+operation,12,true).apply{setTextColor(0xff245b8f.toInt());setPadding(0,dp(5),0,dp(7))})
   card.addView(text("Record ID",10,true).apply{setTextColor(0xff64748b.toInt())})
   card.addView(text(row.optString("entity_id").ifBlank{"Not available"},12).apply{setTextIsSelectable(true);setPadding(0,0,0,dp(8))})
   val actor=row.optString("admin_email").ifBlank{row.optString("actor").ifBlank{row.optString("admin_id")}}
   if(actor.isNotBlank()){
    card.addView(text("ADMIN",10,true).apply{setTextColor(0xff64748b.toInt())})
    card.addView(text(actor,12).apply{setTextIsSelectable(true);setPadding(0,0,0,dp(8))})
   }
   card.addView(text("WHAT CHANGED",10,true).apply{setTextColor(0xff64748b.toInt())})
   card.addView(text(historyChanged(row),13).apply{setTextIsSelectable(true);setPadding(0,dp(3),0,0)})
   list.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
  }
  if(historyHasMore){
   historyLoadButton=button("Load older history",true){loadHistoryPage(false)}
   list.addView(historyLoadButton,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(4);bottomMargin=dp(12)})
  }else{
   historyLoadButton=null
   if(historyItems.isNotEmpty())list.addView(text("You’re all caught up · no older records",12).apply{gravity=Gravity.CENTER;setTextColor(0xff64748b.toInt());setPadding(0,dp(10),0,dp(14))})
  }
 }
 private fun recycle(){
  screen("Recycle Bin");add(toolbarAction("↻  Refresh"){recycle()})
  task{val a=api.publish(JSONObject().put("action","recycle-list")).optJSONArray("items")?:JSONArray()
   if(a.length()==0)add(text("Recycle Bin is empty.",14))
   for(i in 0 until a.length()){val x=a.getJSONObject(i);val id=x.optLong("id");val type=x.optString("entity_type");val entity=x.optString("entity_id");val deleted=x.optString("deleted_at").replace('T',' ').take(16)
    val payload=x.optJSONObject("payload")?:JSONObject();val label=when(type){"route"->{val places=dao.allPlaces();(places.firstOrNull{it.id==payload.optString("from")}?.let{placeName(it)}?:payload.optString("from"))+" → "+(places.firstOrNull{it.id==payload.optString("to")}?.let{placeName(it)}?:payload.optString("to"))};"place"->payload.optJSONObject("name")?.optString("en").orEmpty().ifBlank{entity};else->entity}
    val card=LinearLayout(this).apply{orientation=1;setPadding(dp(14),dp(12),dp(14),dp(12));background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=dp(15).toFloat();setStroke(dp(1),0xffe1e7ef.toInt())}}
    card.addView(text(label.ifBlank{entity},16,true));card.addView(text(type.uppercase()+" · ID: "+entity,12));card.addView(text("Deleted: "+deleted,11))
    val actions=LinearLayout(this).apply{orientation=0};actions.addView(button("Restore"){task{val r=api.publish(JSONObject().put("action","recycle-restore").put("id",id));toast("Restored · revision "+r.optLong("revision"));prepareData();recycle()}},LinearLayout.LayoutParams(0,dp(46),1f))
    actions.addView(button("Permanent Delete",false){confirm("Permanently delete this item? This cannot be undone."){task{api.publish(JSONObject().put("action","recycle-purge").put("id",id));toast("Permanently deleted");recycle()}}},LinearLayout.LayoutParams(0,dp(46),1f))
    card.addView(actions);add(card)
   }
  }
 }

 private var reviewFilter="pending"
 private fun reviews(){screen("Review Center");add(button("Refresh"){reviews()});task{val j=api.publish(JSONObject().put("action","review-list"));val failed=j.optJSONArray("failed")?:JSONArray();if(failed.length()>0)add(text("Warning: some queues could not load: "+(0 until failed.length()).joinToString{failed.optString(it)},12));val tables=listOf("route_submissions","correction_submissions","bus_operator_submissions","feedback_submissions");val rows=mutableListOf<Pair<String,JSONObject>>();tables.forEach{t->val a=j.optJSONArray(t)?:JSONArray();for(i in 0 until a.length())rows.add(t to a.getJSONObject(i))};val statuses=listOf("pending" to "Pending","reviewed" to "Seen / Reviewed","approved" to "Approved","rejected" to "Rejected","deleted" to "Deleted");val bar=LinearLayout(this).apply{orientation=0};statuses.forEach{(st,label)->bar.addView(button(label+" ("+rows.count{(t,x)->(if(t=="feedback_submissions")x.optString("status","pending") else x.optString("status","pending"))==st}+")",reviewFilter==st){reviewFilter=st;reviews()},LinearLayout.LayoutParams(-2,dp(44)).apply{marginEnd=dp(4)})};val horizontal=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(bar)};add(horizontal);val visible=rows.filter{it.second.optString("status","pending")==reviewFilter};if(visible.isEmpty())add(text("No requests in this section.",14));visible.forEach{(table,x)->val id=x.optString("id");val feedback=table=="feedback_submissions";val heading=when(table){"route_submissions"->x.optString("from_text","Route")+" → "+x.optString("to_text");"correction_submissions"->"Route correction · "+x.optString("route_id");"bus_operator_submissions"->x.optString("operator_name",x.optString("service_name","Bus request"));else->x.optString("category","Feedback")};val details=when(table){"route_submissions"->listOf("From: "+x.optString("from_text").ifBlank{x.optString("from_id")},"To: "+x.optString("to_text").ifBlank{x.optString("to_id")},"Start: "+x.optString("start_time"),"Service: "+x.optString("service_name"),"Stops: "+(x.optJSONArray("stops")?.let{a->(0 until a.length()).mapNotNull{i->val s=a.optJSONObject(i);if(s!=null)s.optString("name").ifBlank{s.optString("place_name")}.ifBlank{s.optString("place")}else a.optString(i)}.joinToString(" → ")}?:""),"Phone: "+x.optString("phone"),"Request: "+x.optString("submission_type")).filter{it.substringAfter(":").isNotBlank()}.joinToString("\n");"correction_submissions"->listOf("Type: "+x.optString("request_type"),"Route ID: "+x.optString("route_id"),"Stop: "+x.optString("stop_id"),"Missing place: "+x.optString("missing_place_name"),"Missing time: "+x.optString("missing_time"),"Corrected time: "+x.optString("corrected_time"),"Notes: "+x.optString("notes"),"Phone: "+x.optString("phone")).filter{it.substringAfter(":").isNotBlank()}.joinToString("\n");"bus_operator_submissions"->listOf("Operator: "+x.optString("operator_name"),"Service: "+x.optString("service_name"),"Bus type: "+x.optString("bus_type"),"Seat type: "+x.optString("seat_type"),"Location: "+x.optString("location"),"Route: "+x.optString("from_text")+" → "+x.optString("to_text"),"Phone: "+x.optString("phone"),"WhatsApp: "+x.optString("whatsapp"),"Details: "+x.optString("details")).filter{it.substringAfter(":").isNotBlank()}.joinToString("\n");else->listOf("Category: "+x.optString("category"),"Message: "+x.optString("message"),"Related route: "+x.optString("related_route"),"Contact: "+x.optString("contact"),"Submitted: "+x.optString("created_at")).filter{it.substringAfter(":").isNotBlank()}.joinToString("\n")};val card=LinearLayout(this).apply{orientation=1;setPadding(dp(14),dp(12),dp(14),dp(12));background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=dp(14).toFloat();setStroke(dp(1),0xffe1e7ef.toInt())}};card.addView(text(heading,16,true));card.addView(text(x.optString("created_at").replace('T',' ').take(16)+" · "+table.replace("_"," "),11));card.addView(text(details,12));if(!feedback){card.addView(button(if(table=="bus_operator_submissions")"Review / Edit Bus Request" else "Review / Edit Route"){if(table=="bus_operator_submissions")editReview(table,x) else editReviewRoute(table,x)})};if(feedback){card.addView(text("Read-only feedback",12));card.addView(button(if(reviewFilter=="reviewed")"Mark Unseen" else "Mark Seen"){setStatus(table,id,if(reviewFilter=="reviewed")"pending" else "reviewed")})}else{val actions=LinearLayout(this).apply{orientation=0};if(reviewFilter=="pending"||reviewFilter=="reviewed")actions.addView(button(if(table=="route_submissions"||table=="correction_submissions")"Review & Publish" else "Approve"){if(table=="route_submissions"||table=="correction_submissions")editReviewRoute(table,x) else setStatus(table,id,"approved")},LinearLayout.LayoutParams(0,dp(44),1f));if(reviewFilter!="rejected"&&reviewFilter!="deleted")actions.addView(button("Reject",false){setStatus(table,id,"rejected")},LinearLayout.LayoutParams(0,dp(44),1f));if(reviewFilter=="deleted")actions.addView(button("Restore"){setStatus(table,id,"pending")},LinearLayout.LayoutParams(0,dp(44),1f))else actions.addView(button("Move to Pending"){setStatus(table,id,"pending")},LinearLayout.LayoutParams(0,dp(44),1f));if(reviewFilter!="deleted")actions.addView(button("Delete",false){confirm("Move request to Deleted?"){setStatus(table,id,"deleted")}},LinearLayout.LayoutParams(0,dp(44),1f));card.addView(actions)};add(card)}}}
 private fun setStatus(table:String,id:String,status:String){task{api.publish(JSONObject().put("action","review-status").put("table",table).put("id",id).put("status",status));reviews()}}
 private fun editReview(table:String,x:JSONObject){
  val keys=when(table){"route_submissions"->listOf("from_text","to_text","start_time","service_name","phone","route_text");"correction_submissions"->listOf("request_type","route_id","stop_id","before_stop_id","after_stop_id","missing_place_name","missing_time","corrected_time","notes","phone","change_summary");else->listOf("operator_name","service_name","bus_type","bus_types","seat_type","sleeper_count","seater_count","from_text","to_text","location","phone","whatsapp","details")}
  screen("Review / Edit Bus Request",true,"Update the submitted bus information, then save it to the shared review queue.")
  val fields=JSONObject()
  val inputs=keys.associateWith{key->
   val value=if(key=="bus_types")x.optJSONArray("bus_types")?.let{a->(0 until a.length()).joinToString(", "){a.optString(it)}}?:x.optString("bus_type") else x.optString(key)
   val edit=input(key.replace('_',' ').replaceFirstChar{it.uppercase()},value)
   if(key=="details"){edit.minLines=3;edit.maxLines=5}
   add(edit);key to edit
  }.mapValues{it.value.second}
  add(button("Save Request"){task{
   if(table=="bus_operator_submissions"&&(inputs["operator_name"]?.text.isNullOrBlank()||inputs["phone"]?.text.isNullOrBlank()))throw IllegalStateException("Operator name and mobile are required")
   inputs.forEach{(k,v)->if(v.text.isNotBlank()){if(k=="bus_types")fields.put(k,JSONArray(v.text.toString().split(",").map{it.trim()}.filter{it.isNotBlank()}.distinct()))else fields.put(k,v.text.toString())}}
   api.publish(JSONObject().put("action","review-edit").put("table",table).put("id",x.optString("id")).put("fields",fields));toast("Request saved");reviews()
  }})
 }
 private fun confirm(message:String,yes:()->Unit){AlertDialog.Builder(this).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Confirm"){_,_->yes()}.show()}
}
