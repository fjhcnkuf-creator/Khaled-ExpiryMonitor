package com.example.expiry_monitor
import android.Manifest
import android.app.*;import android.content.*;import android.app.DatePickerDialog;import android.content.pm.PackageManager;import android.os.Build;import android.os.Bundle
import androidx.activity.ComponentActivity;import androidx.activity.compose.rememberLauncherForActivityResult;import androidx.activity.result.contract.ActivityResultContracts;import androidx.activity.compose.setContent
import androidx.camera.core.*;import androidx.camera.lifecycle.ProcessCameraProvider;import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*;import androidx.compose.foundation.lazy.LazyColumn;import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*;import androidx.compose.runtime.*;import androidx.compose.ui.*;import androidx.compose.ui.viewinterop.AndroidView;import androidx.compose.ui.res.painterResource;import kotlinx.coroutines.delay;import androidx.compose.ui.Alignment;import androidx.compose.ui.platform.LocalContext;import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat;import com.google.mlkit.vision.barcode.BarcodeScanning;import com.google.mlkit.vision.common.InputImage
import java.time.*;import android.net.Uri;import java.io.*;import java.time.temporal.ChronoUnit;import java.time.format.DateTimeFormatter;import java.util.concurrent.Executors

data class Product(val id:Long,val name:String,val barcode:String,val production:String,val expiry:String,val qty:Int)

fun updateQuantity(products: List<Product>, id: Long, delta: Int): List<Product> =
    products.map { p ->
        if (p.id == id) p.copy(qty = (p.qty + delta).coerceAtLeast(0)) else p
    }

fun status(p:Product):Int=try{ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.parse(p.expiry)).toInt()}catch(_:Exception){99999}
fun load(c:Context):List<Product>{return c.getSharedPreferences("data",0).getStringSet("products",emptySet())!!.mapNotNull{a->val x=a.split("¦");if(x.size>=6)Product(x[0].toLong(),x[1],x[2],x[3],x[4],x[5].toIntOrNull()?:1)else null}}
fun save(c:Context,p:List<Product>){c.getSharedPreferences("data",0).edit().putStringSet("products",p.map{"${it.id}¦${it.name}¦${it.barcode}¦${it.production}¦${it.expiry}¦${it.qty}"}.toSet()).apply()}
fun alarm(c:Context,p:Product,daysBefore:Int){
 try{
  if(Build.VERSION.SDK_INT>=26){
   val m=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
   m.createNotificationChannel(NotificationChannel("expiry","صلاحية المنتجات",NotificationManager.IMPORTANCE_HIGH))
  }val date=LocalDate.parse(p.expiry).minusDays(daysBefore.toLong());val ms=date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();if(ms<=System.currentTimeMillis())return;val am=c.getSystemService(Context.ALARM_SERVICE)as AlarmManager;val i=Intent(c,Reminder::class.java).putExtra("name",p.name).putExtra("days",daysBefore);val pi=PendingIntent.getBroadcast(c,(p.id*10+daysBefore).toInt(),i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,ms,pi)}catch(_:Exception){}}
class Reminder:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){val ch="expiry";val n=Notification.Builder(c,ch).setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("تنبيه صلاحية المنتج").setContentText("${i.getStringExtra("name")} سينتهي خلال ${i.getIntExtra("days",30)} يومًا").setAutoCancel(true).build();val m=c.getSystemService(Context.NOTIFICATION_SERVICE)as NotificationManager;if(Build.VERSION.SDK_INT>=26)m.createNotificationChannel(NotificationChannel(ch,"صلاحية المنتجات",NotificationManager.IMPORTANCE_HIGH));m.notify(System.currentTimeMillis().toInt(),n)}}

