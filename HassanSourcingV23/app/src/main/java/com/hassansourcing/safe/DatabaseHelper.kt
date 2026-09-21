package com.hassansourcing.safe

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Supplier(val id:Long,val name:String,val contact:String,val phone:String,val whatsapp:String,val wechat:String,val email:String,val address:String,val notes:String)
data class Product(val id:Long,val supplierId:Long,val name:String,val price:String,val currency:String,val moq:String,val cartonQty:String,val cartonSize:String,val weight:String,val notes:String)

class DatabaseHelper(context: Context): SQLiteOpenHelper(context,"hassan_sourcing_safe.db",null,1){
  override fun onCreate(db: SQLiteDatabase){
    db.execSQL("CREATE TABLE suppliers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,contact TEXT,phone TEXT,whatsapp TEXT,wechat TEXT,email TEXT,address TEXT,notes TEXT)")
    db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT,supplierId INTEGER NOT NULL,name TEXT NOT NULL,price TEXT,currency TEXT,moq TEXT,cartonQty TEXT,cartonSize TEXT,weight TEXT,notes TEXT)")
  }
  override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){}
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
}
