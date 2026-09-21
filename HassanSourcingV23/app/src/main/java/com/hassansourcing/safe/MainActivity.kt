package com.hassansourcing.safe

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity: Activity(){
  private lateinit var db:DatabaseHelper
  private var screen="home"
  private val blue=0xFF0A55B8.toInt()
  private val orange=0xFFFF9F1C.toInt()
  private val bg=0xFFF5F7FB.toInt()
  private val text=0xFF183153.toInt()
  private val REQ_EXPORT=900
  override fun onCreate(b:Bundle?){super.onCreate(b);try{db=DatabaseHelper(this);showHome()}catch(e:Throwable){fatal(e)}}
  private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
  private fun fatal(e:Throwable){val t=TextView(this);t.text="Hassan Sourcing startup error\n\n${e.javaClass.simpleName}: ${e.message}";t.setTextColor(Color.RED);t.textSize=18f;t.setPadding(dp(20),dp(40),dp(20),dp(20));setContentView(t)}
  private fun root():Pair<ScrollView,LinearLayout>{val s=ScrollView(this);s.setBackgroundColor(bg);val l=LinearLayout(this);l.orientation=LinearLayout.VERTICAL;l.setPadding(dp(16),dp(16),dp(16),dp(24));s.addView(l);return s to l}
  private fun title(s:String)=TextView(this).apply{text=s;textSize=25f;setTextColor(this@MainActivity.text);setTypeface(typeface,android.graphics.Typeface.BOLD);setPadding(0,0,0,dp(10))}
  private fun field(h:String)=EditText(this).apply{hint=h;setTextColor(this@MainActivity.text);setHintTextColor(0xFF788AA0.toInt());backgroundTintList=android.content.res.ColorStateList.valueOf(blue)}
  private fun button(label:String,color:Int=blue,click:()->Unit)=Button(this).apply{text=label;isAllCaps=false;textSize=16f;setTextColor(Color.WHITE);backgroundTintList=android.content.res.ColorStateList.valueOf(color);setOnClickListener{click()}}
  private fun lp(m:Int=8)=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{setMargins(0,0,0,dp(m))}
  private fun iconRow(icon:Int,en:String,ar:String,click:()->Unit):View{
    val r=LinearLayout(this);r.orientation=LinearLayout.HORIZONTAL;r.gravity=Gravity.CENTER_VERTICAL;r.setPadding(dp(12),dp(12),dp(12),dp(12));r.setBackgroundColor(Color.WHITE);r.setOnClickListener{click()}
    r.addView(ImageView(this).apply{setImageResource(icon);setColorFilter(blue)},LinearLayout.LayoutParams(dp(48),dp(48)))
    r.addView(TextView(this).apply{text="$en\n$ar";textSize=18f;setTextColor(this@MainActivity.text);setPadding(dp(14),0,0,0)},LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
    return r
  }
  private fun suppliers()=try{db.getSuppliers()}catch(_:Throwable){emptyList()}
  private fun products()=try{db.getProducts()}catch(_:Throwable){emptyList()}
  private fun showHome(){screen="home";val(s,r)=root();r.addView(title("Hassan Sourcing"));r.addView(TextView(this).apply{text="V2.3 SAFE • Native Android";setTextColor(blue);textSize=15f},lp())
    r.addView(TextView(this).apply{text="Suppliers / موردين: ${suppliers().size}     Items / أصناف: ${products().size}";textSize=17f;setTextColor(this@MainActivity.text);setPadding(dp(10),dp(14),dp(10),dp(14));setBackgroundColor(Color.WHITE)},lp(12))
    r.addView(iconRow(android.R.drawable.ic_menu_add,"Add Supplier","إضافة مورد"){supplierForm()},lp())
    r.addView(iconRow(android.R.drawable.ic_input_add,"Add Item","إضافة صنف"){if(suppliers().isEmpty()){toast("Add supplier first / أضف مورد أولاً");supplierForm()}else productForm()},lp())
    r.addView(iconRow(android.R.drawable.ic_menu_agenda,"Suppliers & Items","عرض البيانات"){listData()},lp())
    r.addView(iconRow(android.R.drawable.ic_menu_save,"Save Excel","حفظ Excel"){exportExcel()},lp())
    r.addView(iconRow(android.R.drawable.ic_menu_share,"Share Latest Excel","مشاركة آخر Excel"){shareExcel()},lp())
    setContentView(s)
  }
  private fun supplierForm(){screen="supplier";val(s,r)=root();r.addView(title("Add Supplier / إضافة مورد"));val fs=listOf("Supplier / Company name *","Contact person","Phone","WhatsApp","WeChat","Email","Address","Notes").map{field(it)};fs.forEach{r.addView(it,lp(5))}
    r.addView(button("Save Supplier / حفظ المورد"){if(fs[0].text.toString().trim().isEmpty()){toast("Supplier name required");return@button};val id=db.addSupplier(fs[0].text.toString(),fs[1].text.toString(),fs[2].text.toString(),fs[3].text.toString(),fs[4].text.toString(),fs[5].text.toString(),fs[6].text.toString(),fs[7].text.toString());if(id>0){refreshExcel();toast("Supplier Added ✓");showHome()}else toast("Save failed")},lp())
    r.addView(button("Back / رجوع",0xFF6C7A8A.toInt()){showHome()});setContentView(s)
  }
  private fun productForm(){screen="product";val ss=suppliers();if(ss.isEmpty()){supplierForm();return};val(s,r)=root();r.addView(title("Add Item / إضافة صنف"));val sp=Spinner(this);sp.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,ss.map{it.name});r.addView(sp,lp())
    val fs=listOf("Item name *","Price","Currency","MOQ","Carton quantity","Carton size","Weight","Notes").map{field(it)};fs[2].setText("USD");fs.forEach{r.addView(it,lp(5))}
    r.addView(button("Add Item / إضافة الصنف"){if(fs[0].text.toString().trim().isEmpty()){toast("Item name required");return@button};val x=fs.map{it.text.toString()};val id=db.addProduct(ss[sp.selectedItemPosition].id,x[0],x[1],x[2],x[3],x[4],x[5],x[6],x[7]);if(id>0){refreshExcel();toast("Item Added ✓ / تمت إضافة الصنف");listData()}else toast("Save failed")},lp())
    r.addView(button("Back / رجوع",0xFF6C7A8A.toInt()){showHome()});setContentView(s)
  }
  private fun listData(){screen="list";val(s,r)=root();r.addView(title("Suppliers & Items / الموردين والأصناف"));val ps=products();suppliers().forEach{x->r.addView(TextView(this).apply{text=x.name;textSize=19f;setTypeface(typeface,android.graphics.Typeface.BOLD);setTextColor(blue);setPadding(dp(10),dp(10),dp(10),dp(8));setBackgroundColor(Color.WHITE)},lp(3));val its=ps.filter{it.supplierId==x.id};if(its.isEmpty())r.addView(TextView(this).apply{text="No items / لا يوجد أصناف";setTextColor(this@MainActivity.text)},lp()) else its.forEach{p->r.addView(TextView(this).apply{text="• ${p.name}   ${p.price} ${p.currency}\n  MOQ: ${p.moq}   Carton: ${p.cartonQty}";textSize=16f;setTextColor(this@MainActivity.text);setPadding(dp(14),dp(9),dp(8),dp(9));setBackgroundColor(0xFFEDF3FC.toInt())},lp(3))}}
    r.addView(button("Add Item / إضافة صنف",orange){productForm()},lp());r.addView(button("Save Excel / حفظ Excel"){exportExcel()},lp());r.addView(button("Back / رجوع",0xFF6C7A8A.toInt()){showHome()});setContentView(s)
  }
  private fun latest():File=File(File(filesDir,"exports").apply{mkdirs()},"Hassan_Sourcing_Latest.xlsx")
  private fun rebuild():File=latest().also{f->f.outputStream().use{XlsxExporter.write(it,suppliers(),products())}}
  private fun refreshExcel(){try{rebuild()}catch(_:Throwable){};val u=getSharedPreferences("prefs",MODE_PRIVATE).getString("last_export",null)?:return;try{contentResolver.openOutputStream(Uri.parse(u),"w")?.use{XlsxExporter.write(it,suppliers(),products())}}catch(_:Throwable){}}
  private fun exportExcel(){val name="Hassan_Sourcing_${SimpleDateFormat("yyyy-MM-dd_HHmm",Locale.US).format(Date())}.xlsx";val i=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";putExtra(Intent.EXTRA_TITLE,name)};startActivityForResult(i,REQ_EXPORT)}
  @Deprecated("deprecated") override fun onActivityResult(rc:Int,res:Int,data:Intent?){super.onActivityResult(rc,res,data);if(rc==REQ_EXPORT&&res==RESULT_OK){val u=data?.data?:return;try{contentResolver.openOutputStream(u,"w")?.use{XlsxExporter.write(it,suppliers(),products())};getSharedPreferences("prefs",MODE_PRIVATE).edit().putString("last_export",u.toString()).apply();rebuild();toast("Excel saved with ${products().size} items")}catch(e:Throwable){toast("Save failed: ${e.message}")}}}
  private fun shareExcel(){try{val f=rebuild();val u=FileProvider.getUriForFile(this,"$packageName.fileprovider",f);val i=Intent(Intent.ACTION_SEND).apply{type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";putExtra(Intent.EXTRA_STREAM,u);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)};startActivity(Intent.createChooser(i,"Share Excel"))}catch(e:Throwable){toast("Share failed: ${e.message}")}}
  private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
  override fun onBackPressed(){if(screen=="home")finish()else showHome()}
}
