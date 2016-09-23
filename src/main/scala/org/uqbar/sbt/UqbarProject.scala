package org.uqbar.sbt

import scala.util.matching.Regex

import com.typesafe.sbt.GitPlugin
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.SbtGit.versionWithGit

import sbt.AutoPlugin
import sbt.Command.process
import sbt.Compile
import sbt.ConfigKey.configurationToKey
import sbt.Def.macroValueI
import sbt.Developer
import sbt.Keys.developers
import sbt.Keys.homepage
import sbt.Keys.isSnapshot
import sbt.Keys.libraryDependencies
import sbt.Keys.licenses
import sbt.Keys.organization
import sbt.Keys.organizationHomepage
import sbt.Keys.organizationName
import sbt.Keys.scalaSource
import sbt.Keys.scalacOptions
import sbt.Keys.scmInfo
import sbt.Keys.unmanagedSourceDirectories
import sbt.Keys.version
import sbt.Process
import sbt.Project
import sbt.ScmInfo
import sbt.Scoped.t2ToTable2
import sbt.State
import sbt.State.stateOps
import sbt.Test
import sbt.moduleIDConfigurable
import sbt.settingKey
import sbt.taskKey
import sbt.toGroupID
import sbt.url
import sbtrelease.ReleasePlugin
import sbtrelease.ReleasePlugin.autoImport.ReleaseKeys.commandLineReleaseVersion
import sbtrelease.ReleasePlugin.autoImport.ReleaseKeys.useDefaults
import sbtrelease.ReleasePlugin.autoImport.releaseIgnoreUntrackedFiles
import sbtrelease.ReleasePlugin.autoImport.releaseProcess
import sbtrelease.ReleasePlugin.autoImport.releaseTagComment
import sbtrelease.ReleasePlugin.autoImport.releaseTagName
import sbtrelease.ReleasePlugin.autoImport.releaseVcs
import sbtrelease.ReleaseStateTransformations.readVersion
import sbtrelease.ReleaseStateTransformations.reapply
import sbtrelease.ReleaseStateTransformations.runClean
import sbtrelease.ReleaseStateTransformations.runTest
import sbtrelease.ReleaseStateTransformations.tagRelease
import sbtrelease.Version
import sbtrelease.versionFormatError

object UqbarProject extends AutoPlugin {

	override def trigger = allRequirements

	override def requires = ReleasePlugin && GitPlugin

	object autoImport {
		lazy val changelogPattern = settingKey[Regex]("Pattern used to identify changelog entries on git commit comments")
		lazy val changelog = taskKey[Stream[String]]("Changelog entries since last release")
	}

	import autoImport._

	override lazy val projectSettings = {

		val uqbarHomepage = url("http://www.uqbar.org")

		versionWithGit ++ Seq(

			//─────────────────────────────────────────────────────────────────────────────────────────────────────────────────
			// General
			//─────────────────────────────────────────────────────────────────────────────────────────────────────────────────

			organization := "org.uqbar",
			organizationName := "Uqbar Foundation",
			organizationHomepage := Some(uqbarHomepage),
			homepage := Some(uqbarHomepage),
			developers += Developer("uqbar", "Uqbar", "uqbar-project@googlegroups.com ", uqbarHomepage),
			licenses += "LGPLv3" -> url("https://www.gnu.org/licenses/lgpl.html"),

			scmInfo := {
				val remote = """origin[ \t]+git@([^:]*):(.*)\.git[ \t]+\(fetch\)""".r
				Process("git remote -v").lines_!.collect {
					case remote(domain, repo) =>
						ScmInfo(
							url(s"https://$domain/$repo"),
							s"scm:git:https://$domain/$repo.git",
							Some(s"scm:git:git@$domain:$repo.git")
						)
				}.headOption
			},

			//─────────────────────────────────────────────────────────────────────────────────────────────────────────────────
			// Dependencies
			//─────────────────────────────────────────────────────────────────────────────────────────────────────────────────

			libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0" % "test",

			//─────────────────────────────────────────────────────────────────────────────────────────────────────────────────
			// Compilation
			//─────────────────────────────────────────────────────────────────────────────────────────────────────────────────

			scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),

			unmanagedSourceDirectories in Compile := Seq((scalaSource in Compile).value),
			unmanagedSourceDirectories in Test := Seq((scalaSource in Test).value),

			//─────────────────────────────────────────────────────────────────────────────────────────────────────────────────
			// Versioning
			//─────────────────────────────────────────────────────────────────────────────────────────────────────────────────

			git.useGitDescribe := true,
			git.uncommittedSignifier := None,
			isSnapshot := version.value matches ".*-.*",

			//─────────────────────────────────────────────────────────────────────────────────────────────────────────────────
			// Release
			//─────────────────────────────────────────────────────────────────────────────────────────────────────────────────

			changelogPattern := "![ \t]*(.*)".r,

			changelog := {
				val lastTag = Process("git describe --tags --abbrev=0").lines.headOption
				val changeWindow = lastTag.fold("HEAD"){ _ + "..HEAD" }
				Process(s"git log --pretty=%B $changeWindow").lines.flatMap(changelogPattern.value.unapplySeq).flatten
			},

			releaseTagComment <<= (releaseTagName, changelog) map { (tagName, changelog) =>
				val entries = if(changelog.nonEmpty) changelog map { "- " + _ } mkString "\n" else ""
				s"$tagName\n\n$entries"
			},

			releaseProcess := Seq(
				checkUnstagedAndUntracked,
				process("git pull", _: State),
				{ st: State => st.log.success(s"HERE!!!!"); st },
				confirmVersion,
				{ st: State => st.log.success(s"HERE2!!!!"); st },
				runClean,
				runTest,
				tagRelease,
				process("git push --follow-tags", _: State),
				process("publishSigned", _: State),
				process("sonatypeReleaseAll", _: State),
				{ st: State => st.log.success(s"Released as ${Project.extract(st).get(version)}"); st }
			)
		)
	}

	//═══════════════════════════════════════════════════════════════════════════════════════════════════════════════════
	// Release Steps
	//═══════════════════════════════════════════════════════════════════════════════════════════════════════════════════

	private lazy val checkUnstagedAndUntracked = { st: State =>
		val extracted = Project.extract(st)
		val vcs = extracted.get(releaseVcs).getOrElse(sys.error("Aborting release: Working directory is not a repository of a recognized VCS."))
		if (vcs.hasModifiedFiles) sys.error("Aborting release: Unstaged modified files")
		if (vcs.hasUntrackedFiles && !extracted.get(releaseIgnoreUntrackedFiles)) sys.error("Aborting release: Untracked files. Remove them or specify 'releaseIgnoreUntrackedFiles := true' in settings")
		st
	}

	private lazy val confirmVersion = { st: State =>
		val currentVersion = Version(Process("git describe").!!.drop(1).trim).getOrElse(versionFormatError)
		val suggestedReleaseVersion = currentVersion.withoutQualifier.bumpBugfix

		println(s"Current version: ${currentVersion.string}")
		val releaseVersion = readVersion(
			suggestedReleaseVersion.string,
			s"Release version [%s] : ",
			st.get(useDefaults).getOrElse(false),
			st.get(commandLineReleaseVersion).flatten
		)

		reapply(Seq(releaseTagName := s"v$releaseVersion"), st)
	}
}