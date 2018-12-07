package de.thm.ii.submissioncheck.controller

import java.{io, util}

import com.fasterxml.jackson.databind.JsonNode
import de.thm.ii.submissioncheck.misc.{BadRequestException, UnauthorizedException}
import de.thm.ii.submissioncheck.model.User
import de.thm.ii.submissioncheck.services._
import javax.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.{RequestMapping, _}

/**
  * Controller to manage rest api calls for a course resource.
  */
@RestController
@RequestMapping(path = Array("/api/v1/testsystems"))
class TestsystemController {
  @Autowired
  private val userService: UserService = null

  @Autowired
  private val testsystemService: TestsystemService = null

  private final val PATH_LABEL_ID = "id"

  /**
    * getAllTestystems is a route to get all available task systems
    * @param request Request Header containing Headers
    * @return JSON
    */
  @RequestMapping(value = Array(""), method = Array(RequestMethod.GET))
  def getAllTestystems(request: HttpServletRequest): List[Map[String, String]] = {
    val user = userService.verfiyUserByHeaderToken(request)
    if (user.isEmpty || user.get.roleid > 2) {
        throw new UnauthorizedException
    }
    testsystemService.getTestsystems()
  }

  /**
    * createTestsystem is a route to register a tasksystem
    * @param request contain request information
    * @param jsonNode contains JSON request
    * @return JSON
    */
  @RequestMapping(value = Array(""), method = Array(RequestMethod.POST), consumes = Array(MediaType.APPLICATION_JSON_VALUE))
  def createTestsystem(request: HttpServletRequest, @RequestBody jsonNode: JsonNode): Map[String, Any] = {
    val user = userService.verfiyUserByHeaderToken(request)
    if (user.isEmpty || user.get.roleid > 2) {
      throw new UnauthorizedException
    }
    try {
      val id = jsonNode.get(PATH_LABEL_ID).asText()
      val name = jsonNode.get(TestsystemLabels.name).asText()
      val description = jsonNode.get(TestsystemLabels.description).asText()
      val supported_formats = jsonNode.get(TestsystemLabels.supported_formats).asText()
      val machine_port: Int = if (jsonNode.get(TestsystemLabels.machine_port) != null)  jsonNode.get(TestsystemLabels.machine_port).asInt() else 0
      val machine_ip: String = if (jsonNode.get(TestsystemLabels.machine_ip) != null)  jsonNode.get(TestsystemLabels.machine_ip).asText() else ""
      testsystemService.insertTestsystem(id, name, description, supported_formats, machine_port, machine_ip)
    } catch {
      case _: NullPointerException => throw new BadRequestException("Please provide: id, name, description, supported_formats")
    }
  }

  /**
    * updateTestsystem is a route to register a tasksystem
    * @param testsystemid unique course identification
    * @param request contain request information
    * @param jsonNode contains JSON request
    * @return JSON
    */
  @RequestMapping(value = Array("{testsystemid}"), method = Array(RequestMethod.PUT), consumes = Array(MediaType.APPLICATION_JSON_VALUE))
  def updateTestsystem(@PathVariable testsystemid: String, request: HttpServletRequest, @RequestBody jsonNode: JsonNode): Map[String, Any] = {
    val user = userService.verfiyUserByHeaderToken(request)
    if (user.isEmpty || user.get.roleid > 2) {
      throw new UnauthorizedException
    }
    try {
      val name: String = if (jsonNode.get("name") != null)  jsonNode.get("name").asText() else null
      val description: String = if (jsonNode.get("description") != null)  jsonNode.get("description").asText() else null
      val supported_formats: String = if (jsonNode.get("supported_formats") != null)  jsonNode.get("supported_formats").asText() else null
      val machine_port: Int = if (jsonNode.get("machine_port") != null)  jsonNode.get("machine_port").asInt() else 0
      val machine_ip: String = if (jsonNode.get("machine_ip") != null)  jsonNode.get("machine_ip").asText() else null
      // this.courseService.createCourseByUser(user.get, name, description, standard_task_typ)
      Map("success" -> testsystemService.updateTestsystem(testsystemid, name, description, supported_formats, machine_port, machine_ip))
    } catch {
      case _: NullPointerException => throw new BadRequestException("Please provide: name, description, supported_formats")
    }
  }

  /**
    * getTestsystem provides Testsystem details
    * @author Benjamin Manns
    * @param testsystemid unique course identification
    * @param request Request Header containing Headers
    * @return JSON
    */
  @RequestMapping(value = Array("{testsystemid}"), method = Array(RequestMethod.GET), consumes = Array())
  @ResponseBody
  def getTestsystem(@PathVariable testsystemid: String, request: HttpServletRequest): Map[String, String] = {
    val user = userService.verfiyUserByHeaderToken(request)
    if(user.isEmpty || user.get.roleid > 2) {
      throw new UnauthorizedException
    }
    testsystemService.getTestsystem(testsystemid).getOrElse(Map.empty)
  }
  /**
    * deleteTestsystem deletes a Testsystem
    * @param systemid unique course identification
    * @param request Request Header containing Headers
    * @return JSON
    */
  @RequestMapping(value = Array("{id}"), method = Array(RequestMethod.DELETE), consumes = Array())
  @ResponseBody
  def deleteTestsystem(@PathVariable(PATH_LABEL_ID) systemid: String, request: HttpServletRequest): Map[String, Boolean] = {
    val user = userService.verfiyUserByHeaderToken(request)
    if(user.isEmpty || user.get.roleid > 1) { // has to be admin
      throw new UnauthorizedException
    }

    Map("success" -> testsystemService.deleteTestsystem(systemid))
  }
}