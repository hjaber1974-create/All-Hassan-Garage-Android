package com.hassansourcing.safe

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
  private val green=0xFF1B8F4B.toInt()
  private val gray=0xFF6C7A8A.toInt()
  private val bg=0xFFF5F7FB.toInt()
  private val ink=0xFF183153.toInt()

  private val REQ_CAMERA=701
  private var pendingPhotoPath:String?=null
  private var pendingPhotoTarget="NONE"
  private var pendingShopId:Long=0
  private var pendingItemId:Long=0

  override fun onCreate(b:Bundle?){
    super.onCreate(b)
    try{
      db=DatabaseHelper(this)
      showHome()
    }catch(e:Throwable){ fatal(e) }
  }

  private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()

  private fun fatal(e:Throwable){
    val t=TextView(this)
    t.text="Hassan Sourcing error\n\n${e.javaClass.simpleName}: ${e.message}"
    t.setTextColor(Color.RED);t.textSize=18f;t.setPadding(dp(20),dp(40),dp(20),dp(20))
    setContentView(t)
  }

  private fun root():Pair<ScrollView,LinearLayout>{
    val s=ScrollView(this);s.setBackgroundColor(bg)
    val l=LinearLayout(this);l.orientation=LinearLayout.VERTICAL;l.setPadding(dp(16),dp(16),dp(16),dp(30))
    s.addView(l)
    return s to l
  }

  private fun title(s:String)=TextView(this).apply{
    text=s;textSize=25f;setTextColor(ink)
    setTypeface(typeface,android.graphics.Typeface.BOLD)
    setPadding(0,0,0,dp(10))
  }

  private fun small(s:String)=TextView(this).apply{
    text=s;textSize=14f;setTextColor(gray);setPadding(0,0,0,dp(8))
  }

  private fun field(h:String,value:String="")=EditText(this).apply{
    hint=h;setText(value);setTextColor(ink);setHintTextColor(0xFF788AA0.toInt())
    backgroundTintList=android.content.res.ColorStateList.valueOf(blue)
  }

  private fun button(label:String,color:Int=blue,click:()->Unit)=Button(this).apply{
    text=label;isAllCaps=false;textSize=16f;setTextColor(Color.WHITE)
    backgroundTintList=android.content.res.ColorStateList.valueOf(color)
    setOnClickListener{click()}
  }

  private fun lp(m:Int=8)=LinearLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT
  ).apply{setMargins(0,0,0,dp(m))}

  private fun card():LinearLayout=LinearLayout(this).apply{
    orientation=LinearLayout.VERTICAL
    setPadding(dp(14),dp(14),dp(14),dp(14))
    setBackgroundColor(Color.WHITE)
  }

  private fun thumb(path:String,size:Int=90):ImageView{
    val iv=ImageView(this)
    iv.scaleType=ImageView.ScaleType.CENTER_CROP
    try{iv.setImageBitmap(BitmapFactory.decodeFile(path))}catch(_:Throwable){}
    iv.layoutParams=LinearLayout.LayoutParams(dp(size),dp(size)).apply{setMargins(0,0,dp(10),0)}
    return iv
  }

  private fun shopLabel(s:Shop):String{
    if(s.name.isNotBlank()) return s.name
    return "Shop #${s.id}"
  }

  private fun showHome(){
    screen="home"
    val(s,r)=root()
    r.addView(title("Hassan Sourcing"))
    r.addView(small("SHOP MODE • محل واحد → أصناف كثيرة → Save مرة واحدة"))

    val draft=db.getDraftShop()
    if(draft!=null){
      r.addView(button("▶ Resume Current Shop / كمّل المحل الحالي",orange){openShop(draft.id)},lp(10))
    }

    r.addView(button("＋ OPEN NEW SHOP / افتح محل جديد",blue){
      val existing=db.getDraftShop()
      if(existing!=null){
        openShop(existing.id)
      }else{
        val id=db.createDraftShop()
        openShop(id)
      }
    },lp(12))

    r.addView(TextView(this).apply{
      text="Saved Shops / المحلات المحفوظة: ${db.savedShopCount()}"
      textSize=18f;setTextColor(ink);setTypeface(typeface,android.graphics.Typeface.BOLD)
      setPadding(0,dp(8),0,dp(8))
    })

    val saved=db.getSavedShops()
    if(saved.isEmpty()){
      r.addView(small("No finished shops yet / ما في محلات محفوظة بعد"))
    }else{
      saved.forEach{sh->
        val c=card()
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        if(sh.shopPhoto.isNotBlank() && File(sh.shopPhoto).exists()) row.addView(thumb(sh.shopPhoto,72))
        val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        info.addView(TextView(this).apply{
          text=shopLabel(sh);textSize=18f;setTextColor(ink);setTypeface(typeface,android.graphics.Typeface.BOLD)
        })
        val items=db.getShopItems(sh.id)
        info.addView(TextView(this).apply{
          text="${items.size} items / أصناف  •  ${dateText(sh.createdAt)}";textSize=14f;setTextColor(gray)
        })
        row.addView(info,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        c.addView(row)
        c.setOnClickListener{showSavedShop(sh.id)}
        r.addView(c,lp(8))
      }
    }
    setContentView(s)
  }

  private fun openShop(shopId:Long){
    val sh=db.getShop(shopId)?:return showHome()
    screen="shop:$shopId"
    val(s,r)=root()
    r.addView(title("Current Shop / المحل الحالي"))
    r.addView(small("كل الصور والأصناف تحت نفس المحل. Save Shop فقط عند ما تخلص."))

    if(sh.shopPhoto.isNotBlank() && File(sh.shopPhoto).exists()){
      val iv=thumb(sh.shopPhoto,180)
      iv.layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(180)).apply{setMargins(0,0,0,dp(8))}
      r.addView(iv)
    }

    val fName=field("Shop / Company name",sh.name)
    val fContact=field("Contact person",sh.contact)
    val fPhone=field("Phone",sh.phone)
    val fWhats=field("WhatsApp",sh.whatsapp)
    val fWechat=field("WeChat",sh.wechat)
    val fAddress=field("Address / Market / Booth",sh.address)
    val fNotes=field("Shop notes",sh.notes)
    val fields=listOf(fName,fContact,fPhone,fWhats,fWechat,fAddress,fNotes)

    fun saveHeader(){
      db.updateShop(shopId,
        fName.text.toString(),fContact.text.toString(),fPhone.text.toString(),
        fWhats.text.toString(),fWechat.text.toString(),fAddress.text.toString(),fNotes.text.toString()
      )
    }

    r.addView(button("📷 Take Shop / Business Card Photo",orange){
      saveHeader()
      launchCamera("SHOP",shopId,0)
    },lp())

    fields.forEach{r.addView(it,lp(4))}

    val items=db.getShopItems(shopId)
    r.addView(TextView(this).apply{
      text="Items in this shop / أصناف هالمحل: ${items.size}"
      textSize=18f;setTextColor(ink);setTypeface(typeface,android.graphics.Typeface.BOLD)
      setPadding(0,dp(12),0,dp(8))
    })

    items.forEach{item->
      val photos=db.getItemPhotos(item.id)
      val c=card()
      val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
      if(photos.isNotEmpty() && File(photos[0].path).exists()) row.addView(thumb(photos[0].path,70))
      val tx=TextView(this).apply{
        text="${item.name.ifBlank{"Item #${item.id}"}}\n${item.price} ${item.currency}   •   ${photos.size} photo(s)"
        textSize=16f;setTextColor(ink)
      }
      row.addView(tx,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
      c.addView(row)
      c.setOnClickListener{saveHeader();openItemEditor(shopId,item.id)}
      r.addView(c,lp(6))
    }

    r.addView(button("＋ Add Another Item / أضف صنف جديد",blue){
      saveHeader()
      openItemEditor(shopId,null)
    },lp(8))

    r.addView(button("✓ SAVE SHOP / خلصت المحل",green){
      saveHeader()
      val count=db.getShopItems(shopId).size
      if(count==0){
        toast("Add at least one item / أضف صنف واحد على الأقل")
      }else{
        db.finishShop(shopId)
        toast("Shop Saved ✓ / تم حفظ المحل")
        showSavedShop(shopId)
      }
    },lp(8))

    r.addView(button("Home / الرئيسية",gray){
      saveHeader()
      showHome()
    })
    setContentView(s)
  }

  private fun openItemEditor(shopId:Long,itemId:Long?){
    screen="item:$shopId:${itemId?:0L}"
    val existing=itemId?.let{id->db.getShopItems(shopId).firstOrNull{it.id==id}}
    val(s,r)=root()
    r.addView(title("Item / الصنف"))
    r.addView(small("اضغط Add Photo أكثر من مرة لنفس الصنف. الشاشة بتضل مفتوحة."))

    val fName=field("Item name / اسم الصنف",existing?.name?:"")
    val fPrice=field("Price / السعر",existing?.price?:"")
    val fCurrency=field("Currency / العملة",existing?.currency?:"USD")
    val fMoq=field("MOQ / أقل كمية",existing?.moq?:"")
    val fNotes=field("Notes / ملاحظات",existing?.notes?:"")
    listOf(fName,fPrice,fCurrency,fMoq,fNotes).forEach{r.addView(it,lp(4))}

    var currentItemId=itemId

    fun persistItem():Long{
      val name=fName.text.toString().trim().ifBlank{
        val n=db.getShopItems(shopId).size+1
        "Item $n"
      }
      if(fName.text.toString().isBlank()) fName.setText(name)
      val id=currentItemId
      return if(id==null){
        db.addShopItem(shopId,name,fPrice.text.toString(),fCurrency.text.toString(),fMoq.text.toString(),fNotes.text.toString()).also{currentItemId=it}
      }else{
        db.updateShopItem(id,name,fPrice.text.toString(),fCurrency.text.toString(),fMoq.text.toString(),fNotes.text.toString())
        id
      }
    }

    val photoBox=LinearLayout(this).apply{
      orientation=LinearLayout.HORIZONTAL
      gravity=Gravity.CENTER_VERTICAL
    }

    fun fillPhotos(){
      photoBox.removeAllViews()
      val id=currentItemId
      val photos=if(id!=null) db.getItemPhotos(id) else emptyList()
      if(photos.isEmpty()){
        photoBox.addView(small("No photos yet / ما في صور بعد"))
      }else{
        photos.take(4).forEach{p->
          if(File(p.path).exists()) photoBox.addView(thumb(p.path,72))
        }
        if(photos.size>4) photoBox.addView(TextView(this).apply{
          text="+${photos.size-4}";textSize=18f;setTextColor(blue);setPadding(dp(8),0,0,0)
        })
      }
    }

    fillPhotos()
    r.addView(photoBox,lp(8))

    r.addView(button("📷 ADD PHOTO / أضف صورة",orange){
      val id=persistItem()
      if(id>0) launchCamera("ITEM",shopId,id)
    },lp(8))

    r.addView(button("✓ Save Item / حفظ الصنف",green){
      val id=persistItem()
      if(id>0){
        toast("Item Saved ✓")
        openShop(shopId)
      }
    },lp(6))

    r.addView(button("✓ Save + Add Another Item",blue){
      val id=persistItem()
      if(id>0){
        toast("Item Saved ✓")
        openItemEditor(shopId,null)
      }
    },lp(6))

    r.addView(button("Back to Shop / رجوع للمحل",gray){
      if(fName.text.toString().isNotBlank() || fPrice.text.toString().isNotBlank() || currentItemId!=null){
        persistItem()
      }
      openShop(shopId)
    })
    setContentView(s)
  }

  private fun launchCamera(target:String,shopId:Long,itemId:Long){
    try{
      val dir=File(filesDir,"photos").apply{mkdirs()}
      val file=File(dir,"IMG_${System.currentTimeMillis()}.jpg")
      val uri=FileProvider.getUriForFile(this,"$packageName.fileprovider",file)
      val i=Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply{
        putExtra(MediaStore.EXTRA_OUTPUT,uri)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      if(i.resolveActivity(packageManager)==null){
        toast("Camera app not found / ما في تطبيق كاميرا")
        return
      }
      pendingPhotoPath=file.absolutePath
      pendingPhotoTarget=target
      pendingShopId=shopId
      pendingItemId=itemId
      startActivityForResult(i,REQ_CAMERA)
    }catch(e:Throwable){
      toast("Camera error: ${e.message}")
    }
  }

  @Deprecated("deprecated")
  override fun onActivityResult(rc:Int,res:Int,data:Intent?){
    super.onActivityResult(rc,res,data)
    if(rc==REQ_CAMERA){
      val path=pendingPhotoPath
      if(res==RESULT_OK && path!=null && File(path).exists()){
        if(pendingPhotoTarget=="SHOP"){
          db.setShopPhoto(pendingShopId,path)
          toast("Shop photo saved ✓")
          openShop(pendingShopId)
        }else if(pendingPhotoTarget=="ITEM"){
          db.addItemPhoto(pendingItemId,path)
          toast("Photo added ✓")
          openItemEditor(pendingShopId,pendingItemId)
        }
      }else{
        path?.let{try{File(it).delete()}catch(_:Throwable){}}
        if(pendingPhotoTarget=="SHOP") openShop(pendingShopId)
        else if(pendingPhotoTarget=="ITEM") openItemEditor(pendingShopId,pendingItemId)
      }
      pendingPhotoPath=null
      pendingPhotoTarget="NONE"
    }
  }

  private fun showSavedShop(shopId:Long){
    val sh=db.getShop(shopId)?:return showHome()
    screen="saved:$shopId"
    val(s,r)=root()
    r.addView(title(shopLabel(sh)))
    r.addView(small("ONE SHOP FILE / ملف محل واحد"))

    if(sh.shopPhoto.isNotBlank() && File(sh.shopPhoto).exists()){
      val iv=thumb(sh.shopPhoto,190)
      iv.layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(190)).apply{setMargins(0,0,0,dp(10))}
      r.addView(iv)
    }

    val contact=listOf(
      sh.contact.takeIf{it.isNotBlank()},
      sh.phone.takeIf{it.isNotBlank()},
      sh.whatsapp.takeIf{it.isNotBlank()}?.let{"WhatsApp: $it"},
      sh.wechat.takeIf{it.isNotBlank()}?.let{"WeChat: $it"},
      sh.address.takeIf{it.isNotBlank()}
    ).filterNotNull().joinToString("\n")
    if(contact.isNotBlank()) r.addView(TextView(this).apply{text=contact;textSize=16f;setTextColor(ink);setPadding(0,0,0,dp(10))})

    val items=db.getShopItems(shopId)
    r.addView(TextView(this).apply{
      text="${items.size} items / أصناف"
      textSize=19f;setTypeface(typeface,android.graphics.Typeface.BOLD);setTextColor(blue)
    },lp())

    items.forEachIndexed{index,it->
      val photos=db.getItemPhotos(it.id)
      val c=card()
      c.addView(TextView(this).apply{
        text="${index+1}. ${it.name}\nPrice: ${it.price} ${it.currency}   MOQ: ${it.moq}"
        textSize=17f;setTextColor(ink);setTypeface(typeface,android.graphics.Typeface.BOLD)
      })
      if(it.notes.isNotBlank()) c.addView(small(it.notes))
      if(photos.isNotEmpty()){
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        photos.take(4).forEach{p->if(File(p.path).exists())row.addView(thumb(p.path,70))}
        c.addView(row)
      }
      r.addView(c,lp(8))
    }

    r.addView(button("Home / الرئيسية",blue){showHome()})
    setContentView(s)
  }

  private fun dateText(ms:Long):String=
    SimpleDateFormat("dd MMM yyyy HH:mm",Locale.US).format(Date(ms))

  private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()

  override fun onBackPressed(){
    when{
      screen.startsWith("item:")->{
        val p=screen.split(":")
        openShop(p.getOrNull(1)?.toLongOrNull()?:0L)
      }
      screen.startsWith("shop:")->showHome()
      screen.startsWith("saved:")->showHome()
      else->finish()
    }
  }
}
