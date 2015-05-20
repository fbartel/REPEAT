/*
 * Registration Performance Assessment Tool (REPEAT)
 *
 * Copyright (C) 2015  Andreas Schuh
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact: Andreas Schuh <andreas.schuh.84@gmail.com>
 */

package com.andreasschuh.repeat.workflow

import java.nio.file.{Path, Paths}

import scala.language.reflectiveCalls

import com.andreasschuh.repeat.core.{Environment => Env, _}
import com.andreasschuh.repeat.puzzle._

import org.openmole.core.dsl._
import org.openmole.core.workflow.data.Prototype
import org.openmole.core.workflow.transition.Condition
import org.openmole.plugin.grouping.batch._
import org.openmole.plugin.hook.display.DisplayHook
import org.openmole.plugin.hook.file._
import org.openmole.plugin.sampling.combine._
import org.openmole.plugin.sampling.csv._
import org.openmole.plugin.task.scala._
import org.openmole.plugin.tool.pattern.{Skip, Switch, Case}


/**
 * Run registration with different parameters and store results for evaluation
 */
object Evaluate {

  /**
   * Construct workflow puzzle
   *
   * @param reg Registration info
   *
   * @return Workflow puzzle for running the registration and generating the results needed for quality assessment
   */
  def apply(reg: Registration) = {

    // -----------------------------------------------------------------------------------------------------------------
    // Constants
    import Prefix._
    import Dataset.{imgDir => _, segDir => _, _}
    import Workspace.{imgDir, segDir, dofAff, dofPre, dofSuf, logDir, logSuf}

    val labels = Overlap.labels.mkString(",")
    val groups = Overlap.groups.mkString(",")

    // Input/intermediate files
    val tgtImPath  = Paths.get(    imgDir.getAbsolutePath, imgPre + "${tgtId}"          +     imgSuf).toString
    val srcImPath  = Paths.get(    imgDir.getAbsolutePath, imgPre + "${srcId}"          +     imgSuf).toString
    val tgtSegPath = Paths.get(    segDir.getAbsolutePath, segPre + "${tgtId}"          +     segSuf).toString
    val srcSegPath = Paths.get(    segDir.getAbsolutePath, segPre + "${srcId}"          +     segSuf).toString
    val iniDofPath = Paths.get(    dofAff.getAbsolutePath, dofPre + "${tgtId},${srcId}" +     dofSuf).toString
    val affDofPath = Paths.get(reg.affDir.getAbsolutePath, dofPre + "${tgtId},${srcId}" + reg.affSuf).toString
    val outDofPath = Paths.get(reg.dofDir.getAbsolutePath, dofPre + "${tgtId},${srcId}" +     dofSuf).toString
    val outJacPath = Paths.get(reg.dofDir.getAbsolutePath, dofPre + "${tgtId},${srcId}" + reg.jacSuf).toString
    val outImPath  = Paths.get(reg.imgDir.getAbsolutePath, imgPre + "${srcId}-${tgtId}" +     imgSuf).toString
    val outSegPath = Paths.get(reg.segDir.getAbsolutePath, segPre + "${srcId}-${tgtId}" +     segSuf).toString
    val outLogPath = Paths.get(    logDir.getAbsolutePath, "${regId}-${parId}", "${tgtId},${srcId}" + logSuf).toString

    // Evaluation result files
    val runTimeCsvPath = Paths.get(reg.resDir.getAbsolutePath, "Time.csv").toString
    val avgTimeCsvPath = Paths.get(reg.sumDir.getAbsolutePath, "Time.csv").toString

    val dscRegAvgCsvName = Overlap.summary.replace("${measure}", "DSC")
    val dscValuesCsvPath = Paths.get(reg.resDir.getAbsolutePath, "DSC_Label.csv" ).toString
    val dscGrpAvgCsvPath = Paths.get(reg.resDir.getAbsolutePath, "DSC_Mean.csv"  ).toString
    val dscGrpStdCsvPath = Paths.get(reg.resDir.getAbsolutePath, "DSC_Sigma.csv" ).toString
    val dscRegAvgCsvPath = Paths.get(reg.sumDir.getAbsolutePath, dscRegAvgCsvName).toString

    val jsiRegAvgCsvName = Overlap.summary.replace("${measure}", "JSI")
    val jsiValuesCsvPath = Paths.get(reg.resDir.getAbsolutePath, "JSI_Label.csv" ).toString
    val jsiGrpAvgCsvPath = Paths.get(reg.resDir.getAbsolutePath, "JSI_Mean.csv"  ).toString
    val jsiGrpStdCsvPath = Paths.get(reg.resDir.getAbsolutePath, "JSI_Sigma.csv" ).toString
    val jsiRegAvgCsvPath = Paths.get(reg.sumDir.getAbsolutePath, jsiRegAvgCsvName).toString

    // Which intermediate result files to keep
    val keepOutDof = true
    val keepOutIm  = true
    val keepOutSeg = true
    val keepOutJac = true

    // Which evaluation measures to compute
    val timeEnabled = true
    val dscEnabled  = Overlap.measures contains Overlap.DSC
    val jsiEnabled  = Overlap.measures contains Overlap.JSI

    val regSet = "{regId=${regId}, parId=${parId}, tgtId=${tgtId}, srcId=${srcId}}"
    val avgSet = "{regId=${regId}, parId=${parId}}"

    // -----------------------------------------------------------------------------------------------------------------
    // Variables

    // Input/output variables
    val regId   = Val[String]              // ID of registration
    val parIdx  = Val[Int]                 // Row index of parameter set
    val parId   = Val[String]              // ID of parameter set
    val parVal  = Val[Map[String, String]] // Map from parameter name to value
    val tgtId   = Val[Int]                 // ID of target image
    val tgtIm   = Val[Path]                // Fixed target image
    val tgtSeg  = Val[Path]                // Segmentation of target image
    val srcId   = Val[Int]                 // ID of source image
    val srcIm   = Val[Path]                // Moving source image
    val iniDof  = Val[Path]                // Pre-computed affine transformation from target to source
    val affDof  = Val[Path]                // Affine transformation converted to input format
    val outDof  = Val[Path]                // Output transformation converted to IRTK format
    val outIm   = Val[Path]                // Deformed source image
    val outSeg  = Val[Path]                // Deformed source segmentation
    val outJac  = Val[Path]                // Jacobian determinant map
    val outLog  = Val[Path]                // Registration command output log file

    val outSegModified = Val[Boolean]      // Whether deformed source segmentation was newly created

    // Evaluation results
    val runTime = Val[Array[Double]]       // Runtime of registration command
    val avgTime = Val[Array[Double]]       // Mean runtime over all registrations for a given set of parameters

    val runTimeValid = Val[Boolean]        // Whether runtime measurements read from previous results CSV are valid
    val avgTimeValid = Val[Boolean]        // ...

    val dscValues = Val[Array[Double]]     // Dice similarity coefficient (DSC) for each label and segmentation
    val dscGrpAvg = Val[Array[Double]]     // Mean DSC for each label group and segmentation
    val dscGrpStd = Val[Array[Double]]     // Standard deviation of DSC for each label group and segmentation
    val dscRegAvg = Val[Array[Double]]     // Average mean DSC for a given set of registration parameters

    val dscValuesValid = Val[Boolean]      // Whether DSC values read from previous CSV are valid
    val dscGrpAvgValid = Val[Boolean]      // ...
    val dscGrpStdValid = Val[Boolean]      // ...
    val dscRegAvgValid = Val[Boolean]      // ...

    val jsiValues = Val[Array[Double]]     // Jaccard similarity index (JSI) for each label and segmentation
    val jsiGrpAvg = Val[Array[Double]]     // Mean JSI for each label group and segmentation
    val jsiGrpStd = Val[Array[Double]]     // Standard deviation of JSI for each label group and segmentation
    val jsiRegAvg = Val[Array[Double]]     // Average mean JSI for a given set of registration parameters

    val jsiValuesValid = Val[Boolean]      // Whether JSI values read from previous CSV are valid
    val jsiGrpAvgValid = Val[Boolean]      // ...
    val jsiGrpStdValid = Val[Boolean]      // ...
    val jsiRegAvgValid = Val[Boolean]      // ...

    // -----------------------------------------------------------------------------------------------------------------
    // Samplings
    val paramSampling = CSVToMapSampling(reg.parCsv, parVal)
    val tgtIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", tgtId))
    val srcIdSampling = CSVSampling(imgCsv) set (columns += ("Image ID", srcId))
    val imageSampling = (tgtIdSampling x srcIdSampling) filter (if (reg.isSym) "tgtId < srcId" else "tgtId != srcId")

    // -----------------------------------------------------------------------------------------------------------------
    // Auxiliaries

    // NOP puzzle
    val nop = Capsule(EmptyTask() set (name := s"${reg.id}-NOP")).toPuzzle

    // Exploration tasks
    def setRegId =
      EmptyTask() set (
        name    := s"${reg.id}-SetRegId",
        outputs += regId,
        regId   := reg.id
      )

    def forEachPar =
      ExplorationTask(paramSampling zipWithIndex parIdx) set (
        name    := s"${reg.id}-ForEachPar",
        inputs  += regId,
        outputs += regId
      )

    def setParId =
      ScalaTask(
        """
          | val parId  = input.parVal.getOrElse("ID", f"$parIdx%02d")
          | val parVal = input.parVal - "ID"
        """.stripMargin
      ) set (
        name    := s"${reg.id}-SetParId",
        inputs  += (regId, parIdx, parVal),
        outputs += (regId, parId, parVal)
      )

    def demux(taskName: String, input: Prototype[_]*) = {
      val inputNames = input.toSeq.map(_.name)
      val task =
        ScalaTask(inputNames.map(name => s"val $name = input.$name.head").mkString("\n")) set (
          name := s"${reg.id}-$taskName"
        )
      input.foreach(p => {
        task.addInput(p.toArray)
        task.addOutput(p)
      })
      task
    }

    def forEachImPair =
      ExplorationTask(imageSampling) set (
        name    := s"${reg.id}-ForEachImPair",
        inputs  += regId,
        outputs += regId
      )

    // Delete table with summary results which can be recomputed from individual result tables
    def deleteTable(path: String, enabled: Boolean) =
      if (enabled)
        ScalaTask(
          s"""
            | val table = Paths.get(s"$path")
            | if (Files.exists(table)) {
            |   Files.delete(table)
            |   println("${DONE}Delete " + table.toString)
            | }
          """.stripMargin
        ) set (
          name    := s"${reg.id}-DeleteTable",
          imports += "java.nio.file.{Paths, Files}",
          inputs  += regId,
          outputs += regId
        )
      else
        EmptyTask() set (
          name    := s"${reg.id}-KeepTable",
          inputs  += regId,
          outputs += regId
        )

    // Make copy of previous result tables and merge them with previously copied results to ensure none are lost
    def backupTablePath(path: String) = {
      val p = Paths.get(path)
      p.getParent.resolve(FileUtil.hidden(p.getFileName.toString)).toString
    }

    def backupTable(path: String, enabled: Boolean) =
      if (enabled)
        ScalaTask(
          s"""
            | val from = new File(s"$path")
            | val to   = new File(s"${backupTablePath(path)}")
            | if (from.exists) {
            |   val l1 = if (to.exists) fromFile(to).getLines().toList.drop(1) else List[String]()
            |   val l2 = fromFile(from).getLines().toList
            |   val fw = new FileWriter(to)
            |   try {
            |     fw.write(l2.head + "\\n")
            |     val l: List[String] = (l1 ::: l2.tail).groupBy( _.split(",").take(2).mkString(",") ).map(_._2.last)(breakOut)
            |     l.sortBy( _.split(",").take(2).mkString(",") ).foreach( row => fw.write(row + "\\n") )
            |   }
            |   finally fw.close()
            |   Files.delete(from.toPath)
            |   println("${DONE}Backup " + from.getPath)
            | }
          """.stripMargin
        ) set (
          name    := s"${reg.id}-BackupTable",
          imports += ("java.io.{File, FileWriter}", "java.nio.file.Files", "scala.io.Source.fromFile", "scala.collection.breakOut"),
          inputs  += (regId, parId),
          outputs += (regId, parId)
        )
      else
        EmptyTask() set (
          name    := s"${reg.id}-KeepTable",
          inputs  += (regId, parId),
          outputs += (regId, parId)
        )

    val backupTablesEnd =
      Capsule(
        ScalaTask("val regId = input.regId.head") set (
          name    := s"${reg.id}-BackupTablesEnd",
          inputs  += regId.toArray,
          outputs += regId
        )
      )

    // Finalize result table, appending non-overwritten previous results again and sorting the final table
    def finalizeTable(path: String, enabled: Boolean) =
      if (enabled)
        ScalaTask(
          s"""
            | val from = new File(s"${backupTablePath(path)}")
            | val to   = new File(s"$path")
            | if (from.exists) {
            |   val l1 = fromFile(from).getLines().toList
            |   val l2 = if (to.exists) fromFile(to).getLines().toList.tail else List[String]()
            |   val fw = new FileWriter(to)
            |   try {
            |     fw.write(l1.head + "\\n")
            |     val l: List[String] = (l1.tail ::: l2).groupBy( _.split(",").take(2).mkString(",") ).map(_._2.last)(breakOut)
            |     l.sortBy( _.split(",").take(2).mkString(",") ).foreach( row => fw.write(row + "\\n") )
            |   }
            |   finally fw.close()
            |   Files.delete(from.toPath)
            |   println("${DONE}Finalize " + to.getPath)
            | }
          """.stripMargin
        ) set (
          name    := s"${reg.id}-FinalizeTable",
          imports += ("java.io.{File, FileWriter}", "java.nio.file.Files", "scala.io.Source.fromFile", "scala.collection.breakOut"),
          inputs  += (regId, parId),
          outputs += (regId, parId)
        )
      else
        EmptyTask() set (
          name    := s"${reg.id}-KeepTable",
          inputs  += (regId, parId),
          outputs += (regId, parId)
        )

    // Initial conversion of affine input transformation
    val convertDofToAffBegin =
      Capsule(
        ScalaTask(
          s"""
            | val iniDof = Paths.get(s"$iniDofPath")
          """.stripMargin
        ) set (
          name    := s"${reg.id}-ConvertDofToAffBegin",
          imports += "java.nio.file.Paths",
          inputs  += (regId, tgtId, srcId),
          outputs += (regId, tgtId, srcId, iniDof)
        )
      )

    val convertDofToAffEnd =
      Capsule(
        ScalaTask("val regId = input.regId.head") set (
          name    := s"${reg.id}-ConvertDofToAffEnd",
          inputs  += regId.toArray,
          outputs += regId
        )
      )

    // Auxiliary tasks for main registration task
    val registerImagesBegin =
      Capsule(
        ScalaTask(
          s"""
            | val tgtIm  = Paths.get(s"$tgtImPath")
            | val srcIm  = Paths.get(s"$srcImPath")
            | val affDof = Paths.get(s"$affDofPath")
            | val outDof = Paths.get(s"$outDofPath")
            | val outLog = Paths.get(s"$outLogPath")
          """.stripMargin
        ) set (
          name    := s"${reg.id}-RegisterImagesBegin",
          imports += "java.nio.file.Paths",
          inputs  += (regId, parId, tgtId, srcId),
          outputs += (regId, parId, tgtId, srcId, tgtIm, srcIm, affDof, outDof, outLog)
        ),
        strainer = true
      )

    val registerImagesEnd =
      Capsule(
        EmptyTask() set (
          name    := s"${reg.id}-RegisterImagesEnd",
          inputs  += (regId, parId, tgtId, srcId, outDof, runTime, runTimeValid),
          outputs += (regId, parId, tgtId, srcId, outDof, runTime, runTimeValid)
        )
      )

    // Read previous result from backup table to save recomputation if nothing changed
    def readFromTable(path: String, p: Prototype[Array[Double]], isValid: Prototype[Boolean],
                      n: Int = 0, invalid: Double = Double.NaN, enabled: Boolean = true) =
      Capsule(
        ScalaTask(
          s"""
            | val ${p.name} =
            |   if ($enabled)
            |     try {
            |       val file  = new File(s"${backupTablePath(path)}")
            |       val lines = fromFile(file).getLines().toList.view
            |       val row   = lines.filter(_.startsWith(s"$$tgtId,$$srcId,")).last.split(",")
            |       val ncols = ${n + 2}
            |       if (ncols > 2 && row.size != ncols) throw new Exception(s"Expected $$ncols columns in CSV table $${file.getPath}")
            |       row.drop(2).map(_.toDouble)
            |     }
            |     catch {
            |       case _: Exception => Array.fill[Double]($n)($invalid)
            |     }
            |   else Array[Double]()
            |
            | val ${isValid.name} = !${p.name}.isEmpty && !${p.name}.contains($invalid)
            | if (${isValid.name}) println(s"${INFO}Have ${p.name.capitalize} for $regSet")
          """.stripMargin
        ) set (
          name    := s"${reg.id}-Read${p.name.capitalize}",
          imports += ("scala.io.Source.fromFile", "java.io.File", "Double.NaN"),
          inputs  += (regId, parId, tgtId, srcId),
          outputs += (regId, parId, tgtId, srcId, p, isValid)
        ),
        strainer = true
      )

    // Evaluate overlap between target segmentation and deformed source labels
    def evaluateOverlap =
      Capsule(
        ScalaTask(
          s"""
            | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
            |
            | val stats = IRTK.labelStats(${tgtSeg.name}.toFile, ${outSeg.name}.toFile, Some(Overlap.labels.toSet))
            |
            | val dscMetric = Overlap(stats, Overlap.DSC)
            | val dscValues = dscMetric.toArray
            | val dscGrpAvg = dscMetric.getMeanValues
            | val dscGrpStd = dscMetric.getSigmaValues
            |
            | val jsiMetric = Overlap(stats, Overlap.JSI)
            | val jsiValues = jsiMetric.toArray
            | val jsiGrpAvg = jsiMetric.getMeanValues
            | val jsiGrpStd = jsiMetric.getSigmaValues
            |
            | val dscValuesValid = $dscEnabled
            | val dscGrpAvgValid = $dscEnabled
            | val dscGrpStdValid = $dscEnabled
            | val jsiValuesValid = $jsiEnabled
            | val jsiGrpAvgValid = $jsiEnabled
            | val jsiGrpStdValid = $jsiEnabled
            |
            | println(s"${DONE}Overlap evaluation for $regSet")
          """.stripMargin
        ) set (
          name        := s"${reg.id}-EvaluateOverlap",
          imports     += "com.andreasschuh.repeat.core.{Config, IRTK, Overlap}",
          usedClasses += (Config.getClass, IRTK.getClass, Overlap.getClass),
          inputs      += (regId, parId, tgtId, srcId, tgtSeg, outSeg),
          outputs     += (regId, parId, tgtId, srcId, dscValues, dscGrpAvg, dscGrpStd, jsiValues, jsiGrpAvg, jsiGrpStd),
          outputs     += (dscValuesValid, dscGrpAvgValid, dscGrpStdValid, jsiValuesValid, jsiGrpAvgValid, jsiGrpStdValid)
        ),
        strainer = true
      )

    val evaluateOverlapEnd =
      Capsule(
        EmptyTask() set (
          name    := s"${reg.id}-EvaluateOverlapEnd",
          inputs  += (dscValues, dscGrpAvg, dscGrpStd, jsiValues, jsiGrpAvg, jsiGrpStd),
          inputs  += (dscValuesValid, dscGrpAvgValid, dscGrpStdValid, jsiValuesValid, jsiGrpAvgValid, jsiGrpStdValid),
          outputs += (dscValues, dscGrpAvg, dscGrpStd, jsiValues, jsiGrpAvg, jsiGrpStd),
          outputs += (dscValuesValid, dscGrpAvgValid, dscGrpStdValid, jsiValuesValid, jsiGrpAvgValid, jsiGrpStdValid)
        ),
        strainer = true
      )

    // Write individual registration result to CSV table
    def appendToTable(path: String, result: Prototype[Array[Double]], header: String) =
      EmptyTask() set (
        name    := s"${reg.id}-Write${result.name.capitalize}",
        inputs  += (regId, parId, tgtId, srcId, result),
        outputs += (regId, parId, tgtId, srcId, result)
      ) hook (
        AppendToCSVFileHook(path, tgtId, srcId, result) set (
          csvHeader := "Target,Source," + header,
          singleRow := true
        ),
        DisplayHook(s"${DONE}Append ${result.name.capitalize} for $regSet")
      )

    // Calculate mean of values over all registration results computed with a fixed set of parameters
    def calcMean(result: Prototype[Array[Double]], resultValid: Prototype[Boolean],
                 mean:   Prototype[Array[Double]], meanValid:   Prototype[Boolean]) =
      ScalaTask(
        s"""
          | val regId = input.regId.head
          | val parId = input.parId.head
          | val ${meanValid.name} = !${resultValid.name}.contains(false)
          | val ${mean.name} = if (!${meanValid.name}) Double.NaN else
          |   ${result.name}.transpose.map(_.sum / ${result.name}.size)
        """.stripMargin
      ) set (
        name    := s"${reg.id}-Calc${mean.name.capitalize}",
        inputs  += (regId.toArray, parId.toArray, result.toArray, resultValid.toArray),
        outputs += (regId, parId, mean, meanValid)
      )

    // Write mean values calculated over all registration results computed with a fixed set of parameters to CSV table
    def appendToMeanTable(path: String, mean: Prototype[Array[Double]], header: String) =
      EmptyTask() set (
        name    := s"${reg.id}-Write${mean.name.capitalize}",
        inputs  += (regId, parId, mean),
        outputs += (regId, parId, mean)
      ) hook (
        AppendToCSVFileHook(path, regId, parId, mean) set (
          csvHeader := "Registration,Parameters," + header,
          singleRow := true
        ),
        DisplayHook(s"${DONE}Append ${mean.name.capitalize} for $avgSet")
      )

    def display(prefix: String, message: String) =
      Capsule(EmptyTask(), strainer = true) hook DisplayHook(prefix + message)

    // -----------------------------------------------------------------------------------------------------------------
    // Backup previous result tables
    def backupTables =
      setRegId --
      // Delete summary tables with mean values
      deleteTable(avgTimeCsvPath,   timeEnabled && !Overlap.append) --
      deleteTable(dscRegAvgCsvPath, dscEnabled  && !Overlap.append) --
      deleteTable(jsiRegAvgCsvPath, jsiEnabled  && !Overlap.append) --
      // Results of individual registrations
      forEachPar -< setParId --
        backupTable(runTimeCsvPath,   timeEnabled) --
        backupTable(dscValuesCsvPath, dscEnabled) --
        backupTable(dscGrpAvgCsvPath, dscEnabled) --
        backupTable(dscGrpStdCsvPath, dscEnabled) --
        backupTable(jsiValuesCsvPath, jsiEnabled) --
        backupTable(jsiGrpAvgCsvPath, jsiEnabled) --
        backupTable(jsiGrpStdCsvPath, jsiEnabled) >-
      backupTablesEnd

    // -----------------------------------------------------------------------------------------------------------------
    // Prepare input transformation
    def convertDofToAff =
      backupTablesEnd -- Capsule(forEachImPair, strainer = true) -<
        convertDofToAffBegin --
          ConvertDofToAff(reg, regId, tgtId, srcId, iniDof, affDof, affDofPath) >-
        convertDofToAffEnd

    // -----------------------------------------------------------------------------------------------------------------
    // Run registration command
    def registerImages = {
      // Execute pairwise registration
      val regCond =
        Condition(
          s"""
            | val tgtIm  = input.tgtIm.toFile
            | val srcIm  = input.srcIm.toFile
            | val iniDof = new java.io.File(s"$iniDofPath")
            | val outDof = new java.io.File(s"$outDofPath")
            | val outIm  = new java.io.File(s"$outImPath")
            | val outSeg = new java.io.File(s"$outSegPath")
            | val outJac = new java.io.File(s"$outJacPath")
            |
            | def updateOutDof = $keepOutDof &&
            |   outDof.lastModified() < iniDof.lastModified &&
            |   outDof.lastModified() < tgtIm .lastModified &&
            |   outDof.lastModified() < srcIm .lastModified
            | def updateOutIm = $keepOutIm &&
            |   outIm.lastModified() < iniDof.lastModified &&
            |   outIm.lastModified() < tgtIm .lastModified &&
            |   outIm.lastModified() < srcIm .lastModified
            | def updateOutSeg = $keepOutSeg &&
            |   outSeg.lastModified() < iniDof.lastModified &&
            |   outSeg.lastModified() < tgtIm .lastModified &&
            |   outSeg.lastModified() < srcIm .lastModified
            | def updateOutJac = $keepOutJac &&
            |   outJac.lastModified() < iniDof.lastModified &&
            |   outJac.lastModified() < tgtIm .lastModified &&
            |   outJac.lastModified() < srcIm .lastModified
            |
            | updateOutDof || (!outDof.exists && (updateOutIm || updateOutSeg || updateOutJac)) || ($timeEnabled && !runTimeValid)
          """.stripMargin
        )
      def regImPair =
        RegisterImages(reg, regId, parId, parVal, tgtId, srcId, tgtIm, srcIm, affDof, outDof, outLog, runTime, runTimeValid)
      def runReg =
        convertDofToAffEnd -- forEachPar -< setParId --
          Capsule(forEachImPair, strainer = true) -<
            registerImagesBegin --
              readFromTable(runTimeCsvPath, runTime, runTimeValid, n = 4, invalid = .0, enabled = timeEnabled) --
              Switch(
                Case( regCond, display(QSUB, s"Registration for $regSet") -- regImPair),
                Case(!regCond, display(SKIP, s"Registration for $regSet"))
              ) --
            registerImagesEnd
      // Write runtime measurements
      def writeTime =
        registerImagesEnd -- Switch(
          Case(  "runTimeValid", appendToTable(runTimeCsvPath, runTime, header = "User,System,Total,Real")),
          Case(s"!runTimeValid && $timeEnabled", display(WARN, s"Missing ${runTime.name.capitalize} for $regSet"))
        ) >- demux("WriteTimeDemux", regId, parId) -- finalizeTable(runTimeCsvPath, timeEnabled)
      // Write mean of runtime measurements
      def writeMeanTime =
        registerImagesEnd >-
          calcMean(runTime, runTimeValid, avgTime, avgTimeValid) -- Switch(
            Case(  "avgTimeValid", appendToMeanTable(avgTimeCsvPath, avgTime, header = "User,System,Total,Real")),
            Case(s"!avgTimeValid && $timeEnabled", display(WARN, s"Invalid ${avgTime.name.capitalize} for $avgSet"))
          )
      // Assemble sub-workflow
      runReg + writeTime + writeMeanTime
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Deform source image for visual comparison and to measure residual distance
    def deformImage =
      if (!keepOutIm) nop else {
        registerImagesEnd --
          DeformImage(reg, regId, parId, tgtId, srcId, tgtIm, tgtImPath, srcImPath, outDof, outIm, outImPath)
        // TODO: Measure residual intensity error using different similarity metrics such as SSD, CC, and MI
        // TODO: Create PNG snapshots of exemplary slices for visual comparison
      }

    // -----------------------------------------------------------------------------------------------------------------
    // Deform source segmentation and evaluate overlap with target labels
    def deformLabels =
      if (!keepOutSeg && !dscEnabled && !jsiEnabled) nop else {
        // Deform source and evaluate overlap
        val evalCond =
          Condition(
            s"""
              | def dscValid = dscValuesValid && dscGrpAvgValid && dscGrpStdValid
              | def jsiValid = jsiValuesValid && jsiGrpAvgValid && jsiGrpStdValid
              | ($dscEnabled || $jsiEnabled) && (outSegModified || !dscValid || !jsiValid)
            """.stripMargin
          )
        def deformSource =
          registerImagesEnd --
            DeformLabels(reg, regId, parId, tgtId, srcId, tgtSeg, tgtSegPath, srcSegPath, outDof, outSeg, outSegPath, outSegModified)
        def calcOverlap =
          readFromTable(dscValuesCsvPath, dscValues, dscValuesValid, enabled = dscEnabled) --
          readFromTable(dscGrpAvgCsvPath, dscGrpAvg, dscGrpAvgValid, enabled = dscEnabled) --
          readFromTable(dscGrpStdCsvPath, dscGrpStd, dscGrpStdValid, enabled = dscEnabled) --
          readFromTable(jsiValuesCsvPath, jsiValues, jsiValuesValid, enabled = jsiEnabled) --
          readFromTable(jsiGrpAvgCsvPath, jsiGrpAvg, jsiGrpAvgValid, enabled = jsiEnabled) --
          readFromTable(jsiGrpStdCsvPath, jsiGrpStd, jsiGrpStdValid, enabled = jsiEnabled) --
          Switch(
            Case( evalCond, display(QSUB, s"Overlap evaluation for $regSet") -- (evaluateOverlap on Env.short by 10)),
            Case(!evalCond, display(SKIP, s"Overlap evaluation for $regSet"))
          ) --
          evaluateOverlapEnd
        // Write overlap measures
        def writeOverlap = (
            evaluateOverlapEnd -- (
              appendToTable(dscValuesCsvPath, dscValues, header = labels) when "dscValuesValid",
              appendToTable(dscGrpAvgCsvPath, dscGrpAvg, header = groups) when "dscGrpAvgValid",
              appendToTable(dscGrpStdCsvPath, dscGrpStd, header = groups) when "dscGrpStdValid",
              appendToTable(jsiValuesCsvPath, jsiValues, header = labels) when "jsiValuesValid",
              appendToTable(jsiGrpAvgCsvPath, jsiGrpAvg, header = groups) when "jsiGrpAvgValid",
              appendToTable(jsiGrpStdCsvPath, jsiGrpStd, header = groups) when "jsiGrpStdValid"
            )
          ) -- demux("WriteOverlapDemux1", regId, parId) >- demux("WriteOverlapDemux2", regId, parId) -- (
            finalizeTable(dscValuesCsvPath, dscEnabled),
            finalizeTable(dscGrpAvgCsvPath, dscEnabled),
            finalizeTable(dscGrpStdCsvPath, dscEnabled),
            finalizeTable(jsiValuesCsvPath, jsiEnabled),
            finalizeTable(jsiGrpAvgCsvPath, jsiEnabled),
            finalizeTable(jsiGrpStdCsvPath, jsiEnabled)
          )
        // Write mean of overlap measures
        def writeMeanOverlap =
          evaluateOverlapEnd >- (
            calcMean(dscGrpAvg, dscGrpAvgValid, dscRegAvg, dscRegAvgValid) -- Switch(
              Case(  "dscRegAvgValid", appendToMeanTable(dscRegAvgCsvPath, dscRegAvg, header = groups)),
              Case(s"!dscRegAvgValid && $dscEnabled", display(WARN, s"Invalid ${dscRegAvg.name.capitalize} for $avgSet"))
            ),
            calcMean(jsiGrpAvg, jsiGrpAvgValid, jsiRegAvg, jsiRegAvgValid) -- Switch(
              Case(  "jsiRegAvgValid", appendToMeanTable(jsiRegAvgCsvPath, jsiRegAvg, header = groups)),
              Case(s"!jsiRegAvgValid && $jsiEnabled", display(WARN, s"Invalid ${jsiRegAvg.name.capitalize} for $avgSet"))
            )
          )
        // TODO: Create PNG snapshots of segmentation overlay on top of target image for visual assessment
        // Assemble sub-workflow
        (deformSource -- calcOverlap) + writeOverlap + writeMeanOverlap
      }

    // -----------------------------------------------------------------------------------------------------------------
    // TODO: Sort summary result tables when all results are appended

    // -----------------------------------------------------------------------------------------------------------------
    // TODO: Deform grid generated by Init workflow
    // TODO: Create PNG snapshots of deformed grid for visual assessment of deformation

    // -----------------------------------------------------------------------------------------------------------------
    // Calculate map of Jacobian determinants for qualitative assessment of volume changes and invertibility
    def calcDetJac =
      if (!keepOutJac) nop else {
        registerImagesEnd -- ComputeJacobian(reg, regId, parId, tgtId, srcId, tgtImPath, outDof, outJac, outJacPath)
        // TODO: Compute statistics of Jacobian determinant and store these in CSV file
      }

    // -----------------------------------------------------------------------------------------------------------------
    // Assemble complete registration evaluation workflow
    backupTables + convertDofToAff + registerImages + deformImage + deformLabels + calcDetJac
  }
}