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

import com.andreasschuh.repeat.core._
import com.andreasschuh.repeat.puzzle._

import org.openmole.core.dsl._
import org.openmole.core.workflow.data.Prototype
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

    def backupTablePath(path: String) = {
      val p = Paths.get(path)
      p.getParent.resolve(FileUtil.hidden(p.getFileName.toString)).toString
    }

    // NOP puzzle
    val nop = Capsule(EmptyTask() set (name := s"${reg.id}-NOP")).toPuzzle

    // Exploration tasks
    val setRegId =
      EmptyTask() set (
        name    := s"${reg.id}-SetRegId",
        outputs += regId,
        regId   := reg.id
      )

    val forEachPar =
      ExplorationTask(paramSampling) set (
        name    := s"${reg.id}-ForEachPar",
        inputs  += regId,
        outputs += regId
      )

    val setParId =
      ScalaTask(
        """
          | val parId  = input.parVal("ID")
          | val parVal = input.parVal - "ID"
        """.stripMargin
      ) set (
        name    := s"${reg.id}-SetParId",
        inputs  += (regId, parVal),
        outputs += (regId, parId, parVal)
      )

    val forEachImPair = // must *not* be a capsule as it is used more than once!
      ExplorationTask(imageSampling) set (
        name    := s"${reg.id}-ForEachImPair",
        inputs  += regId,
        outputs += regId
      )

    // Initial conversion of affine input transformation
    val convertDofToAffBegin =
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

    // Generators for auxiliary tasks used to avoid unnecessary re-computation of result measures
    def backupTable(path: String, enabled: Boolean = true) =
      if (enabled)
        Capsule(
          ScalaTask(
            s"""
              | val from = Paths.get(s"$path")
              | val to   = Paths.get(s"${backupTablePath(path)}")
              | if (Files.exists(from)) {
              |   println("${Prefix.INFO}Backup " + from)
              |   Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
              | }
            """.stripMargin
          ) set (
            name    := s"${reg.id}-MovePrevResult",
            imports += "java.nio.file.{Paths, Files, StandardCopyOption}",
            inputs  += (regId, parId),
            outputs += (regId, parId)
          ),
          strainer = true
        )
      else
        Capsule(
          EmptyTask() set (
            name    := s"${reg.id}-KeepPrevResult",
            inputs  += (regId, parId),
            outputs += (regId, parId)
          ),
          strainer = true
        )

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
            |
            | println("${Prefix.INFO}" + (if (${isValid.name}) "Have" else "Miss") + s" previous ${p.name.capitalize} for $regSet")
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
    val evaluateOverlap =
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
        DisplayHook(s"${Prefix.INFO}Appended ${result.name.capitalize} for $regSet")
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
          |   ${result.name}.transpose.map(_.sum / ${result.name}.head.size)
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
        DisplayHook(s"${Prefix.INFO}Appended ${mean.name.capitalize} for $avgSet")
      )

    /* TODO: Test this to close OpenMOLE issue #62. Either use it instead of separate calcMean and appendToMeanTable or remove.
    def calcMeanAndAppendToTable(path: String, result: Prototype[Array[Double]], mean: Prototype[Array[Double]], header: String) =
      ScalaTask(
        s"""
          | val regId = input.regId.head
          | val parId = input.parId.head
          | val ${mean.name} = ${result.name}.transpose.map(_.sum / ${result.name}.head.size)
        """.stripMargin
      ) set (
        name    := s"${reg.id}-Calc${mean.name.capitalize}",
        inputs  += (regId.toArray, parId.toArray, result.toArray),
        outputs += (regId, parId, mean)
      ) hook (
        AppendToCSVFileHook(path, regId, parId, mean) set (
          csvHeader := "Registration,Parameters," + header,
          singleRow := true
        ),
        DisplayHook(s"${Prefix.INFO}Appended ${mean.name.capitalize} for $avgSet")
      )
    */

    def Display(prefix: String, message: String) =
      Capsule(EmptyTask(), strainer = true) hook DisplayHook(prefix + message)

    // -----------------------------------------------------------------------------------------------------------------
    // Workflow
    def convertDofToAff =
      setRegId -- Capsule(forEachImPair, strainer = true) -<
        convertDofToAffBegin --
          ConvertDofToAff(reg, regId, tgtId, srcId, iniDof, affDof, affDofPath) >-
        convertDofToAffEnd

    def backupTables =
      backupTable(runTimeCsvPath,   enabled = timeEnabled) --
      backupTable(avgTimeCsvPath,   enabled = timeEnabled) --
      backupTable(dscValuesCsvPath, enabled = dscEnabled ) --
      backupTable(dscGrpAvgCsvPath, enabled = dscEnabled ) --
      backupTable(dscGrpStdCsvPath, enabled = dscEnabled ) --
      backupTable(dscRegAvgCsvPath, enabled = dscEnabled ) --
      backupTable(jsiValuesCsvPath, enabled = jsiEnabled ) --
      backupTable(jsiGrpAvgCsvPath, enabled = jsiEnabled ) --
      backupTable(jsiGrpStdCsvPath, enabled = jsiEnabled ) --
      backupTable(jsiRegAvgCsvPath, enabled = jsiEnabled )

    def registerImages = {
      def run =
        convertDofToAffEnd -- forEachPar -<
          setParId -- backupTables -- Capsule(forEachImPair, strainer = true) -<
            registerImagesBegin --
              readFromTable(runTimeCsvPath, runTime, runTimeValid, n = 4, invalid = .0, enabled = timeEnabled) --
              Skip(
                RegisterImages(reg, regId, parId, parVal, tgtId, srcId, tgtIm, srcIm, affDof, outDof, outLog, runTime, runTimeValid),
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
                  | def skip = !(updateOutDof || updateOutIm || updateOutSeg || updateOutJac) && (!$timeEnabled || runTimeValid)
                  | if (skip) println(s"${Prefix.INFO}Skip registration for $regSet")
                  | skip
                """.stripMargin
              ) --
            registerImagesEnd
      def writeTime =
        registerImagesEnd -- Switch(
          Case(  "runTimeValid", appendToTable(runTimeCsvPath, runTime, header = "User,System,Total,Real")),
          Case(s"!runTimeValid && $timeEnabled", Display(Prefix.WARN, s"Missing ${runTime.name.capitalize} for $regSet"))
        )
      def writeMeanTime =
        registerImagesEnd >-
          calcMean(runTime, runTimeValid, avgTime, avgTimeValid) -- Switch(
            Case(  "avgTimeValid", appendToMeanTable(avgTimeCsvPath, avgTime, header = "User,System,Total,Real")),
            Case(s"!avgTimeValid && $timeEnabled", Display(Prefix.WARN, s"Invalid ${avgTime.name.capitalize} for $avgSet"))
          )
      /*
      def writeMeanTime =
        registerImagesEnd >- (
          calcMeanAndAppendToTable(avgTimeCsvPath, runTime, avgTime, "User,System,Total,Real") when "!runTimeValid.contains(false)"
        )
      */
      run + writeTime + writeMeanTime
    }

    def deformImage =
      if (!keepOutIm) nop else
        registerImagesEnd -- DeformImage(reg, regId, parId, tgtId, srcId, tgtImPath, srcImPath, outDof, outIm, outImPath)

    def deformLabels =
      if (!keepOutSeg) nop else {
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
          Skip(
            evaluateOverlap hook DisplayHook(s"${Prefix.INFO}Evaluated overlap for $regSet"),
            s"""
              | def dscValid = dscValuesValid && dscGrpAvgValid && dscGrpStdValid
              | def jsiValid = jsiValuesValid && jsiGrpAvgValid && jsiGrpStdValid
              | def skip = (!$dscEnabled && !$jsiEnabled) || (!outSegModified && dscValid && jsiValid)
              | if (skip) println(s"${Prefix.INFO}Skip overlap evaluation for $regSet")
              | skip
            """.stripMargin
          ) -- evaluateOverlapEnd
        def writeOverlap =
          evaluateOverlapEnd -- (
            appendToTable(dscValuesCsvPath, dscValues, header = labels) when "dscValuesValid",
            appendToTable(dscGrpAvgCsvPath, dscGrpAvg, header = groups) when "dscGrpAvgValid",
            appendToTable(dscGrpStdCsvPath, dscGrpStd, header = groups) when "dscGrpStdValid",
            appendToTable(jsiValuesCsvPath, jsiValues, header = labels) when "jsiValuesValid",
            appendToTable(jsiGrpAvgCsvPath, jsiGrpAvg, header = groups) when "jsiGrpAvgValid",
            appendToTable(jsiGrpStdCsvPath, jsiGrpStd, header = groups) when "jsiGrpStdValid"
          )
        def writeMeanOverlap =
          evaluateOverlapEnd >- (
            calcMean(dscGrpAvg, dscGrpAvgValid, dscRegAvg, dscRegAvgValid) -- Switch(
              Case(  "dscRegAvgValid", appendToMeanTable(dscRegAvgCsvPath, dscRegAvg, header = groups)),
              Case(s"!dscRegAvgValid && $dscEnabled", Display(Prefix.WARN, s"Invalid ${dscRegAvg.name.capitalize} for $avgSet"))
            ),
            calcMean(jsiGrpAvg, jsiGrpAvgValid, jsiRegAvg, jsiRegAvgValid) -- Switch(
              Case(  "jsiRegAvgValid", appendToMeanTable(jsiRegAvgCsvPath, jsiRegAvg, header = groups)),
              Case(s"!jsiRegAvgValid && $jsiEnabled", Display(Prefix.WARN, s"Invalid ${jsiRegAvg.name.capitalize} for $avgSet"))
            )
          )
        (deformSource -- calcOverlap) + writeOverlap + writeMeanOverlap
      }

    def calcDetJac =
      if (!keepOutJac) nop else {
        registerImagesEnd -- ComputeJacobian(reg, regId, parId, tgtId, srcId, tgtImPath, outDof, outJac, outJacPath)
        // TODO: Compute statistics of Jacobian determinant and store these in CSV file
      }

    convertDofToAff + registerImages + deformImage + deformLabels + calcDetJac
  }
}
