package com.hassansourcing.safe

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Supplier(val id:Long,val name:String,val contact:String,val phone:String,val whatsapp:String,val wechat:String,val email:String,val address:String,val notes:String)
data class Product(val id:Long,val supplierId:Long,val name:String,val price:String,val currency:String,val moq:String,val cartonQty:String,val cartonSize:String,val weight:String,val notes:String)

data class Shop(
  val id:Long,
  val name:String,
  val contact:String,
  val phone:String,
  val whatsapp:String,
  val wechat:String,
  val address:String,
  val notes:String,
  val shopPhoto:String,
  val createdAt:Long,
  val status:String
)

data class ShopItem(
  val id:Long,
  val shopId:Long,
  val name:String,
  val price:String,
  val currency:String,
  val moq:String,
  val notes:String
)

data class ItemPhoto(val id:Long,val itemId:Long,val path:String)

class DatabaseHelper(context: Context): SQLiteOpenHelper(context,"hassan_sourcing_safe.db",null,2){
  override fun onCreate(db: SQLiteDatabase){
    createLegacyTables(db)
    createShopTables(db)
  }

  private fun createLegacyTables(db:SQLiteDatabase){
    db.execSQL("CREATE TABLE IF NOT EXISTS suppliers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,contact TEXT,phone TEXT,whatsapp TEXT,wechat TEXT,email TEXT,address TEXT,notes TEXT)")
    db.execSQL("CREATE TABLE IF NOT EXISTS products(id INTEGER PRIMARY KEY AUTOINCREMENT,supplierId INTEGER NOT NULL,name TEXT NOT NULL,price TEXT,currency TEXT,moq TEXT,cartonQty TEXT,cartonSize TEXT,weight TEXT,notes TEXT)")
  }

  private fun createShopTables(db:SQLiteDatabase){
    db.execSQL("CREATE TABLE IF NOT EXISTS shops(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,contact TEXT,phone TEXT,whatsapp TEXT,wechat TEXT,address TEXT,notes TEXT,shopPhoto TEXT,createdAt INTEGER NOT NULL,status TEXT NOT NULL DEFAULT 'DRAFT')")
    db.execSQL("CREATE TABLE IF NOT EXISTS shop_items(id INTEGER PRIMARY KEY AUTOINCREMENT,shopId INTEGER NOT NULL,name TEXT NOT NULL,price TEXT,currency TEXT,moq TEXT,notes TEXT)")
    db.execSQL("CREATE TABLE IF NOT EXISTS item_photos(id INTEGER PRIMARY KEY AUTOINCREMENT,itemId INTEGER NOT NULL,path TEXT NOT NULL)")
  }

