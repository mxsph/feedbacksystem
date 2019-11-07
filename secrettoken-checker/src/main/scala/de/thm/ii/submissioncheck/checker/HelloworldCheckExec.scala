package de.thm.ii.submissioncheck.checker
import de.thm.ii.submissioncheck.SecretTokenChecker.{logger}
/**
  * Hello World Checker Class is an example
  * @param compile_production flagg which compiles the path corresponding if app runs in docker or not
  */
class HelloworldCheckExec(override val compile_production: Boolean) extends BaseChecker(compile_production) {
  /** the unique identification of a checker, will extended to "helloworldchecker" */
  override val checkername = "helloworld"
  /** define which configuration files the checker need - to be overwritten */
  override val configFiles: List[String] = List("configfile")

  /**
    * perform a check of request, will be executed after processing the kafka message
    * @param taskid submissions task id
    * @param submissionid submitted submission id
    * @param submittedFilePath path of submitted file (if zip or something, it is also a "file"
    * @param isInfo execute info procedure for given task
    * @param use_extern include an existing file, from previous checks
    * @return check succeeded, output string, exitcode
    */
  override def exec(taskid: String, submissionid: String, submittedFilePath: String, isInfo: Boolean, use_extern: Boolean): (Boolean, String, Int) = {
    var (baseFilePath, configfiles) = loadCheckerConfig(taskid)
    val docentsContent = scala.io.Source.fromFile(configfiles(0).toString).mkString
    val usersContent = scala.io.Source.fromFile(submittedFilePath).mkString
    logger.warning(usersContent)
    val success = (docentsContent.trim == usersContent.trim)
    val output = s"The ${checkername} checker results: ${success}"
    val exitcode = 0
    (success, output, exitcode)
  }
}

