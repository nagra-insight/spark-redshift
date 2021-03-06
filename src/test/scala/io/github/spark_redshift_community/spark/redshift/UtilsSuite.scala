/*
 * Copyright 2015 TouchType Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.spark_redshift_community.spark.redshift

import java.net.URI

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.BucketLifecycleConfiguration
import com.amazonaws.services.s3.model.BucketLifecycleConfiguration.Rule
import org.mockito.Matchers.anyString
import org.mockito.Mockito
import org.scalatest.{FunSuite, Matchers}
import org.mockito.Mockito._

/**
 * Unit tests for helper functions
 */
class UtilsSuite extends FunSuite with Matchers {

  test("joinUrls preserves protocol information") {
    Utils.joinUrls("s3n://foo/bar/", "/baz") shouldBe "s3n://foo/bar/baz/"
    Utils.joinUrls("s3n://foo/bar/", "/baz/") shouldBe "s3n://foo/bar/baz/"
    Utils.joinUrls("s3n://foo/bar/", "baz/") shouldBe "s3n://foo/bar/baz/"
    Utils.joinUrls("s3n://foo/bar/", "baz") shouldBe "s3n://foo/bar/baz/"
    Utils.joinUrls("s3n://foo/bar", "baz") shouldBe "s3n://foo/bar/baz/"
  }

  test("joinUrls preserves credentials") {
    assert(
      Utils.joinUrls("s3n://ACCESSKEY:SECRETKEY@bucket/tempdir", "subdir") ===
      "s3n://ACCESSKEY:SECRETKEY@bucket/tempdir/subdir/")
  }

  test("fixUrl produces Redshift-compatible equivalents") {
    Utils.fixS3Url("s3a://foo/bar/12345") shouldBe "s3://foo/bar/12345"
    Utils.fixS3Url("s3n://foo/bar/baz") shouldBe "s3://foo/bar/baz"
  }

  test("addEndpointToUrl produces urls with endpoints added to host") {
    Utils.addEndpointToUrl("s3a://foo/bar/12345") shouldBe "s3a://foo.s3.amazonaws.com/bar/12345"
    Utils.addEndpointToUrl("s3n://foo/bar/baz") shouldBe "s3n://foo.s3.amazonaws.com/bar/baz"
  }

  test("temp paths are random subdirectories of root") {
    val root = "s3n://temp/"
    val firstTempPath = Utils.makeTempPath(root)

    Utils.makeTempPath(root) should (startWith (root) and endWith ("/")
      and not equal root and not equal firstTempPath)
  }

  test("removeCredentialsFromURI removes AWS access keys") {
    def removeCreds(uri: String): String = {
      Utils.removeCredentialsFromURI(URI.create(uri)).toString
    }
    assert(removeCreds("s3n://bucket/path/to/temp/dir") === "s3n://bucket/path/to/temp/dir")
    assert(
      removeCreds("s3n://ACCESSKEY:SECRETKEY@bucket/path/to/temp/dir") ===
      "s3n://bucket/path/to/temp/dir")
  }

  test("getRegionForRedshiftCluster") {
    val redshiftUrl =
      "jdbc:redshift://example.secret.us-west-2.redshift.amazonaws.com:5439/database"
    assert(Utils.getRegionForRedshiftCluster("mycluster.example.com") === None)
    assert(Utils.getRegionForRedshiftCluster(redshiftUrl) === Some("us-west-2"))
  }

  test("checkThatBucketHasObjectLifecycleConfiguration when no rule") {
    // Configure a mock S3 client so that we don't hit errors when trying to access AWS in tests.
    val mockS3Client = Mockito.mock(classOf[AmazonS3Client], Mockito.RETURNS_SMART_NULLS)

    when(mockS3Client.getBucketLifecycleConfiguration(anyString())).thenReturn(
      new BucketLifecycleConfiguration().withRules(
        new Rule().withStatus(BucketLifecycleConfiguration.DISABLED)
      ))
    assert(Utils.checkThatBucketHasObjectLifecycleConfiguration(
      "s3a://bucket/path/to/temp/dir", mockS3Client) === true)
  }

  test("checkThatBucketHasObjectLifecycleConfiguration when rule with prefix") {
    // Configure a mock S3 client so that we don't hit errors when trying to access AWS in tests.
    val mockS3Client = Mockito.mock(classOf[AmazonS3Client], Mockito.RETURNS_SMART_NULLS)

    when(mockS3Client.getBucketLifecycleConfiguration(anyString())).thenReturn(
      new BucketLifecycleConfiguration().withRules(
        new Rule().withPrefix("/path/").withStatus(BucketLifecycleConfiguration.ENABLED)
      ))
    assert(Utils.checkThatBucketHasObjectLifecycleConfiguration(
      "s3a://bucket/path/to/temp/dir", mockS3Client) === true)
  }

  test("checkThatBucketHasObjectLifecycleConfiguration when rule without prefix") {
    // Configure a mock S3 client so that we don't hit errors when trying to access AWS in tests.
    val mockS3Client = Mockito.mock(classOf[AmazonS3Client], Mockito.RETURNS_SMART_NULLS)

    when(mockS3Client.getBucketLifecycleConfiguration(anyString())).thenReturn(
      new BucketLifecycleConfiguration().withRules(
        new Rule().withStatus(BucketLifecycleConfiguration.ENABLED)
      ))
    assert(Utils.checkThatBucketHasObjectLifecycleConfiguration(
      "s3a://bucket/path/to/temp/dir", mockS3Client) === true)
  }

  test("checkThatBucketHasObjectLifecycleConfiguration when error in checking") {
    // Configure a mock S3 client so that we don't hit errors when trying to access AWS in tests.
    val mockS3Client = Mockito.mock(classOf[AmazonS3Client], Mockito.RETURNS_SMART_NULLS)

    when(mockS3Client.getBucketLifecycleConfiguration(anyString()))
      .thenThrow(new NullPointerException())
    assert(Utils.checkThatBucketHasObjectLifecycleConfiguration(
      "s3a://bucket/path/to/temp/dir", mockS3Client) === false)
  }
}
