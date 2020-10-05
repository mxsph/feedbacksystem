package de.thm.ii.fbs.controller

import com.fasterxml.jackson.databind.JsonNode
import de.thm.ii.fbs.controller.exception.ForbiddenException
import de.thm.ii.fbs.model.{Course, CourseRole, GlobalRole, Participant}
import de.thm.ii.fbs.services.persistance.CourseRegistrationService
import de.thm.ii.fbs.services.security.AuthService
import de.thm.ii.fbs.util.JsonWrapper.jsonNodeToWrapper
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation._

/**
  * Handles course registration and course participants
  */
@RestController
@CrossOrigin
@RequestMapping(path = Array("/api/v1/"), produces = Array(MediaType.APPLICATION_JSON_VALUE))
class CourseRegistrationController {
  @Autowired
  private val authService: AuthService = null
  @Autowired
  private val courseRegistrationService: CourseRegistrationService = null

  /**
    * Get registered courses
    * @param uid User id
    * @param req http request
    * @param res http response
    * @return List of courses
    */
  @GetMapping(value = Array("/users/{uid}/courses"))
  @ResponseBody
  def getRegisteredCourses(@PathVariable("uid") uid: Integer, req: HttpServletRequest, res: HttpServletResponse): List[Course] = {
    val user = authService.authorize(req, res)
    val globalRole = user.globalRole

    if (globalRole == GlobalRole.ADMIN || globalRole == GlobalRole.MODERATOR || user.id == uid) {
      courseRegistrationService.getRegisteredCourses(uid, ignoreHidden = false)
    } else {
      throw new ForbiddenException()
    }
  }

  /**
    * Get participants of a course
    * @param cid Course id
    * @param req http request
    * @param res http response
    * @return List of courses
    */
  @GetMapping(value = Array("/courses/{cid}/participants"))
  @ResponseBody
  def getParticipants(@PathVariable("cid") cid: Integer, req: HttpServletRequest, res: HttpServletResponse): List[Participant] = {
    val user = authService.authorize(req, res)
    val globalRole = user.globalRole
    val participants = courseRegistrationService.getParticipants(cid)

    val privilegedByCourse = participants.find(_.user.id == user.id).map(_.role).exists {
      case CourseRole.DOCENT | CourseRole.TUTOR => true
      case _ => false
    }

    (globalRole, privilegedByCourse) match {
      case (GlobalRole.ADMIN | GlobalRole.MODERATOR, _) | (_, true) => participants
      case _ => throw new ForbiddenException()
    }
  }

  /**
    * Register a user into a course
    * @param uid User id
    * @param cid Course id
    * @param req http request
    * @param res http response
    * @param body Content
    */
  @PutMapping(value = Array("/users/{uid}/courses/{cid}"), consumes = Array(MediaType.APPLICATION_JSON_VALUE))
  def register(@PathVariable("uid") uid: Int, @PathVariable("cid") cid: Int, req: HttpServletRequest, res: HttpServletResponse,
               @RequestBody body: JsonNode): Unit = {
    val user = authService.authorize(req, res)
    val role = Option(body).flatMap(_.retrive("roleId").asInt()).map(CourseRole.parse).getOrElse(CourseRole.STUDENT)

    (user.globalRole, user.id) match {
      case (GlobalRole.ADMIN | GlobalRole.MODERATOR, _) => courseRegistrationService.register(cid, uid, role)
      case (_, `uid`) => courseRegistrationService.register(cid, uid, CourseRole.STUDENT)
      case _ => throw new ForbiddenException()
    }
  }

  /**
    * Deregister a user from a course
    * @param uid Course id
    * @param cid Course id
    * @param req http request
    * @param res http response
    */
  @DeleteMapping(value = Array("/users/{uid}/courses/{cid}"))
  def deregister(@PathVariable("uid") uid: Int, @PathVariable("cid") cid: Int, req: HttpServletRequest, res: HttpServletResponse): Unit = {
    val user = authService.authorize(req, res)

    (user.globalRole, user.id) match {
      case (GlobalRole.ADMIN | GlobalRole.MODERATOR, _) | (_, `uid`) => courseRegistrationService.deregister(cid, uid)
      case _ => throw new ForbiddenException()
    }
  }
}