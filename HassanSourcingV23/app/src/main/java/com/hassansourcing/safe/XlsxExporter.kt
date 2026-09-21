package com.hassansourcing.safe

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxExporter {
  private fun e(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
  private fun col(i:Int):String{var n=i+1;val b=StringBuilder();while(n>0){val r=(n-1)%26;b.insert(0,('A'.code+r).toChar());n=(n-1)/26};return b.toString()}
  fun write(output:OutputStream,suppliers:List<Supplier>,products:List<Product>){
    val rows=mutableListOf<List<String>>()
    rows+=listOf("Supplier","Contact","Phone","WhatsApp","WeChat","Email","Address","Item","Price","Currency","MOQ","Carton Qty","Carton Size","Weight","Notes")
    suppliers.forEach{s->
      val ps=products.filter{it.supplierId==s.id}
      if(ps.isEmpty()) rows+=listOf(s.name,s.contact,s.phone,s.whatsapp,s.wechat,s.email,s.address,"","","","","","","",s.notes)
      else ps.forEach{p->rows+=listOf(s.name,s.contact,s.phone,s.whatsapp,s.wechat,s.email,s.address,p.name,p.price,p.currency,p.moq,p.cartonQty,p.cartonSize,p.weight,listOf(s.notes,p.notes).filter{it.isNotBlank()}.joinToString(" | "))}
    }
    val sheetRows=buildString{
      rows.forEachIndexed{r,row->val rn=r+1;append("<row r=\"$rn\">");row.forEachIndexed{c,v->val ref="${col(c)}$rn";append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${e(v)}</t></is></c>")};append("</row>")}
    }
    val types="""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""
    val rels="""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    val wb="""<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sourcing" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    val wbr="""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
    val sheet="""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$sheetRows</sheetData></worksheet>"""
    ZipOutputStream(output).use{z->fun add(n:String,t:String){z.putNextEntry(ZipEntry(n));z.write(t.toByteArray());z.closeEntry()};add("[Content_Types].xml",types);add("_rels/.rels",rels);add("xl/workbook.xml",wb);add("xl/_rels/workbook.xml.rels",wbr);add("xl/worksheets/sheet1.xml",sheet)}
  }
}
