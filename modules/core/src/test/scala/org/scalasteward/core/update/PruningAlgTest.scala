package org.scalasteward.core.update

import io.circe.parser.decode
import munit.FunSuite
import org.scalasteward.core.data.RepoData
import org.scalasteward.core.mock.MockContext._
import org.scalasteward.core.mock.MockState
import org.scalasteward.core.repocache.RepoCache
import org.scalasteward.core.repoconfig.RepoConfig
import org.scalasteward.core.vcs.data.Repo

class PruningAlgTest extends FunSuite {
  test("needsAttention") {
    val repo = Repo("fthomas", "scalafix-test")
    val Right(repoCache) = decode[RepoCache](
      s"""|{
          |  "sha1": "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |  "dependencyInfos": [],
          |  "maybeRepoConfig": {
          |    "pullRequests": {
          |      "frequency": "@daily"
          |    }
          |  }
          |}""".stripMargin
    )
    val pullRequestsFile =
      config.workspace / "store/pull_requests/v2/fthomas/scalafix-test/pull_requests.json"
    val pullRequestsContent =
      s"""|{
          |  "https://github.com/fthomas/scalafix-test/pull/27" : {
          |    "baseSha1" : "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |    "update" : {
          |      "Single" : {
          |        "crossDependency" : [
          |          {
          |            "groupId" : "org.scalatest",
          |            "artifactId" : {
          |              "name" : "scalatest",
          |              "maybeCrossName" : "scalatest_2.12"
          |            },
          |            "version" : "3.0.8",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          }
          |        ],
          |        "newerVersions" : [
          |          "3.1.0"
          |        ],
          |        "newerGroupId" : null
          |      }
          |    },
          |    "state" : "open",
          |    "entryCreatedAt" : 1581969227183
          |  }
          |}""".stripMargin
    val initial = MockState.empty
      .add(pullRequestsFile, pullRequestsContent)
    val data = RepoData(repo, repoCache, repoCache.maybeRepoConfig.getOrElse(RepoConfig.empty))
    val state = pruningAlg.needsAttention(data).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      commands = Vector(List("read", pullRequestsFile.toString)),
      logs = Vector(
        (None, "Find updates for fthomas/scalafix-test"),
        (None, "Found 0 updates"),
        (None, "fthomas/scalafix-test is up-to-date")
      )
    )
    assertEquals(state, expected)
  }

  test("needsAttention: 0 updates when includeScala not specified in repo config") {
    val repo = Repo("fthomas", "scalafix-test")
    val Right(repoCache) = decode[RepoCache](
      s"""|{
          |  "sha1": "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |  "dependencyInfos" : [
          |    {
          |      "value" : [
          |        {
          |          "dependency" : {
          |            "groupId" : "org.scala-lang",
          |            "artifactId" : {
          |              "name" : "scala-library",
          |              "maybeCrossName" : null
          |            },
          |            "version" : "2.12.10",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          },
          |          "filesContainingVersion" : [
          |            "build.sbt"
          |          ]
          |        }
          |      ],
          |      "resolvers" : [
          |        {
          |          "MavenRepository" : {
          |            "name" : "public",
          |            "location" : "https://foobar.org/maven2/"
          |          }
          |        }
          |      ]
          |    }
          |  ],
          |  "maybeRepoConfig": {
          |    "pullRequests": {
          |      "frequency": "@daily"
          |    }
          |  }
          |}""".stripMargin
    )
    val pullRequestsFile =
      config.workspace / "store/pull_requests/v2/fthomas/scalafix-test/pull_requests.json"
    val pullRequestsContent =
      s"""|{
          |  "https://github.com/fthomas/scalafix-test/pull/27" : {
          |    "baseSha1" : "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |    "update" : {
          |      "Single" : {
          |        "crossDependency" : [
          |          {
          |            "groupId" : "org.scalatest",
          |            "artifactId" : {
          |              "name" : "scalatest",
          |              "maybeCrossName" : "scalatest_2.12"
          |            },
          |            "version" : "3.0.8",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          }
          |        ],
          |        "newerVersions" : [
          |          "3.1.0"
          |        ],
          |        "newerGroupId" : null
          |      }
          |    },
          |    "state" : "open",
          |    "entryCreatedAt" : 1581969227183
          |  }
          |}""".stripMargin
    val versionsFile =
      config.workspace / "store/versions/v1/https/foobar.org/maven2/org/scala-lang/scala-library/versions.json"
    val versionsContent =
      s"""|{
          |  "updatedAt" : 9999999999999,
          |  "versions" : [
          |    "2.12.9",
          |    "2.12.10",
          |    "2.12.11",
          |    "2.13.0",
          |    "2.13.1"
          |  ]
          |}
          |""".stripMargin
    val initial = MockState.empty
      .add(pullRequestsFile, pullRequestsContent)
      .add(versionsFile, versionsContent)
    val data = RepoData(repo, repoCache, repoCache.maybeRepoConfig.getOrElse(RepoConfig.empty))
    val state = pruningAlg.needsAttention(data).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      commands = Vector(List("read", pullRequestsFile.toString)),
      logs = Vector(
        (None, "Find updates for fthomas/scalafix-test"),
        (None, "Found 0 updates"),
        (None, "fthomas/scalafix-test is up-to-date")
      )
    )
    assertEquals(state, expected)
  }

  test("needsAttention: update scala-library when includeScala=true in repo config") {
    val repo = Repo("fthomas", "scalafix-test")
    val Right(repoCache) = decode[RepoCache](
      s"""|{
          |  "sha1": "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |  "dependencyInfos" : [
          |    {
          |      "value" : [
          |        {
          |          "dependency" : {
          |            "groupId" : "org.scala-lang",
          |            "artifactId" : {
          |              "name" : "scala-library",
          |              "maybeCrossName" : null
          |            },
          |            "version" : "2.12.10",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          },
          |          "filesContainingVersion" : [
          |            "build.sbt"
          |          ]
          |        }
          |      ],
          |      "resolvers" : [
          |        {
          |          "MavenRepository" : {
          |            "name" : "public",
          |            "location" : "https://foobar.org/maven2/"
          |          }
          |        }
          |      ]
          |    }
          |  ],
          |  "maybeRepoConfig": {
          |    "pullRequests": {
          |      "frequency": "@daily"
          |    },
          |    "updates" : {
          |      "pin" : [
          |      ],
          |      "allow" : [
          |      ],
          |      "ignore" : [
          |      ],
          |      "limit" : null,
          |      "includeScala" : true
          |    }
          |  }
          |}""".stripMargin
    )
    val pullRequestsFile =
      config.workspace / "store/pull_requests/v2/fthomas/scalafix-test/pull_requests.json"
    val pullRequestsContent =
      s"""|{
          |  "https://github.com/fthomas/scalafix-test/pull/27" : {
          |    "baseSha1" : "12def27a837ba6dc9e17406cbbe342fba3527c14",
          |    "update" : {
          |      "Single" : {
          |        "crossDependency" : [
          |          {
          |            "groupId" : "org.scalatest",
          |            "artifactId" : {
          |              "name" : "scalatest",
          |              "maybeCrossName" : "scalatest_2.12"
          |            },
          |            "version" : "3.0.8",
          |            "sbtVersion" : null,
          |            "scalaVersion" : null,
          |            "configurations" : null
          |          }
          |        ],
          |        "newerVersions" : [
          |          "3.1.0"
          |        ],
          |        "newerGroupId" : null
          |      }
          |    },
          |    "state" : "open",
          |    "entryCreatedAt" : 1581969227183
          |  }
          |}""".stripMargin
    val versionsFile =
      config.workspace / "store/versions/v1/https/foobar.org/maven2/org/scala-lang/scala-library/versions.json"
    val versionsContent =
      s"""|{
          |  "updatedAt" : 9999999999999,
          |  "versions" : [
          |    "2.12.9",
          |    "2.12.10",
          |    "2.12.11",
          |    "2.13.0",
          |    "2.13.1"
          |  ]
          |}
          |""".stripMargin
    val initial = MockState.empty
      .add(pullRequestsFile, pullRequestsContent)
      .add(versionsFile, versionsContent)
    val data = RepoData(repo, repoCache, repoCache.maybeRepoConfig.getOrElse(RepoConfig.empty))
    val state = pruningAlg.needsAttention(data).runS(initial).unsafeRunSync()
    val expected = initial.copy(
      commands = Vector(
        List("read", versionsFile.toString),
        List("read", pullRequestsFile.toString),
        List("read", versionsFile.toString),
        List("read", pullRequestsFile.toString),
        List("read", pullRequestsFile.toString)
      ),
      logs = Vector(
        (None, "Find updates for fthomas/scalafix-test"),
        (None, "Found 1 update:\n  org.scala-lang:scala-library : 2.12.10 -> 2.12.11"),
        (
          None,
          "fthomas/scalafix-test is outdated:\n  new version: org.scala-lang:scala-library : 2.12.10 -> 2.12.11"
        )
      )
    )
    assertEquals(state, expected)
  }
}
