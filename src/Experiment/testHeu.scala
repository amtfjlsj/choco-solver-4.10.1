import java.io.File
import java.lang.System.out
import java.util
import java.util.{Arrays, Comparator, Map}

import amtf.TimeCount
import amtf.parser.XCSPParser2
import com.github.tototoshi.csv.CSVWriter
import org.chocosolver.solver.{Model, Solver}
import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.constraints.extension.Tuples
import org.chocosolver.solver.constraints.extension.nary.{PropCompactTable, PropTableStr2}
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.impl.BitsetIntVarImpl

import scala.collection.mutable.ArrayBuffer
import scala.util.Sorting
import scala.xml.XML
import scala.collection.JavaConversions._
import org.chocosolver.solver.search.strategy.Search.activityBasedSearch
import org.chocosolver.solver.search.strategy.Search.AbsCondomOverWDegSearch
import org.chocosolver.solver.search.strategy.Search.CaCdOverWDegSearch
import org.chocosolver.solver.search.strategy.Search.dualSearch
import org.chocosolver.solver.search.strategy.Search.dualSearch1
import org.chocosolver.solver.search.strategy.Search.domOverWDegSearch

object testHeu {
  var name = ""
  var pType = ""
  var varType = ""
  var heuName = ""


  def main(args: Array[String]): Unit = {

    if (args.isEmpty)
      argEmpty()
    else
      withArgs(args)
  }

  def argEmpty(): Unit = {
    println(s"hardware cocurrency: ${Runtime.getRuntime.availableProcessors()}")
    val file = XML.loadFile("D:\\java\\ctrl-choco2\\src\\Experiment\\FDEFolders.xml")
    val inputRoot = (file \\ "inputRoot").text
    val outputRoot = (file \\ "outputRoot").text
    val outputFolder = (file \\ "outputFolder").text
    val inputFolderNodes = file \\ "folder"

    for (fn <- inputFolderNodes) {
      val fmt = (fn \\ "@format").text.toInt
      val folderStr = fn.text
      val inputPath = inputRoot + "/" + folderStr
      val files = getFiles(new File(inputPath))
      Sorting.quickSort(files)
      println("exp files:")
      files.foreach(f => println(f.getName))
      val resFile = new File(outputRoot + "/" + outputFolder + folderStr + ".csv")
      val writer = CSVWriter.open(resFile)
      val titleLine = ArrayBuffer[String]()
      titleLine += "name"
      titleLine ++= Array("algorithm", "nodes", "time")
      titleLine ++= Array("algorithm", "nodes", "time")
      titleLine ++= Array("algorithm", "nodes", "time")
      titleLine ++= Array("algorithm", "nodes", "time")
      titleLine ++= Array("algorithm", "nodes", "time")


      writer.writeRow(titleLine)
      var dataLine = new ArrayBuffer[String](titleLine.length)

      for (f <- files) {
        println("Build Model: "+ f.getName)
        dataLine.clear()
        dataLine += f.getName()

//        val parser = new XCSPParser2

        //-------------算法-------------

//        val model = new Model
//        try{
//          parser.model(model, f.getPath)
//        }catch {
//          case e: Exception =>
//            e.printStackTrace()
//        }
//
//        val ho = model.getHooks
//
//        var decVars = model.getHook("decisions").asInstanceOf[Array[IntVar]]
//        if (decVars == null) {
//          decVars = parser.mvars.values.toArray(new Array[IntVar](parser.mvars.size))
//        }
//
//        val solver = model.getSolver
//        solver.setSearch(activityBasedSearch(decVars:_*))
//        solver.limitTime(900000)
//        solver.solve
//        out.println("node: " + solver.getNodeCount)
//        out.println("time: " + solver.getTimeCount + "s")
//        dataLine += "ABS"
//        dataLine += solver.getMeasures.getNodeCount.toString
//        dataLine += solver.getMeasures.getTimeCount.toString
//
//        val solver1 = model.getSolver
////        solver1.setSearch(activityBasedSearch(decVars:_*))
//        solver1.limitTime(900000)
//        solver1.solve
//        out.println("node: " + solver1.getNodeCount)
//        out.println("time: " + solver1.getTimeCount + "s")
//        dataLine += "dom/wdeg"
//        dataLine += solver1.getMeasures.getNodeCount.toString
//        dataLine += solver1.getMeasures.getTimeCount.toString
//
//
//        val solver2 = model.getSolver
//        solver2.setSearch(AbsCondomOverWDegSearch(decVars:_*))
//        solver2.limitTime(900000)
//        solver2.solve
//        out.println("node: " + solver2.getNodeCount)
//        out.println("time: " + solver2.getTimeCount + "s")
//        dataLine += "dom/wdeg+abscon"
//        dataLine += solver2.getMeasures.getNodeCount.toString
//        dataLine += solver2.getMeasures.getTimeCount.toString
//
//
//        val solver3 = model.getSolver
//        solver3.setSearch(CaCdOverWDegSearch(decVars:_*))
//        solver3.limitTime(900000)
//        solver3.solve
//        out.println("node: " + solver3.getNodeCount)
//        out.println("time: " + solver3.getTimeCount + "s")
//        dataLine += "dom/wdeg+cacd"
//        dataLine += solver3.getMeasures.getNodeCount.toString
//        dataLine += solver3.getMeasures.getTimeCount.toString
//
//        val solver4 = model.getSolver
//        solver4.setSearch(dualSearch(decVars:_*))
//        solver4.limitTime(900000)
//        solver4.solve
//        out.println("node: " + solver4.getNodeCount)
//        out.println("time: " + solver4.getTimeCount + "s")
//        dataLine += "dom/wdeg+dual"
//        dataLine += solver4.getMeasures.getNodeCount.toString
//        dataLine += solver4.getMeasures.getTimeCount.toString

        dataLine+="ABS"
        var a=solver(f,"abs");
        dataLine += a._1
        dataLine += a._2

        dataLine+="dom/wdeg"
        a=solver(f,"domE");
        dataLine += a._1
        dataLine += a._2

        dataLine+="dom/wdeg+abscon"
        a=solver(f,"domA");
        dataLine += a._1
        dataLine += a._2

        dataLine+="dom/wdeg+cacd"
        a=solver(f,"domC");
        dataLine += a._1
        dataLine += a._2

        dataLine+="dom/wdeg+dual"
        a=solver(f,"domD");
        dataLine += a._1
        dataLine += a._2


        writer.writeRow(dataLine)
        println("end: " + f.getName)
      }
      writer.close()
      println("-----" + folderStr + " done!-----")
    }
    println("-----All done!-----")
  }