  override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){
    if(oldVersion<2) createShopTables(db)
  }

  fun addSupplier(name:String,contact:String,phone:String,whatsapp:String,wechat:String,email:String,address:String,notes:String):Long{
    val v=ContentValues().apply{put("name",name.trim());put("contact",contact.trim());put("phone",phone.trim());put("whatsapp",whatsapp.trim());put("wechat",wechat.trim());put("email",email.trim());put("address",address.trim());put("notes",notes.trim())}
    return writableDatabase.insert("suppliers",null,v)
  }

  fun addProduct(supplierId:Long,name:String,price:String,currency:String,moq:String,cartonQty:String,cartonSize:String,weight:String,notes:String):Long{
    val v=ContentValues().apply{put("supplierId",supplierId);put("name",name.trim());put("price",price.trim());put("currency",currency.trim());put("moq",moq.trim());put("cartonQty",cartonQty.trim());put("cartonSize",cartonSize.trim());put("weight",weight.trim());put("notes",notes.trim())}
    return writableDatabase.insert("products",null,v)
  }

  fun getSuppliers():List<Supplier>{
    val out=mutableListOf<Supplier>()
    readableDatabase.rawQuery("SELECT * FROM suppliers ORDER BY id DESC",null).use{c->while(c.moveToNext())out+=Supplier(c.getLong(0),c.getString(1)?:"",c.getString(2)?:"",c.getString(3)?:"",c.getString(4)?:"",c.getString(5)?:"",c.getString(6)?:"",c.getString(7)?:"",c.getString(8)?:"")}
    return out
  }

  fun getProducts():List<Product>{
    val out=mutableListOf<Product>()
    readableDatabase.rawQuery("SELECT * FROM products ORDER BY id DESC",null).use{c->while(c.moveToNext())out+=Product(c.getLong(0),c.getLong(1),c.getString(2)?:"",c.getString(3)?:"",c.getString(4)?:"",c.getString(5)?:"",c.getString(6)?:"",c.getString(7)?:"",c.getString(8)?:"",c.getString(9)?:"")}
    return out
  }

  fun createDraftShop():Long{
    val v=ContentValues().apply{
      put("name","")
      put("createdAt",System.currentTimeMillis())
      put("status","DRAFT")
    }
    return writableDatabase.insert("shops",null,v)
  }

  fun updateShop(id:Long,name:String,contact:String,phone:String,whatsapp:String,wechat:String,address:String,notes:String){
    val v=ContentValues().apply{
      put("name",name.trim());put("contact",contact.trim());put("phone",phone.trim());put("whatsapp",whatsapp.trim());put("wechat",wechat.trim());put("address",address.trim());put("notes",notes.trim())
    }
    writableDatabase.update("shops",v,"id=?",arrayOf(id.toString()))
  }

  fun setShopPhoto(id:Long,path:String){
    writableDatabase.update("shops",ContentValues().apply{put("shopPhoto",path)},"id=?",arrayOf(id.toString()))
  }

  fun finishShop(id:Long){
    writableDatabase.update("shops",ContentValues().apply{put("status","SAVED")},"id=?",arrayOf(id.toString()))
  }

  fun getDraftShop():Shop?{
    readableDatabase.rawQuery("SELECT id,name,contact,phone,whatsapp,wechat,address,notes,shopPhoto,createdAt,status FROM shops WHERE status='DRAFT' ORDER BY id DESC LIMIT 1",null).use{c->
      if(c.moveToFirst()) return shopFromCursor(c)
    }
    return null
  }

  fun getShop(id:Long):Shop?{
    readableDatabase.rawQuery("SELECT id,name,contact,phone,whatsapp,wechat,address,notes,shopPhoto,createdAt,status FROM shops WHERE id=? LIMIT 1",arrayOf(id.toString())).use{c->
      if(c.moveToFirst()) return shopFromCursor(c)
    }
    return null
  }

  fun getSavedShops():List<Shop>{
    val out=mutableListOf<Shop>()
    readableDatabase.rawQuery("SELECT id,name,contact,phone,whatsapp,wechat,address,notes,shopPhoto,createdAt,status FROM shops WHERE status='SAVED' ORDER BY id DESC",null).use{c->
      while(c.moveToNext()) out+=shopFromCursor(c)
    }
    return out
  }

  private fun shopFromCursor(c:android.database.Cursor)=Shop(
    c.getLong(0),c.getString(1)?:"",c.getString(2)?:"",c.getString(3)?:"",c.getString(4)?:"",c.getString(5)?:"",c.getString(6)?:"",c.getString(7)?:"",c.getString(8)?:"",c.getLong(9),c.getString(10)?:""
  )

  fun addShopItem(shopId:Long,name:String,price:String,currency:String,moq:String,notes:String):Long{
    val v=ContentValues().apply{
      put("shopId",shopId);put("name",name.trim());put("price",price.trim());put("currency",currency.trim());put("moq",moq.trim());put("notes",notes.trim())
    }
    return writableDatabase.insert("shop_items",null,v)
  }

  fun getShopItems(shopId:Long):List<ShopItem>{
    val out=mutableListOf<ShopItem>()
    readableDatabase.rawQuery("SELECT id,shopId,name,price,currency,moq,notes FROM shop_items WHERE shopId=? ORDER BY id",arrayOf(shopId.toString())).use{c->
      while(c.moveToNext()) out+=ShopItem(c.getLong(0),c.getLong(1),c.getString(2)?:"",c.getString(3)?:"",c.getString(4)?:"",c.getString(5)?:"",c.getString(6)?:"")
    }
    return out
  }

  fun addItemPhoto(itemId:Long,path:String):Long{
    return writableDatabase.insert("item_photos",null,ContentValues().apply{put("itemId",itemId);put("path",path)})
  }

  fun getItemPhotos(itemId:Long):List<ItemPhoto>{
    val out=mutableListOf<ItemPhoto>()
    readableDatabase.rawQuery("SELECT id,itemId,path FROM item_photos WHERE itemId=? ORDER BY id",arrayOf(itemId.toString())).use{c->
      while(c.moveToNext()) out+=ItemPhoto(c.getLong(0),c.getLong(1),c.getString(2)?:"")
    }
    return out
  }

  fun savedShopCount():Int{
    readableDatabase.rawQuery("SELECT COUNT(*) FROM shops WHERE status='SAVED'",null).use{c->if(c.moveToFirst())return c.getInt(0)}
    return 0
  }
}
