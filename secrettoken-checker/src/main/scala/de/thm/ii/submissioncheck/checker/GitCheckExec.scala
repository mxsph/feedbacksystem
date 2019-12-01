package de.thm.ii.submissioncheck.checker

import java.io.{ByteArrayOutputStream, File}
import java.net.{HttpURLConnection, URL, URLEncoder}
import java.nio.file.{Files, Path, Paths}
import de.thm.ii.submissioncheck.SecretTokenChecker.ULDIR
import de.thm.ii.submissioncheck.services.FileOperations
import de.thm.ii.submissioncheck.{JsonHelper, SecretTokenChecker}
import scala.io.Source
import scala.sys.process.{Process, ProcessLogger}

/**
  * Check submissions wich can handle php and bash tests
  * @param compile_production flagg which compiles the path corresponding if app runs in docker or not
  */
class GitCheckExec(override val compile_production: Boolean) extends BaseChecker(compile_production) {
  /** the unique identification of a checker, will extended to "gitchecker" */
  override val checkername = "git"
  /** define which configuration files the checker need - to be overwritten */
  override val configFiles: Map[String, Boolean] = Map("structurecheck" -> true, "config.json" -> false)

  private val timeout = 5000
  /** save the output of our execution */
  var output: String = ""
  private val startCode = -99
  /** save the success of our execution */
  var success: Boolean = false
  /** save the exit code of our execution */
  var exitCode: Int = startCode

  private val LABEL_TEST = "test"
  private val LABEL_RESULT = "result"
  private val LABEL_HEADER = "header"
  private val LABEL_GIT = "git"
  /** file of checker to configure it */
  var LABEL_CONFIGFILE = "config.json"
  /** file in gitignore syntax to check if seomthing is there or not */
  var LABEL_STRUCTUREFILE = "structurecheck"

  @throws(classOf[java.io.IOException])
  @throws(classOf[java.net.SocketTimeoutException])
  private def gitlabGet(url: String, token: String, connectTimeout: Int = timeout, readTimeout: Int = timeout, requestMethod: String = "GET") = {
    val connection = (new URL(url)).openConnection.asInstanceOf[HttpURLConnection]
    connection.setConnectTimeout(connectTimeout)
    connection.setReadTimeout(readTimeout)
    connection.setRequestMethod(requestMethod)
    connection.setRequestProperty("PRIVATE-TOKEN", token)
    val inputStream = connection.getInputStream
    val content = io.Source.fromInputStream(inputStream).mkString
    if (inputStream != null) inputStream.close
    content
  }

  private def runStructureTest(configFile: String, targetPath: Path) = {
    var result: List[Map[String, Any]] = List()
    val submittedFiles = FileOperations.tree(targetPath.toFile)
    val bufferedSource = Source.fromFile(configFile)
    for (line <- bufferedSource.getLines) {
      // Handle lines, with gitignore syntax
      // first trim the line
      val trimmed = line.trim
      if (trimmed.matches("""!\*.*""")){
        val searchString = trimmed.replace("!", ".")
        // file with extension should be not there
        result = Map(LABEL_TEST -> trimmed, LABEL_RESULT ->
          (submittedFiles.toList.map(f => f.toPath).filter(f => f.toString.matches(searchString)).size == 0)) :: result
      } else if (trimmed.matches("!.*")) {
        // file should not be there
        val filePath = targetPath.resolve(trimmed.replace("!", ""))
        result = Map(LABEL_TEST -> trimmed, LABEL_RESULT -> !Files.exists(filePath)) :: result
      } else if (trimmed.matches("#.*")) {
        //skipp this line
      } else if (trimmed.matches("/.*")) {
        // is absolute path
        val filePath = targetPath.resolve(trimmed.replaceFirst("^/", ""))
        result = Map(LABEL_TEST -> trimmed, LABEL_RESULT -> Files.exists(filePath)) :: result
      } else {
        // file should be there
        val filePath = targetPath.resolve(trimmed)
        result = Map(LABEL_TEST -> trimmed, LABEL_RESULT -> Files.exists(filePath)) :: result
      }
    }
    bufferedSource.close
    result
  }

  /**
    * get list of every person how comitted something
    */
  private def gitGetContributorList(target_dir: String) = {
    val gitLogOutput = new ByteArrayOutputStream
    val seq = Seq("-C", target_dir, "shortlog", "-sne", "--all")

    Process(LABEL_GIT, seq).#>(gitLogOutput).run().exitValue()

   gitLogOutput.toString.split("\n").map(entry => {
      entry.replaceFirst("^\\s*[0-9]+\\s+", "").replaceFirst("<.+>", "").trim
    }).toList
  }

