package migrate.internal

import scala.util.Try

import compiler.interfaces.CompilationUnit
import compiler.interfaces.Scala3Compiler
import migrate.AbsolutePath
import migrate.utils.ScalaExtensions._
import scalafix.interfaces._

sealed trait FileMigrationState {
  val evaluation: ScalafixFileEvaluation

  def source: AbsolutePath = AbsolutePath.from(evaluation.getEvaluatedFile).get

  def patches: Seq[ScalafixPatch] = evaluation.getPatches().toSeq

  def previewAllPatches(): Try[CompilationUnit] =
    evaluation
      .previewPatches()
      .map { content =>
        new CompilationUnit(source.value, content)
      }
      .asScala
      .toTry(new ScalafixException(s"Cannot apply patch on file $source"))

  def previewPatches(patches: Seq[ScalafixPatch]): Try[CompilationUnit] =
    evaluation
      .previewPatches(patches.toArray)
      .map { content =>
        new CompilationUnit(source.value, content)
      }
      .asScala
      .toTry(new ScalafixException(s"Cannot apply patch on file $source"))

}

object FileMigrationState {
  case class Initial(evaluation: ScalafixFileEvaluation) extends FileMigrationState {
    def migrate(compiler: Scala3Compiler): FileMigrationState.FinalState =
      new FileMigration(this, compiler).migrate()

    def success(necessaryPatches: Seq[ScalafixPatch]): Succeeded = Succeeded(evaluation, necessaryPatches)

    def failed(cause: Throwable): Failed = Failed(evaluation, cause)
  }
  sealed trait FinalState {
    def isSuccess: Boolean = this.isInstanceOf[Succeeded]
  }
  case class Succeeded(evaluation: ScalafixFileEvaluation, necessaryPatches: Seq[ScalafixPatch])
      extends FileMigrationState
      with FinalState {
    def newFileContent: Try[String] = previewPatches(necessaryPatches).map(_.content)
  }

  case class Failed(evaluation: ScalafixFileEvaluation, cause: Throwable) extends FileMigrationState with FinalState
}
