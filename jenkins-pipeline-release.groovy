@Library('jenkins-shared-libraries')
import com.blackducksoftware.jenkins.pipeline.Libraries

def libraries = new Libraries()
def properties
def release = libraries.getRelease()
def utilities = libraries.getUtilities()
def projectName="blackduck-cfssl"
def shortImageName=projectName
def longImageName="blackducksoftware/${shortImageName}"
env.BRANCH = env.BRANCH ?: "master"

node('docker'){
  try{
    stage('Pull from SCM'){
      step([$class: 'CopyArtifact', filter: 'git-hashes.properties', fingerprintArtifacts: true, projectName: 'CI', selector: [$class: 'TriggeredBuildSelector', allowUpstreamDependencies: false, fallbackToLastSuccessful: true, upstreamFilterStrategy: 'UseGlobalSetting']])
      String[] lines = readFile('git-hashes.properties').split('\n')
      for (int i=0; i < lines.length; i++){
          String[] parts = lines[i].split('=')
          env."${parts[0]}" = parts[1]
      }
      checkout scm: [$class: 'GitSCM', branches: [[name: "${env.CFSSL_COMMITHASH}"]], userRemoteConfigs: [[url: "ssh://git@github.com/blackducksoftware/${projectName}.git"]], extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: "${projectName}"]]]
      properties = libraries.getProperties(this, readFile("${projectName}/release.properties"))

    }
    stage('Build'){
      try{
        sh """
          docker pull ${longImageName}:${properties.version} && 
          docker tag ${longImageName}:${properties.version} ${longImageName}:${proeprties.versionNoSnapshot}
        """
        utilities.pushImage(shortImageName, properties.versionNoSnapshot, env.DOCKER_REGISTRY_NIGHTLY, env.ARTIFACTORY_DEPLOYER_USER, env.ARTIFACTORY_DEPLOYER_PASSWORD)
      } finally {
        utilities.rmvDkrImage("${longImageName}:${properties.versionNoSnapshot}")
      }
    }
    stage('Release'){
      try{
        release.prepareForRelease("${projectName}", env.BRANCH)
        release.doRelease("${projectName}", env.BRANCH, properties)

        sh """
           cd ${projectName} &&
           git tag -l | xargs --no-run-if-empty git tag -d &&
           git fetch -t &&
           git checkout ${env.CFSSL_COMMITHASH} &&
           git tag -a ${properties.versionNoSnapshot} -m '${projectName} ${properties.versionNoSnapshot}' &&
           git push --tags
           """
        
        gitHub.release("${properties.versionNoSnapshot}", "${properties.versionNoSnapshot}",
                       "${projectName} ${properties.versionNoSnapshot}", "blackducksoftware/${localProjectName}",
                       'blackduckservbuilder', "${env.BLACKDUCKSERVBUILDER_GITHUB_RELEASE_TOKEN}")

        build job: 'Docker Release', parameters: [[$class: 'StringParameterValue', name: 'VERSION', value: "${properties.versionNoSnapshot}"], [$class: 'BooleanParameterValue', name: 'RCBUILD', value: false]], wait: true

        sh """
           cd ${projectName} &&
           git checkout ${env.BRANCH} &&
           sed -i 's/${properties.versionNoSnapshot}/${properties.bumpedVersion}/' build.properties &&
           git commit -a -m 'Increment version to ${properties.bumpedVersion}' &&
           git push -u origin HEAD:${env.BRANCH}
           """

      } catch(Exception e){
        println "Failing during release steps... cleaning up!"
        release.cleanupFailedRelease("${projectName}", properties)
        throw e
      }
    }
    build job: 'BlackDuck Upload Cache - Push To Docker.io', parameters: [[$class: 'StringParameterValue', name: 'VERSION', value: properties.versionNoSnapshot]], wait: true
  } catch(Exception e){
    echo e.getMessage()
    throw e
  }
}

