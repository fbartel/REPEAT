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

package com.andreasschuh.repeat.puzzle

//import java.nio.file.Path
//import scala.language.reflectiveCalls
//
//import org.openmole.core.dsl._
//import org.openmole.core.workflow.data.Prototype
//import org.openmole.plugin.hook.display.DisplayHook
//import org.openmole.plugin.task.scala._
//import org.openmole.plugin.tool.pattern.Skip
//
//import com.andreasschuh.repeat.core.{Environment => Env, _}
//
//
///**
// * Run (deformable) pairwise registration
// */
//object RegisterImages {
//
//  /**
//   * Performs (deformable) registration between target and source
//   *
//   * @param reg[in]             Registration info
//   * @param parId[in,out]       ID of parameter set
//   * @param parVal[in,out]      Registration parameters
//   * @param tgtId[in,out]       ID of target image
//   * @param srcId[in,out]       ID of source image
//   * @param affDofPath[in]      Initial affine guess of transformation from target to source
//   * @param outDofPath[in,out]  Output transformation from target to source
//   * @param runTime[in,out]     Runtime of registration command in seconds
//   *
//   * @return Puzzle piece to compute transformation from target to source
//   */
//  def apply(reg: Registration, regId: Prototype[String], parId: Prototype[String], parVal: Prototype[Map[String, String]],
//            tgtId: Prototype[Int], srcId: Prototype[Int], tgtImPath: Prototype[Path], srcImPath: Prototype[Path],
//            affDofPath: Prototype[Path], outDofPath: Prototype[Path], outLogPath: Prototype[Path],
//            runTime: Prototype[Array[Double]]) = {
//
//    val template = Val[Cmd]
//    val phi2dof  = Val[Cmd]
//
//    val phi2dofCmd = reg.phi2dofCmd getOrElse Seq[String]()
//
//    ScalaTask(
//      s"""
//        | Config.parse(\"\"\"${Config()}\"\"\", "${Config().base}")
//        |
//        | val outDofDir = ${outDofPath.name}.getParent
//        | if (outDofDir != null) Files.createDirectories(outDofDir)
//        |
//        | val log = new TaskLogger(${outLogPath.name}.toFile)
//        |
//        | val phiDofPath =
//        |   if (phi2dof.size > 0) Paths.get(workDir.getAbsolutePath, "phi${reg.phiSuf}")
//        |   else ${outDofPath.name}
//        |
//        | val regCfg = new File(workDir, "reg.cfg")
//        | val config = interpolate(\"\"\"${reg.config getOrElse ""}\"\"\".stripMargin.trim, ${parVal.name})
//        | if (config.length > 0) {
//        |   val fw = new FileWriter(regCfg)
//        |   fw.write(config + "\\n")
//        |   fw.close()
//        |   val label = "Configuration"
//        |   log.out(("-" * (40 - label.length / 2)) + label + ("-" * (40 - (label.length + 1) / 2)))
//        |   log.out(config + "\\n" + ("-" * 80) + "\\n")
//        | }
//        |
//        | val args = ${parVal.name} ++ Map(
//        |   "regId"  -> ${regId.name},
//        |   "parId"  -> ${parId.name},
//        |   "target" -> ${tgtImPath.name}.toString,
//        |   "source" -> ${srcImPath.name}.toString,
//        |   "aff"    -> ${affDofPath.name}.toString,
//        |   "phi"    -> phiDofPath.toString,
//        |   "config" -> regCfg.getPath
//        | )
//        | val cmd = Seq("/usr/bin/time", "-p") ++ Registration.command(template, args)
//        | val str = cmd.mkString("REPEAT> \\"", "\\" \\"", "\\"\\n")
//        | log.out(str)
//        | val ret = cmd ! log
//        | if (ret != 0) throw new Exception("Registration returned non-zero exit code: " + str)
//        | val ${runTime.name} = if (log.time.sum > .0) log.time else Array[Double]()
//        |
//        | if (phi2dof.size > 0) {
//        |   val args = Map(
//        |     "regId"  -> ${regId.name},
//        |     "parId"  -> ${parId.name},
//        |     "dof"    -> ${outDofPath.name}.toString,
//        |     "out"    -> ${outDofPath.name}.toString,
//        |     "dofout" -> ${outDofPath.name}.toString,
//        |     "in"     -> phiDofPath.toString,
//        |     "phi"    -> phiDofPath.toString
//        |   )
//        |   val cmd = Registration.command(phi2dof, args)
//        |   val str = cmd.mkString("\\nREPEAT> \\"", "\\" \\"", "\\"\\n")
//        |   log.out(str)
//        |   val ret = cmd ! log
//        |   if (ret != 0) throw new Exception("Failed to convert output transformation: " + str)
//        | }
//        |
//        | log.close()
//      """.stripMargin
//    ) set (
//      name        := s"${reg.id}-RegisterImages",
//      imports     += ("com.andreasschuh.repeat.core._", "scala.sys.process._", "java.io.{File, FileWriter}", "java.nio.file.{Paths, Files}"),
//      usedClasses += (Config.getClass, Registration.getClass, classOf[TaskLogger]),
//      inputs      += (regId, parId, parVal, tgtId, srcId, tgtImPath, srcImPath, affDofPath, outDofPath, outLogPath, template, phi2dof),
//      outputs     += (regId, parId, tgtId, srcId, outDofPath, outLogPath, runTime),
//      template    := reg.runCmd,
//      phi2dof     := phi2dofCmd
//    )
//  }
//}