  private def runMaintainerTest(docentFile: File, target_dir: String, git_url: String) = {
    var result: List[Map[String, Any]] = List()
    var docentResult: List[Map[String, Any]] = List()
    var docentSettingMap: Map[String, Any] = Map()
    try {
      val bufferedSource = Source.fromFile(docentFile)
      docentSettingMap = JsonHelper.jsonStrToMap(bufferedSource.mkString)
    } catch {
      case e: Exception => {
        output = "Feedbacksystem has invalid testfiles (GIT Checker). Please contact your docent."
        exitCode = 2
      }
    }
    if (exitCode == startCode || exitCode == 0) {
      try {
        val base_url = docentSettingMap("base_url")
        val API_TOKEN = docentSettingMap("API_TOKEN").toString
        val projectID = URLEncoder.encode(git_url.replaceFirst("^git.*:", "").replaceFirst(".git", ""), "UTF-8")

        val projectInfo: Map[String, Any] = JsonHelper.jsonStrToMap(gitlabGet(base_url + projectID, API_TOKEN))
        val projectMaintainer: List[Map[String, Any]] = JsonHelper.jsonStrToList(gitlabGet(base_url + projectID + "/members",
          API_TOKEN)).asInstanceOf[List[Map[String, Any]]]
        val pNames = gitGetContributorList(target_dir)

        projectMaintainer.foreach(maintainerMap => {
          val name = maintainerMap("name").asInstanceOf[String]
          if (maintainerMap("access_level").asInstanceOf[BigInt] == 40) result = Map(LABEL_TEST -> name, LABEL_RESULT -> pNames.contains(name)) :: result
        })
        val projectDeveloper = projectMaintainer.map(maintainer => {
          val name = maintainer("name").asInstanceOf[String]
          if (maintainer("access_level").asInstanceOf[BigInt] == 30) name else None
        })

        // check if docent is developer in this repo
        docentSettingMap("docents").asInstanceOf[List[String]].foreach(docent => {
          docentResult = Map(LABEL_TEST -> docent, LABEL_RESULT -> projectDeveloper.contains(docent)) :: docentResult
        })
      } catch {
        case e: java.net.SocketTimeoutException => {
          output = "Connection to GIT failed"
          exitCode = 1
        }
        case e: Exception => {
          output = "GIT Url is invalid"
          exitCode = 2
        }
      }
    }
    (result, docentResult)
  }

  /**
    * perform a check of request, will be executed after processing the kafka message
    * @param taskid submissions task id
    * @param submissionid submitted submission id
    * @param submittedFilePath path of submitted file (if zip or something, it is also a "file"
    * @param isInfo execute info procedure for given task
    * @param use_extern include an existing file, from previous checks
    * @param jsonMap complete submission payload
    * @return check succeeded, output string, exitcode
    */
  override def exec(taskid: String, submissionid: String, submittedFilePath: String, isInfo: Boolean, use_extern: Boolean, jsonMap: Map[String, Any]):
  (Boolean, String, Int) = {
    val git_url = scala.io.Source.fromFile(submittedFilePath).mkString
    new File(submittedFilePath).delete()
    val targetPath = Paths.get(ULDIR).resolve(taskid.toString).resolve(submissionid)
    val target_dir = targetPath.toString

    val basePath = Paths.get(ULDIR).resolve(taskid.toString).resolve(checkernameExtened)
    val targetDirPath = Paths.get(target_dir)
    Files.createDirectories(targetDirPath)

    if (exitCode == startCode) { // no problems yet
      var seq: Seq[String] = null
      val stdoutStream = new StringBuilder; val stderrStream = new StringBuilder
      val logger = ProcessLogger((o: String) => stdoutStream.append(o), (e: String) => stderrStream.append(e))
      seq = Seq("clone", git_url, target_dir)
      exitCode = Process(LABEL_GIT, seq).!(logger)
      output = stdoutStream.toString() + "\n" + stderrStream.toString()

      var checkresultList: List[Map[String, Any]] = List()
      var maintainerMap: List[Map[String, Any]] = List()
      var docentMap: List[Map[String, Any]] = List()
      if (exitCode == 0) {
        val configFile = new File(basePath.resolve(LABEL_STRUCTUREFILE).toString)
        val configFilePathRel = configFile.getPath
        val configFileAbsPath = configFile.getAbsolutePath
        var structureMap = runStructureTest(configFileAbsPath, targetPath)
        checkresultList = Map(LABEL_HEADER -> "Structure Check", LABEL_RESULT -> structureMap) :: checkresultList

        if (Files.exists(basePath.resolve(LABEL_CONFIGFILE))) {
          // we request to git(lab/hub) and do some activity checks
          val mapTuple = runMaintainerTest(new File(basePath.resolve(LABEL_CONFIGFILE).toString),
            targetDirPath.toAbsolutePath.toString, git_url)
          maintainerMap = mapTuple._1; docentMap = mapTuple._2;
          checkresultList = Map(LABEL_HEADER -> "Maintainer Check", LABEL_RESULT -> maintainerMap) :: checkresultList
          checkresultList = Map(LABEL_HEADER -> "Docent Check", LABEL_RESULT -> docentMap) :: checkresultList
        }

        if (exitCode == startCode || exitCode == 0) {
          var sum = 0
          structureMap.foreach(a => if (a(LABEL_RESULT).asInstanceOf[Boolean]) sum += 1)
          maintainerMap.foreach(a => if (a(LABEL_RESULT).asInstanceOf[Boolean]) sum += 1)
          docentMap.foreach(a => if (a(LABEL_RESULT).asInstanceOf[Boolean]) sum += 1)
          // If checks are passed we can send a string or a JSON String
          output = JsonHelper.listToJsonStr(checkresultList)
          if (sum == (structureMap.size + maintainerMap.size + docentMap.size)) exitCode = 0 else exitCode = 1
        }
      }
    }
    (exitCode == 0, output, exitCode)
  }

  /**
    * get systems public key
    *
    * @return public key
    */
  def getPublicKey(): String = {
    var seq: Seq[String] = null
    val stdoutStream = new ByteArrayOutputStream
    seq = Seq(System.getenv("HOME") + "/.ssh/id_rsa.pub")

    val exitcode = Process("cat", seq).#>(stdoutStream).run().exitValue()
    stdoutStream.toString
  }
}