  def withArgs(args: Array[String]): Unit = {

  }

  def solver(f:File ,algorithm: String):(String,String)={

    val parser = new XCSPParser2
    val model = new Model
    try
      parser.model(model, "D:\\dataheu\\Random-RB-low\\frb-30-15-1.xml")
    catch {
      case e: Exception =>
        e.printStackTrace()
    }

    val ho = model.getHooks

    var decVars = model.getHook("decisions").asInstanceOf[Array[IntVar]]
    if (decVars == null) {
      decVars = parser.mvars.values.toArray(new Array[IntVar](parser.mvars.size))
    }
    val solver = model.getSolver
//    algorithm match {
//      case "abs" => solver.setSearch(activityBasedSearch(decVars:_*))
//      case "domA" => solver.setSearch(AbsCondomOverWDegSearch(decVars:_*))
//      case "domC" => solver.setSearch(CaCdOverWDegSearch(decVars:_*))
//      case "domD" => solver.setSearch(dualSearch(decVars:_*))
//      case "domE" => solver.setSearch(domOverWDegSearch(decVars:_*))
//    }

    solver.setSearch(domOverWDegSearch(decVars:_*))

    solver.limitTime(900000)
    solver.solve
    out.println("node: " + solver.getNodeCount)
    out.println("time: " + solver.getTimeCount + "s")
    return (solver.getMeasures.getNodeCount.toString,solver.getMeasures.getTimeCount.toString)
  }

  //获取指定单个目录下所有文件
  def getFiles(dir: File): Array[File] = {
    dir.listFiles.filter(_.isFile) ++
      dir.listFiles.filter(_.isDirectory).flatMap(getFiles)
  }
}
