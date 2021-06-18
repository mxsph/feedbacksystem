package de.thm.ii.fbs.services.classroom

import de.thm.ii.fbs.model.User
import de.thm.ii.fbs.services.conferences.ConferenceService
import de.thm.ii.fbs.services.conferences.conference.{BBBConference, Conference}
import de.thm.ii.fbs.services.persistence.CourseService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder

import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import scala.collection.mutable
import scala.language.postfixOps;

/**
  * Handles BBB requests.
  * @param templateBuilder Request template builder.
  * @param apiUrl the bbb api url
  * @param secret the bbb secret
  * @param originName the bbb meta data that identifies the origin
  * @param originVersion the bbb meta data the identifies the origin version
  * @param courseService the CourseService
  * @author Dominik Kröll
  */
@Service
class ClassroomService(templateBuilder: RestTemplateBuilder,
                       @Value("${services.bbb.service-url}") private val apiUrl: String,
                       @Value("${services.bbb.shared-secret}") private val secret: String,
                       @Value("${services.bbb.origin-name}") private val originName: String,
                       @Value("${services.bbb.origin-version}") private val originVersion: String,
                       courseService: CourseService
                ) extends ConferenceService {
  private val restTemplate = templateBuilder.build()

  /**
    * Creates a new Conference using BBB
    * @param courseId the id for the new conference
    * @return the newly created conference
    */
  override def createConference(courseId: Int): Conference = {
    val classroomId = UUID.randomUUID().toString
    // TODO: Custom Exception
    val course = courseService.find(courseId).get
    val studentPassword = UUID.randomUUID().toString
    val tutorPassword = UUID.randomUUID().toString
    val teacherPassword = UUID.randomUUID().toString

    // actual registering of conference against BBB api
    this.registerDigitalClassroom(classroomId, course.name, studentPassword, teacherPassword, tutorPassword)
    new DigitalClassroom(classroomId, courseId, studentPassword, teacherPassword, this)
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
      "moderatorPW" -> moderatorPassword,
      "tutorPW" -> tutorPassword,
      "meta_bbb-origin-server-name" -> originName,
      "meta_bbb-origin-version" -> originVersion,
      "meta_bbb-origin" -> "Greenlight"
    )
    val response = getBBBAPI("create", request)
    response.getStatusCode.is2xxSuccessful()
  }

  /**
    * Get join Link for conference users conference.
    * @param id Conference id to register.
    * @param user user name to register.
    * @param password password to register.
    * @return The uri of the registered conference
    */
  def getBBBConferenceLink(user: User, id: String, password: String): URI = {
    val link = buildBBBRequestURL("join", Map("fullName" -> s"${user.prename} ${user.surname}",
      "meetingID" -> id, "password" -> password))
    URI.create(link)
  }

  /**
    * Ends the conference
    * @param id the id of the meeting to end
    * @param moderatorPassword the moderatorPassword of the meeting to end
    * @return true if request succeeds
    */
  def endBBBConference(id: String, moderatorPassword: String): Boolean = {
    val response = getBBBAPI("end", Map("meetingID" -> id, "password" -> moderatorPassword))
    response.getStatusCode.is2xxSuccessful()
  }

  /**
    * Sends a GET-Request to the BBB API
    * @param method The BBB methode to invoked
    * @param params The params to send
    * @return The ResponseEntity
    */
  private def getBBBAPI(method: String, params: Map[String, String]): ResponseEntity[String] = {
    val url = buildBBBRequestURL(method, params)
    restTemplate.getForEntity(url, classOf[String])
  }

  /**
    * Builds a BBB API URL with checksum
    * @param method The method of the url
    * @param params The params of the url
    * @return The BBB API with checksum
    */
  private def buildBBBRequestURL(method: String, params: Map[String, String]): String = {
    val queryBuilder = UriComponentsBuilder.newInstance()
    val values = mutable.Buffer[String]();
    for ((key, value) <- params) {
      queryBuilder.queryParam(key, s"{$key}");
      values += value
    }
    var query = queryBuilder.encode.build.expand(values.toArray: _*).toString.substring(1)
    val checksum = computeHexSha1Hash(s"$method$query$secret")
    queryBuilder.queryParam("checksum", "{checksum}")
    values += checksum
    query = queryBuilder.encode.build.expand(values.toArray: _*).toString.substring(1)
    s"$apiUrl/api/$method?$query"
  }

  /**
    * Hashes input
    * @param input the input to hash
    * @return the hex-encoeded hash
    */
  private def computeHexSha1Hash(input: String): String = {
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update(input.getBytes("utf8"))
    val hash = digest.digest()
    toHexString(hash)
  }

  /**
    * Hex encodes input
    * @param input the input to encode
    * @return the encoded input
    */
  private def toHexString(input: Array[Byte]): String = input
    .map(b => String.format("%02x", b))
    .reduce((sb, s) => sb + s)

}

/**
Companion object carrying name attribute
  */
object ClassroomService {
  /**
    * name attribute
    */
  val name = "digital-classroom"
}


