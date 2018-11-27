@Library('jenkins-shared-libraries')
import com.blackducksoftware.jenkins.pipeline.Libraries

def libraries = new Libraries()
def properties
def utilities = libraries.getUtilities()
def projectName="blackduck-cfssl"
def shortImageName=projectName
def longImageName="blackducksoftware/${shortImageName}"
def lastCommit

def branch = env.BRANCH ?: 'origin/master'

node('docker'){
  try{
    stage('Pull from SCM'){
      checkout scm: [$class: 'GitSCM', branches: [[name: "${branch}"]], userRemoteConfigs: [[url: "ssh://git@github.com/blackducksoftware/${projectName}.git"]], extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: "${projectName}"]]]
      properties = libraries.getProperties(this, readFile("${projectName}/release.properties"))
      lastCommit = sh(returnStdOut:true, script: "cd ${projectName} && git rev-parse HEAD")

      sh "echo CFSSL_COMMITHASH=${lastCommit} > git-hashes.properties"
      sh "echo VERSION=${properties.version} >> git-hashes.properties"
    }
    stage('Build'){
      try{
        sh "cd ${projectName} && docker build --pull --build-arg VERSION=${properties.versionNoSnapshot} --build-arg LASTCOMMIT=${lastCommit} --build-arg BUILDTIME=\$(date) --build-arg BUILD=${env.JENKINS_TAG} -t ${longImageName}:${properties.version} ."
        utilities.pushImage(shortImageName, properties.version, env.DOCKER_REGISTRY_SNAPSHOT, env.ARTIFACTORY_DEPLOYER_USER, env.ARTIFACTORY_DEPLOYER_PASSWORD)
      } finally {
        utilities.rmvDkrImage("${longImageName}:${properties.version}")
      }
      archiveArtifacts 'git-hashes.properties'
    }
  } catch(Exception e){
    echo e.getMessage()
    throw e
  }
}

