# Release of Silverpeas

The variables used in this document:

  * VERSION\_TO\_RELEASE: the new version to release (for example 6.0)
  * NEXT\_DEV\_VERSION: the next development version (for example 6.1)
  * GPG\_PASSPHRASE: the passphrase to unlock the GPG key to use in the the artifacts signing
  * GPG\_KEY: the name of the key in your GPG key database to sign the artifact

## The different release modes

### <a name="jenkins"></a>The Silverpeas project release Jenkins pipeline

The project [Jenkins Pipelines](https://github.com/Silverpeas/Jenkins-Pipelines) provides a set 
of Jenkins pipeline definitions, each of them within a `Jenkinsfile`. Among them several pipelines 
have been written to automate the release of the Silverpeas projects. They are defined in the 
`src/releases/` directory of the project; each subfolder matches a Silverpeas project and in 
each of them the release pipeline is defined within a `Jenkinsfile`.

The only operation to do is then to read the recommendations and constrains of the pipeline 
usage at the header of the `Jenkinsfile` and then create a dedicated job in the Jenkins server if 
it is not already done.
  
### <a name="maven"></a>The Maven Release Plugin

First, update in the pom.xml of the project the version of the dependencies on others Silverpeas projects for their new stable version if necessary and commit the change:

```bash
$ git commit -am "Update the SNAPSHOT dependencies to their stable version for the release VERSION_TO_RELEASE"
```

Second, prepare the release of the new version by specifying some the properties of the release:

```bash
$ mvn --batch-mode release:prepare -Dtag=VERSION_TO_RELEASE -Prelease-sign-artifacts -DreleaseVersion=VERSION_TO_RELEASE -DdevelopmentVersion=NEXT_DEV_VERSION -Darguments="-Dgpg.passphrase=GPG_PASSPHRASE -Dgpg.keyname=GPG_KEY"
```

Then, finalize the release of the new version:

```bash
$ mvn release:perform -Prelease-sign-artifacts -Darguments="-Dgpg.passphrase=GPG_PASSPHRASE -Dgpg.keyname=GPG_KEY"
```

If there is something wrong or in the case of a failure, you can rollback the release:

```bash
$ mvn release:rollback
$ git tag -d VERSION_TO_RELEASE
```

And if the tag was already pushed into the remote Git repository:

```bash
$ git push origin :VERSION_TO_RELEASE
```
    
Once released, update in the pom.xml of the project the version of the dependencies on others Silverpeas projects for their new development version if necessary and commit the changes.
(According to the dependencies (`Silverpeas-JCR-AccessControl` for example), the versions can be kept in their stable version unless they have any change.)

```bash
$ git commit -a --amend
```
    
Finally, validate all the changes:

```bash
$ mvn clean deploy
$ git push
$ git push --tags
```
    
### <a name="by_hand"></a>By hand

#### For the Maven projects

  1. Update in the pom.xml of the project the version of the dependencies on others Silverpeas projects for their new stable version if necessary
  2. Update in the pom.xml the version of the project to VERSION\_TO\_RELEASE by using the Maven 
     Versions plugin (this will update also the subprojects if any):
     ```bash
     $ mvn -U versions:set -DgenerateBackupPoms=false -DnewVersion=VERSION_TO_RELEASE
     ```
  3. [Perform](#maven-step-4) the release of VERSION\_TO\_RELEASE
  4. Update in the pom.xml of the project the version of the dependencies on others Silverpeas 
     project for their new development version if necessary
  5. Update in the pom.xml the version of the project to NEXT\_DEV\_VERSION by using the Maven 
     Versions plugin:
     ```bash
     $ mvn -U versions:set -DgenerateBackupPoms=false -DnewVersion=NEXT_DEV_VERSION
     ```
  6. Don't forget to update also as above the subprojects if any.
  7. [Perform](#maven-step-8) the deployment of NEXT\_DEV\_VERSION
  8. [Validate](#maven-step-9) both the release and the post-release
  
<a name="maven-step-4"></a>To perform by hand the release, please execute the following command lines (step 4):
  
```bash
$ git commit -am "Prepare release VERSION_TO_RELEASE"
$ mvn clean deploy -Prelease-sign-artifacts -Dgpg.passphrase=GPG_PASSPHRASE -Dgpg.keyname=GPG_KEY
$ git tag VERSION_TO_RELEASE
```

<a name="maven-step-8"></a>To perform the post-release, please execute the following command lines (step 8):

```bash
$ git commit -am "Prepare for next development iteration"
$ mvn clean deploy
```

<a name="maven-step-9"></a>To validate the whole changes (step 9):

```bash
    $ git push
    $ git push --tags
```

#### For the Gradle projects

  1. Update in the build.gradle file of the project the version of the dependencies on others Silverpeas projects for their new stable version if necessary
  2. Update in the build.gradle the version of the project to VERSION\_TO\_RELEASE
  3. Don't forget to update also as above the subprojects if any.
  4. [Perform](#gradle-step-4) the release of VERSION\_TO\_RELEASE
  5. Update in the build.gradle of the project the version of the dependencies on others Silverpeas project for their new development version if necessary
  6. Update in the build.gradle the version of the project to NEXT\_DEV\_VERSION.
  7. Don't forget to update also as above the subprojects if any.
  8. [Perform](#gradle-step-8) the deployment of NEXT\_DEV\_VERSION
  9. [Validate](#gradle-step-9) both the release and the post-release
  
<a name="gradle-step-4"></a>To perform by hand the release, please execute the following command lines (step 4):
  
```bash
    $ git commit -am "Prepare release VERSION_TO_RELEASE"
    $ ./gradlew clean test install publish
    $ git tag VERSION_TO_RELEASE
```

<a name="gradle-step-8"></a>To perform the post-release, please execute the following command lines (step 8):
  
```bash
    $ git commit -am "Prepare for next development iteration"
    $ ./gradlew clean test install publish
```

<a name="gradle-step-9"></a>To validate the whole changes (step 9):

```bash
    $ git push
    $ git push --tags
```

## The release process

Some relationship rules:

  * `silverpeas-dependencies-bom`, `silverpeas-test-dependencies-bom` and `Silverpeas-Project` form all of them a set.
    Any change in this set implies a release of the whole set at the same version.
  * `Silverpeas-Core`, `Silverpeas-Components`, `Silverpeas-Assembly`, 
    `Silverpeas-Looks`, `Silverpeas-Setup`, and `Silverpeas-Distribution` form all of them a set.
    Any change in this set implies a release of the whole set at the same version.
    Generally speaking, this whole set depends on the change in `Silverpeas-Core` and in `Silverpeas-Components`.
    If a change is required in `Silverpeas-Setup` or in `Silverpeas-Distribution`, their release will be relative to the release of both `Silverpeas-Core` and of `Silverpeas-Components`.
    Nevertheless, if a fix is required in `Silverpeas-Setup` or in `Silverpeas-Distribution`, the two can be released with a minor version independently of `Silverpeas-Core` and of `Silverpeas-Components` (in this case, don't forget to set explicitly the version of `Silverpeas-Setup` directly in the `build.gradle` of `Silverpeas-Distribution`).

Some definitions:

  * condition: what are the conditions for the project to be released. If those conditions aren't satisfied then the project shouldn't be released.
  * pre-release: what are the steps to follow before the release of the new version
  * constraint: what is the constraint in the release

Now the ordered process:

1. silverpeas-dependencies-bom
    * *condition*: either itself 
    * *constraint*: the version of the project should be equal to the version of both 
      `silverpeas-test-dependencies-bom` and `Silverpeas-Project`.

2. silverpeas-test-dependencies-bom
    * *condition*: either itself or `silverpeas-dependencies-bom`
    * *constraint*: the version of the project should be equal to the version of both
        `silverpeas-dependencies-bom` and `Silverpeas-Project`.
    
3. Silverpeas-Project
    * *condition*: `silverpeas-dependencies` and `silverpeas-test-dependencies` are released at the same version the project should be released
    * *pre-release*: update the dependencies on both `silverpeas-depencencies` and `silverpeas-test-dependencies`
    * *constraint*: the version of the project should be equal to the version of both
        `silverpeas-test-dependencies-bom` and `silverpeas-dependencies-bom`.
    
4. Silverpeas-Core
    * *pre-release*: update the dependency of the parent POM on the latest stable version of `Silverpeas-Project`
    
5. Silverpeas-Components
    * *pre-release*: update the dependency of the parent POM on the latest stable version of `Silverpeas-Project`
    * *constraint*: the version of the project should be equal to the version `Silverpeas-Core`.
6. Silverpeas-Looks
    * *pre-release*: update the dependency of the parent POM on the latest stable version of `Silverpeas-Project`
    * *constraint*: the version of the project should be equal to the version of both 
      `Silverpeas-Core` and `Silverpeas-Components`.
    
7. Silverpeas-Assembly
    * *condition*: `Silverpeas-Core` and `Silverpeas-Components` are released
    * *pre-release*: update the dependency of the parent POM on the latest stable version of `Silverpeas-Project` and update the dependency on `Silverpeas-Jackrabbit-JCA` if any
    * *constraint*: the version of the project should be equal to the version of both
        `Silverpeas-Core`, `Silverpeas-Components`, and `Silverpeas-Looks`.
    
8. Silverpeas-Setup
    * *condition*: Silverpeas-Assembly is released
    
9. Silverpeas-Distribution
    * *condition*: `Silverpeas-Setup` is released
    * *constrain*: The version to release must be the same as the released version of `Silverpeas-Core`, `Silverpeas-Components`, and
      `Silverpeas-Assembly`.