class MainActivity:ComponentActivity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);if(Build.VERSION.SDK_INT>=33)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),10);setContent{App()}}
 @Composable fun App(){
 val c=LocalContext.current
 var splash by remember{mutableStateOf(true)}
 LaunchedEffect(Unit){delay(1400);splash=false}
 if(splash){Splash();return}
 var ps by remember{mutableStateOf(load(c))};var page by remember{mutableStateOf("home")};var edit by remember{mutableStateOf<Product?>(null)};var scan by remember{mutableStateOf("")};when(page){
  "home"->Home(ps,{edit=null;scan="";page="edit"},{page="soon"},{page="settings"},{p->ps=ps.filter{it.id!=p.id};save(c,ps)})
  "soon"->Soon(ps,{page="home"},{edit=it;page="edit"})
  "edit"->Edit(edit,scan,{p->
   ps=if(edit==null)ps+p else ps.map{if(it.id==p.id)p else it}
   save(c,ps)
   val notifyDays=c.getSharedPreferences("settings",0).getInt("days",30)
   alarm(c,p,notifyDays)
   page="home"
  },{page="scan"},{page="home"})
  "scan"->Scanner({scan=it;page="edit"},{page="edit"})
 }}
@Composable fun Splash(){
 Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
  Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
   androidx.compose.foundation.Image(painterResource(com.example.expiry_monitor.R.drawable.khaled_logo),contentDescription="Khaled | مراقب الصلاحية",modifier=Modifier.size(230.dp))
   Spacer(Modifier.height(18.dp));Text("Khaled",style=MaterialTheme.typography.headlineLarge);Text("مراقب الصلاحية",style=MaterialTheme.typography.titleLarge)
   Spacer(Modifier.height(24.dp));CircularProgressIndicator()
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Home(ps:List<Product>,add:()->Unit,soon:()->Unit,settings:()->Unit,del:(Product)->Unit){
 var query by remember{mutableStateOf("")}
 var filter by remember{mutableStateOf("الكل")}
 val filtered=ps.filter{p->
  val q=query.trim()
  val matches=q.isEmpty()||p.name.contains(q,true)||p.barcode.contains(q)
  val d=status(p)
  val state=when{d<0->"منتهية";d<=30->"قريبة";else->"صالحة"}
  matches&&(filter=="الكل"||filter==state)
 }.sortedBy{status(it)}
 Scaffold(topBar={TopAppBar(title={Text("Khaled | مراقب الصلاحية")})},floatingActionButton={FloatingActionButton(add){Text("+")}}){pad->
  Column(Modifier.padding(pad).padding(16.dp)){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
    Text("لوحة التحكم",style=MaterialTheme.typography.headlineSmall)
    TextButton(settings){Text("⚙ الإعدادات")}
   }
   Spacer(Modifier.height(10.dp))
   val e=ps.count{status(it)<0};val s=ps.count{status(it) in 0..30};val v=ps.size-e-s
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("📦 الكل",ps.size);Stat("🟢 صالحة",v)}
   Spacer(Modifier.height(8.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("🟠 خلال 30 يوم",s);Stat("🔴 منتهية",e)}
   Spacer(Modifier.height(12.dp))
   OutlinedTextField(query,{query=it},label={Text("ابحث بالاسم أو الباركود")},singleLine=true,modifier=Modifier.fillMaxWidth())
   Spacer(Modifier.height(8.dp))
   Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("الكل","صالحة","قريبة","منتهية").forEach{f->FilterChip(selected=filter==f,onClick={filter=f},label={Text(f)})}}
   Spacer(Modifier.height(8.dp))
   Button(soon,Modifier.fillMaxWidth()){Text("عرض القريبة من الانتهاء")}
   Spacer(Modifier.height(8.dp))
   if(filtered.isEmpty()) Text("لا توجد منتجات مطابقة.",modifier=Modifier.padding(20.dp))
   LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
    items(filtered){p->
     val d=status(p)
     Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
      Text(p.name,style=MaterialTheme.typography.titleMedium)
      Text("الباركود: ${p.barcode}");Text("الانتهاء: ${p.expiry}")
      Text(if(d<0)"🔴 منتهي" else if(d<=30)"🟠 متبقي $d يوم" else "🟢 متبقي $d يوم")
      Row(
    verticalAlignment = Alignment.CenterVertically
) {
    Text("الكمية: ${p.qty}", modifier = Modifier.weight(1f))
    OutlinedButton(
        onClick = {
            ps = updateQuantity(ps, p.id, -1)
            save(c, ps)
        },
        enabled = p.qty > 0,
        modifier = Modifier.padding(horizontal = 2.dp)
    ) { Text("−") }
    OutlinedButton(
        onClick = {
            ps = updateQuantity(ps, p.id, 1)
            save(c, ps)
        },
        modifier = Modifier.padding(horizontal = 2.dp)
    ) { Text("+") }
}
      TextButton({del(p)}){Text("حذف")}
     }}
    }
   }
  }
 }
}
@Composable fun RowScope.Stat(t:String,n:Int){Card(Modifier.weight(1f)){Column(Modifier.padding(12.dp)){Text(t);Text("$n",style=MaterialTheme.typography.headlineMedium)}}}
@OptIn(ExperimentalMaterial3Api::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Settings(onBack:()->Unit){
 val c=LocalContext.current
 var days by remember{mutableStateOf(c.getSharedPreferences("settings",0).getInt("days",30))}
 val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
  if(uri!=null) try{c.contentResolver.openOutputStream(uri)?.use{out->val data=load(c).joinToString("\n"){"${it.id}¦${it.name}¦${it.barcode}¦${it.production}¦${it.expiry}¦${it.qty}"};out.write(data.toByteArray())}}catch(_:Exception){}
 }
 val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
  if(uri!=null) try{val text=c.contentResolver.openInputStream(uri)!!.bufferedReader().readText();val ps=text.lines().mapNotNull{a->val x=a.split("¦");if(x.size>=6)Product(x[0].toLong(),x[1],x[2],x[3],x[4],x[5].toIntOrNull()?:1)else null};save(c,ps);android.widget.Toast.makeText(c,"تم استرجاع البيانات. أعد فتح التطبيق لتحديث القائمة.",android.widget.Toast.LENGTH_SHORT).show()}catch(_:Exception){}
 }
 Scaffold(topBar={TopAppBar(title={Text("الإعدادات")},navigationIcon={TextButton(onBack){Text("رجوع")}})}){pad->
  Column(Modifier.padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   Text("التنبيه",style=MaterialTheme.typography.titleLarge)
   Text("سيصلك تنبيه قبل انتهاء المنتج بـ $days يومًا")
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(7,15,30,60).forEach{d->FilterChip(selected=days==d,onClick={days=d;c.getSharedPreferences("settings",0).edit().putInt("days",d).apply()},label={Text("$d")})}}
   Button({create.launch("expiry_backup.json")},Modifier.fillMaxWidth()){Text("⬆ تصدير نسخة احتياطية")}
   Button({open.launch(arrayOf("application/json","text/plain"))},Modifier.fillMaxWidth()){Text("⬇ استرجاع نسخة احتياطية")}
   Text("ملاحظة: النسخة الاحتياطية تحفظ أسماء المنتجات والباركود والتواريخ والكميات.",style=MaterialTheme.typography.bodySmall)
   Text("إذا لم تظهر التنبيهات، تأكد من السماح للتطبيق بالإشعارات من إعدادات الهاتف.",style=MaterialTheme.typography.bodySmall)
  }
 }
}

