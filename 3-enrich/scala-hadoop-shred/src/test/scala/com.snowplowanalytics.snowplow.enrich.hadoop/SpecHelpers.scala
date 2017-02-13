/*
 * Copyright (c) 2012-2014 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics
package snowplow
package enrich
package hadoop

// Iglu Scala Client
import iglu.client.Resolver

// Scalaz
import scalaz._
import Scalaz._

// This project
import utils.JsonUtils

/**
 * Holds general helpers for running the
 * Specs2 tests.
 */
object SpecHelpers {

  // Internal
  private val igluConfigField = "IgluConfigField"
  private val duplicateStorageField = "DuplicateStorageConfigField"

  /**
   * Standard Iglu configuration.
   */
  val IgluConfig =
     """|{
          |"schema": "iglu:com.snowplowanalytics.iglu/resolver-config/jsonschema/1-0-0",
          |"data": {
            |"cacheSize": 500,
            |"repositories": [
              |{
                |"name": "Iglu Central",
                |"priority": 0,
                |"vendorPrefixes": [ "com.snowplowanalytics" ],
                |"connection": {
                  |"http": {
                    |"uri": "http://iglucentral.com"
                  |}
                |}
              |}
            |]
          |}
        |}""".stripMargin.replaceAll("[\n\r]","")

  /**
   * Builds an Iglu resolver from
   * the above Iglu configuration.
   */
  val IgluResolver = (for {
    json <- JsonUtils.extractJson(igluConfigField, IgluConfig)
    reso <- Resolver.parse(json)
  } yield reso).getOrElse(throw new RuntimeException("Could not build an Iglu resolver, should never happen"))


  /**
    * Duplicate storage configuration, enabling cross-batch deduplication on CI environment
    * If CI is set and all envvars are available it becomes valid schema
    * If not all envvars are available, but CI is set - it will throw runtime exception as impoperly configured CI environment
    * If not all envvars are available, but CI isn't set - it will return empty JSON, which should not be used anywhere (in JobSpecHelpers)
    */
  val DuplicateStorageConfig = getDynamodbConfig.map { case (accessKeyId, secretAccessKey, awsRegion, dynamodbTable) =>
    s"""|{
         |"schema": "iglu:com.snowplowanalytics.snowplow.storage/amazon_dynamodb_config/jsonschema/1-0-0",
         |"data": {
           |"name": "local",
           |"accessKeyId": "$accessKeyId",
           |"secretAccessKey": "$secretAccessKey",
           |"awsRegion": "$awsRegion",
           |"dynamodbTable": "$dynamodbTable",
           |"purpose": "DUPLICATE_TRACKING"
         |}
       |}""".stripMargin
  } match {
    case Success(config) => config
    case Failure(error) if CI => throw new RuntimeException("Cannot get AWS DynamoDB CI configuration. " + error.toList.mkString(", "))
    case Failure(_) => "{}"
  }

  /**
   * Check if tests are running in continous integration environment
   */
  def CI = sys.env.get("CI") match {
    case Some("true") => true
    case _ =>
      println("WARNING! Test requires CI envvar to be set and DynamoDB local instance to be running")
      false
  }

  /**
   * Get environment variable wrapped into `Validation`
   */
  def getEnv(envvar: String): ValidationNel[String, String] = sys.env.get(envvar) match {
    case Some(v) => Success(v)
    case None => Failure(NonEmptyList(s"Environent variable [$envvar] is not available"))
  }

  /**
   * Get environment varables required to access to DynamoDB
   */
  def getDynamodbConfig = {
    val configValidation = getEnv("ACCESS_KEY_ID") |@| getEnv("SECRET_ACCESS_KEY") |@| getEnv("AWS_REGION") |@| getEnv("DYNAMODB_TABLE")
    configValidation { (accessKeyId, secretAccessKey, awsRegion, dynamodbTable) =>
      (accessKeyId, secretAccessKey, awsRegion, dynamodbTable)
    }
  }
}
