package de.thm.ii.fbs.services.conferences

import java.net.URI

import de.thm.ii.fbs.model.User

/**
  * A Conference created by a ConferenceService
  */
abstract class Conference {
  /**
    * The id of the conference
    */
  abstract val id: String

  /**
    * The name of the ConferenceService used to create the Conference
    */
  abstract val serviceName: String

  /**
    * Gets the http URL for the conference
    * @param user the user for which to generate the URL
    * @param moderator the type of url to generate
    * @return the conference url
    */
  abstract def getURL(user: User, moderator: Boolean = false): URI

  /**
    * Creates a map containing information about the Conference
    * @return the map
    */
  abstract def toMap: Map[String, String]
}