@Composable fun Soon(ps:List<Product>,back:()->Unit,edit:(Product)->Unit){val x=ps.filter{status(it) in 0..30}.sortedBy{status(it)};Scaffold(topBar={TopAppBar(title={Text("قريبة الانتهاء")},navigationIcon={TextButton(back){Text("رجوع")}})}){pad->LazyColumn(Modifier.padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){items(x){p->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text(p.name,style=MaterialTheme.typography.titleMedium);Text("ينتهي: ${p.expiry} — متبقي ${status(p)} يوم");TextButton({edit(p)}){Text("تعديل")}}}}}}}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Edit(old:Product?,code:String,onSave:(Product)->Unit,scan:()->Unit,back:()->Unit){
 var n by remember(old){mutableStateOf(old?.name?:"")}
 var b by remember(old,code){mutableStateOf(if(code.isNotEmpty())code else old?.barcode?:"")}
 var pr by remember(old){mutableStateOf(old?.production?:"")}
 var ex by remember(old){mutableStateOf(old?.expiry?:"")}
 var q by remember(old){mutableStateOf((old?.qty?:1).toString())}
 val c=LocalContext.current
 fun pickDate(current:String,onPick:(String)->Unit){
  val d=try{LocalDate.parse(current)}catch(_:Exception){LocalDate.now()}
  DatePickerDialog(c,{_,y,m,day->onPick(LocalDate.of(y,m+1,day).toString())},d.year,d.monthValue-1,d.dayOfMonth).show()
 }
 Scaffold(topBar={TopAppBar(title={Text(if(old==null)"إضافة منتج" else "تعديل المنتج")},navigationIcon={TextButton(back){Text("رجوع")}})}){pad->
  Column(Modifier.padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
   Button(scan,Modifier.fillMaxWidth()){Text("📷 مسح الباركود")}
   OutlinedTextField(n,{n=it},label={Text("اسم المنتج")},Modifier.fillMaxWidth())
   OutlinedTextField(b,{b=it},label={Text("رقم الباركود")},Modifier.fillMaxWidth())
   OutlinedTextField(pr,{pr=it},label={Text("تاريخ الإنتاج")},Modifier.fillMaxWidth(),readOnly=true)
   Button({pickDate(pr){pr=it}},Modifier.fillMaxWidth()){Text("📅 اختيار تاريخ الإنتاج")}
   OutlinedTextField(ex,{ex=it},label={Text("تاريخ الانتهاء")},Modifier.fillMaxWidth(),readOnly=true)
   Button({pickDate(ex){ex=it}},Modifier.fillMaxWidth()){Text("📅 اختيار تاريخ الانتهاء")}
   OutlinedTextField(q,{q=it},label={Text("الكمية")},Modifier.fillMaxWidth())
   val validDates=try{
    val e=LocalDate.parse(ex)
    val pp=if(pr.isBlank()) null else LocalDate.parse(pr)
    pp==null || !e.isBefore(pp)
   }catch(_:Exception){false}
   if(!validDates && ex.isNotBlank()) Text("⚠️ تأكد من صحة التواريخ وأن تاريخ الانتهاء بعد الإنتاج.",color=MaterialTheme.colorScheme.error)
   Button({onSave(Product(old?.id?:System.currentTimeMillis(),n,b,pr,ex,q.toIntOrNull()?:1))},
    enabled=n.isNotBlank()&&b.isNotBlank()&&ex.isNotBlank()&&validDates,Modifier.fillMaxWidth()){Text("حفظ المنتج")}
  }
 }
}

