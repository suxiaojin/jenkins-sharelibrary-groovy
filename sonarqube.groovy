def SonarScan(projectName,projectDesc,projectPath){
	withSonarQubeEnv("sonarqube-test"){
		def scanHome = "/opt/sonar"
		def sonarDate = sh  returnStdout: true, script: 'date  +%Y%m%d%H%M%S'
		sonarDate = sonarDate - "\n"
		echo "${sonarDate}"

		sh """
			${scanHome}/bin/sonar-scanner -Dsonar.projectKey=${projectName} \
			-Dsonar.projectName=${projectName} \
			-Dsonar.projectVersion=${sonarDate} \
			-Dsonar.ws.timeout=30 \
			-Dsonar.projectDescription=${projectDesc} \
			-Dsonar.links.homepage=http://www.baidu.com \
			-Dsonar.sources=${projectPath} \
			-Dsonar.sourceEncoding=UTF-8 \
			-Dsonar.java.binaries=target/classes \
			-Dsonar.java.test.binaries=target/test-classes \
			-Dsonar.java.surefire.report=target/surefire-reports -X

		"""
	}
}