package de.thm.ii.fbs.services.classroom

import de.thm.ii.fbs.model.CourseRole.STUDENT
import de.thm.ii.fbs.model.User
import de.thm.ii.fbs.model.classroom.JoinRoomBBBResponse
import de.thm.ii.fbs.services.conferences.ConferenceService
import de.thm.ii.fbs.services.conferences.conference.{BBBConference, Conference}
import de.thm.ii.fbs.services.persistence.{CourseRegistrationService, CourseService}
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.{HttpClientErrorException, HttpServerErrorException}
import org.springframework.web.util.UriComponentsBuilder

import java.net.URI
import java.util.UUID
import scala.collection.mutable
import scala.language.postfixOps

/**
  * Handles BBB requests.
  * @param templateBuilder Request template builder.
  * @param apiUrl the bbb api url
  * @param secret the bbb secret
  * @param originName the bbb meta data that identifies the origin
  * @param originVersion the bbb meta data the identifies the origin version
  * @param courseService the CourseService
  * @param courseRegistrationService the CourseRegistrationService

  * @author Dominik Kröll
  */
@Service
class ClassroomService(templateBuilder: RestTemplateBuilder,
                       @Value("${services.bbb.service-url}") private val apiUrl: String,
                       @Value("${services.bbb.shared-secret}") private val secret: String,
                       @Value("${services.bbb.origin-name}") private val originName: String,
                       @Value("${services.bbb.origin-version}") private val originVersion: String,
                       courseService: CourseService,
                       courseRegistrationService: CourseRegistrationService
                ) {

  private val restTemplate = templateBuilder.build()
  private val classrooms = mutable.HashMap[Int, DigitalClassroom]()

  def joinUser(courseId: Int, user: User): URI = {
    val courseRole = courseRegistrationService.getCoursePrivileges(user.id).getOrElse(courseId, STUDENT)
    val classroom = classrooms.getOrElseUpdate(courseId, createClassroom(courseId))
    classroom.getURL(user, courseRole)
  }

  /**
    * Future feature? currently there is no attendance tracking in FBS
    * @param courseId the courseId
    * @param user the user
    * @return
    */
  def leaveUser(courseId: Int, user: User): Boolean = {
    true
  }

  /**
    * Creates a new Conference using BBB
    * @param courseId the id for the new conference
    * @return the newly created conference
    */
  def createClassroom(courseId: Int): DigitalClassroom = {
    // TODO: Custom Exception
    val course = courseService.find(courseId).get
    val studentPassword = UUID.randomUUID().toString
    val tutorPassword = UUID.randomUUID().toString
    val teacherPassword = UUID.randomUUID().toString

    // actual registering of conference against BBB api
    this.registerDigitalClassroom(courseId.toString, course.name, studentPassword, teacherPassword, tutorPassword)
    new DigitalClassroom(courseId, studentPassword, tutorPassword, teacherPassword, this)
  }

  def recreateClassroom(courseId: Int): Unit = {
    this.classrooms.update(courseId, createClassroom(courseId))
  }

  /**
    * Register a new conference.
    * @param id Conference id to register.
    * @param meetingName Conference id to register.
    * @param studentPassword password to register.
    * @param moderatorPassword moderator password to register.
    * @param tutorPassword tutor passwort to register
    * @return boolean showing if creation of room was successful
    */
  def registerDigitalClassroom(id: String, meetingName: String, studentPassword: String, moderatorPassword: String, tutorPassword: String): Boolean = {
    val request = Map(
      "name" -> meetingName,
      "meetingID" -> id,
      "attendeePW" -> studentPassword,
      "tutorPW" -> tutorPassword,
      "moderatorPW" -> moderatorPassword,
      "meta_bbb-origin-server-name" -> originName,
      "meta_bbb-origin-version" -> originVersion,
      "meta_bbb-origin" -> "Greenlight"
    )
    val response = getClassroomApi("create", request)
    response.getStatusCode.is2xxSuccessful()
  }

  /**
    * Get join Link for conference users conference.
    * @param courseId courseId to register a classroom for.
    * @param user user name to join to classroom.
    * @param password password to join the user with.
    * @return The join URI for the specified user.
    */
  def getBBBConferenceLink(user: User, courseId: Int, password: String): URI = {
    val paramMap = Map(
      "fullName" -> s"${user.prename} ${user.surname}",
      "meetingID" -> courseId.toString,
      "password" -> password
    )
    val url = buildClassroomApiRequestUri("join", paramMap)
    var response: JoinRoomBBBResponse = null
    try {
      response = getJoinRoomResponse(url)
    } catch {
      case _: Throwable =>
        recreateClassroom(courseId)
        response = getJoinRoomResponse(buildClassroomApiRequestUri("join", paramMap))
    }
    URI.create(response.url)
  }

  private def getJoinRoomResponse(url: String): JoinRoomBBBResponse = {
    restTemplate.getForEntity(url, classOf[JoinRoomBBBResponse]).getBody
  }

  /**
    * Ends the conference
    * @param courseId the id of the meeting to end
    * @param teacherPassword the teacherPassword of the meeting to end
    * @return true if request succeeds
    */
  def endClassroom(courseId: Int, teacherPassword: String): Boolean = {
    val response = getClassroomApi("end", Map("meetingID" -> courseId.toString, "password" -> teacherPassword))
    response.getStatusCode.is2xxSuccessful()
  }

  /**
    * Sends a GET-Request to the BBB API
    * @param method The BBB methode to invoked
    * @param params The params to send
    * @return The ResponseEntity
    */
  private def getClassroomApi(method: String, params: Map[String, String]): ResponseEntity[String] = {
    val url = buildClassroomApiRequestUri(method, params)
    restTemplate.getForEntity(url, classOf[String])
  }

  /**
    * Builds a BBB API URL with checksum
    * @param method The method of the url
    * @param params The params of the url
    * @return The BBB API with checksum
    */
  private def buildClassroomApiRequestUri(method: String, params: Map[String, String]): String = {
    val queryBuilder = UriComponentsBuilder.newInstance()
    val values = mutable.Buffer[String]();
    for ((key, value) <- params) {
      queryBuilder.queryParam(key, s"{$key}");
      values += value
    }
    var query = queryBuilder.cloneBuilder().encode.build.expand(values.toArray: _*).toString.substring(1)
    val checksum = DigestUtils.sha1Hex(s"$method$query$secret")
    queryBuilder.queryParam("checksum", s"$checksum")
    values += checksum
    query = queryBuilder.build.expand(values.toArray: _*).toString.substring(1)
    s"$apiUrl/api/$method?$query"
  }
}
