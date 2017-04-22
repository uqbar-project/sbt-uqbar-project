# SBT: Uqbar Project

This *SBT* plugin containing common definitions and release workflow for Uqbar's sbt projects.

## Requirements

- SBT 0.12.10+ (previous versions of sbt have issues converting from sbt.url to java.net.URL type)

## Setup

To include this plugin in your *SBT* build, just add the following line to your `project/plugins.sbt` file, or your
global *SBT* configuration:

```scala
addSbtPlugin("org.uqbar" % "sbt-uqbar-project" % "latest.integration")
```


## Definitions

This plugin provides the general settings for any standard *SBT* based Uqbar project. These settings include:

- General organization info (such as name, urls and emails).
- Licenses.
- SCM info (reflectively obtained from git).
- Default scalac arguments and directory setup.
- Common dependencies.
- Automatically generated semantic versioning (based on git describe).
- Standard release process.
- Automatic changelog generation (based on git commits).

### Dependencies

In order to keep projects as lightweight as possible, only plugins meant to be used in **all** projects (such as 
[sbt-git](https://github.com/sbt/sbt-git), [sbt-release](https://github.com/sbt/sbt-release) and 
[sbt-sonatype](https://github.com/xerial/sbt-sonatype)) are included. Only [scalatest](http://www.scalatest.org/) is
included as library dependency.

### Versioning

This plugin enables *sbt-git*'s versioning. Instead of setting a value for the *version* key, projects will get
automatically versioned based on git [describe](https://git-scm.com/docs/git-describe). Release versions will be named
after tag they are based on, while snapshot versions will append the number of commits and last commit identifier to the
name of the latest release. We encourage the use of default format along with *semantic versioning* for tag names, but
this can be configured as explained on *sbt-release* homepage.

### Changelog

Projects including this plugin will be able to automatically generate a changelog by parsing the comments on git commits
since last version was released. Changelog will include any commit comment line that matches the `changelogPattern`
setting which, by default, will select any line starting with a *"!"* character. 

This changelog can be obtained by calling the `changelog` task and is automatically included on tag comments during 
release.

### Release

This plugin provides a default setup for *sbt-release*, defining a release process that will pull the latest branch head,
run the tests, generate and push a new tag and publish the generated artifacts.

You can perform a release by running the `release` command.


### Publishing

The release process will automatically deploy the generated artifacts to Uqbar's Sonatype repository, as long as your PGP 
`credentials` and `pgpPassphrase` keys are properly set.


## Contributions

Yes, please! Pull requests are always welcome, just try to keep it small and clean.


## License

This code is open source software licensed under the [LGPL v3 License](https://www.gnu.org/licenses/lgpl.html) by [The Uqbar Foundation](http://www.uqbar-project.org/). Feel free to use it accordingly.