@Composable fun Scanner(done:(String)->Unit,back:()->Unit){val c=LocalContext.current;val a=c as ComponentActivity;var view by remember{mutableStateOf<PreviewView?>(null)};DisposableEffect(Unit){if(ContextCompat.checkSelfPermission(c,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)a.requestPermissions(arrayOf(Manifest.permission.CAMERA),11);val ex=Executors.newSingleThreadExecutor();val f=ProcessCameraProvider.getInstance(c);f.addListener({val pr=f.get();val pv=PreviewView(c);view=pv;val preview=Preview.Builder().build().also{it.surfaceProvider=pv.surfaceProvider};val an=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();an.setAnalyzer(ex){ip->ip.image?.let{BarcodeScanning.getClient().process(InputImage.fromMediaImage(it,ip.imageInfo.rotationDegrees)).addOnSuccessListener{r->r.firstOrNull()?.rawValue?.let(done)}.addOnCompleteListener{ip.close()}}?:ip.close()};try{pr.unbindAll();pr.bindToLifecycle(a,CameraSelector.DEFAULT_BACK_CAMERA,preview,an)}catch(_:Exception){}},ContextCompat.getMainExecutor(c));onDispose{ex.shutdown()}}
 Box(Modifier.fillMaxSize()){view?.let{AndroidView(factory={it},Modifier.fillMaxSize())};Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Bottom){Button(back,Modifier.fillMaxWidth().padding(16.dp)){Text("رجوع")}}}}
