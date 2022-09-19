def call(Map pipelineParams) {
    pipeline {
        agent { label 'build-server' }
        options {
            buildDiscarder(logRotator(numToKeepStr: '30', daysToKeepStr: '30'))
            timeout(time: 30, unit: 'MINUTES')
            disableConcurrentBuilds()
            timestamps()
        }
        stages {
            stage('Initialization') {
                steps {
                    script {
                        env.imageName = pipelineParams.imageName
                        env.tagId = pipelineParams.version?.trim() ? pipelineParams.version : "${BUILD_ID}"
                        if (pipelineParams.gradleTool == null)
                            pipelineParams.gradleTool = 'gradle-default'
                    }
                }
            }
            stage('CI') {
                agent { label 'build-server' }
                tools {
                    gradle "${pipelineParams.gradleTool}"
                }
                when {
                    expression { 
                        pipelineParams.skipBuild != true
                    } 
                }
                steps {
                    script {
                        def IMAGENAME = "${env.imageName}"
                        def VERSION = env.tagId
                        switch (pipelineParams.build) {
                            case 'dotnetcoreMicroServiceRelease':
                            dotnetcoreMicroServiceRelease{
                                repoUrl = pipelineParams.repoUrl
                                credentialsId = pipelineParams.credentialsId
                                imageName = IMAGENAME
                                branches = pipelineParams.branch
                                commit = pipelineParams.commit
                                tagId = VERSION
                                workspace = pwd()
                                publishImageTag = pipelineParams.publishImageTag
                                testImageTag = pipelineParams.testImageTag
                            }
                            break
                            case 'javaMicroServiceRelease':
                            javaMicroServiceRelease{
                                repoUrl = pipelineParams.repoUrl
                                credentialsId = pipelineParams.credentialsId
                                imageName = IMAGENAME
                                branches = pipelineParams.branch
                                commit = pipelineParams.commit
                                tagId = VERSION
                                workspace = pwd()
                                gradleImage = pipelineParams.gradleImage
                                gradleBuildCommand = pipelineParams.gradleBuildCommand?.trim() ? pipelineParams.gradleBuildCommand.trim() : 'clean assemble'
                                gradleTestCommand = pipelineParams.gradleTestCommand?.trim() ? pipelineParams.gradleTestCommand.trim() : 'check'
                                gradleImageCommand = pipelineParams.gradleImageCommand?.trim() ? pipelineParams.gradleImageCommand.trim() : 'unpack docker dockerTag'
                            }
                            break
                            case 'nodeMicroServiceRelease':
                            nodeMicroServiceRelease{
                                repoUrl = pipelineParams.repoUrl
                                credentialsId = pipelineParams.credentialsId
                                imageName = IMAGENAME
                                branches = pipelineParams.branch
                                commit = pipelineParams.commit
                                tagId = VERSION
                                workspace = pwd()
                                buildEnvironments = pipelineParams.buildEnvironments
                                context = pipelineParams.context
                            }
                            break
							case 'nodeMicroServiceRelease2':
                            nodeMicroServiceRelease2{
                                repoUrl = pipelineParams.repoUrl
                                credentialsId = pipelineParams.credentialsId
                                imageName = IMAGENAME
                                branches = pipelineParams.branch
                                commit = pipelineParams.commit
                                tagId = VERSION
                                workspace = pwd()
                                buildEnvironments = pipelineParams.buildEnvironments
                                context = pipelineParams.context
                            }
                            break
                            case 'staticMicroServiceRelease':
                            staticServiceRelease{
                                repoUrl = pipelineParams.repoUrl
                                credentialsId = pipelineParams.credentialsId
                                imageName = IMAGENAME
                                branches = pipelineParams.branch
                                commit = pipelineParams.commit
                                tagId = VERSION
                                workspace = pwd()
                                buildEnvironments = pipelineParams.buildEnvironments
                                context = pipelineParams.context
                            }
                            break
                            case 'hugoMicroServiceRelease':
                            hugoServiceRelease{
                                repoUrl = pipelineParams.repoUrl
                                credentialsId = pipelineParams.credentialsId
                                imageName = IMAGENAME
                                branches = pipelineParams.branch
                                commit = pipelineParams.commit
                                tagId = VERSION
                                workspace = pwd()
                                buildEnvironments = pipelineParams.buildEnvironments
                                context = pipelineParams.context
                            }
                            break
                            default:
                            throw new Exception(sprintf('unsupported release type %1$s.', [pipelineParams.build]))
                            break
                        }
                    }
                    script {
                        def registry = pipelineParams.registry?.trim() ? pipelineParams.registry.trim() : "registry.i-counting.cn" 
                        dockerImageDeploy(
                            imageName: env.imageName,
                            tagId: env.tagId,
                            registry: registry
                        )
                        currentBuild.description = "${registry}/${env.imageName}:${env.tagId}"
                   }
                   script {
                       if (pipelineParams.commit != null \
                            && pipelineParams.commit != "null" && pipelineParams.commit != '') {
                            sshagent([pipelineParams.credentialsId]) {
                                def RELEASE_VERSION = env.tagId.startsWith('r') ? env.tagId : "r${env.tagId}"
                                sh """ 
                                    git tag -fa \"${RELEASE_VERSION}\" -m \"Tag as release version ${RELEASE_VERSION}\"
                                    git push origin HEAD:${pipelineParams.branch}
                                    git push -f origin refs/tags/${RELEASE_VERSION}:refs/tags/${RELEASE_VERSION}
                                    """
                            }
                        } else {
                            echo "Ingore scm tag due to it's not a release build."
                        }
                   }
                }
            }
            stage('Update charts') {
                when {
                    expression { 
                        currentBuild.currentResult == 'SUCCESS' 
                    } 
                }
                steps {
                    script {
                        if (pipelineParams.helm != null){
                            sshagent([pipelineParams.helm.charts.credentialsId]) {
                                gitCheckout {
                                    repoUrl = pipelineParams.helm.charts.repoUrl
                                    credentialsId = pipelineParams.helm.charts.credentialsId
                                    branches = pipelineParams.helm.charts.branch
                                    changelog = false
                                }
                                def chart = pipelineParams.helm.chart.substring("charts/".length())
                                if (pipelineParams.helm.tag?.trim()) {
                                    echo "[Deprecated]Updating the component version via given tag: " + pipelineParams.helm.tag
                                    sh """ 
                                        sed -i '/${pipelineParams.helm.tag}:/c${pipelineParams.helm.tag}: ${env.tagId}' ${chart}/values.yaml
                                    """
                                } else {
                                    echo "Updating the component version via image name: " + env.imageName
                                    def escapedImageName = env.imageName.replace('/', '\\/')
                                    sh """ 
                                        sed -i '/${escapedImageName}/c\\ \\ name: ${escapedImageName}:${env.tagId}' ${chart}/values.yaml
                                    """
                                }
                                sh """
                                    changed=`git diff --quiet --exit-code ${chart}/values.yaml 2>/dev/null || echo \$?`
                                    if [ -z \$changed ];then
                                        echo "No release version won't be updated in values.yml."
                                    elif [ \$changed -eq 1 ];then
                                        git add ${chart}/values.yaml
                                        git commit --no-verify -m "[${pipelineParams.helm.releaseName}] Update image to '${pipelineParams.registry}/${pipelineParams.imageName}:${env.tagId}'."
                                        git fetch && git rebase origin/${pipelineParams.helm.charts.branch}
                                        git push origin HEAD:${pipelineParams.helm.charts.branch}
                                    else
                                        echo "Something wrong happened when checking if the release version updated or not."
                                    fi
                                """
                            }
                        }
                    }
                }
            }
            stage('Start') {
                agent { label pipelineParams.helm ? "${pipelineParams.helm.node}" : "swarm-master" }
                when {
                    expression { 
                        pipelineParams.runner != null || pipelineParams.helm.isStart != false
                    } 
                }
                steps {
                    script {
                        if (pipelineParams.runner) {
                            def IMAGENAME = env.imageName
                            def VERSION = env.tagId
                            def LOGDRIVER = pipelineParams.logDriver?.trim() ? pipelineParams.logDriver : ''
                            def runnerJob = pipelineParams.runner?.trim() ? pipelineParams.runner : 'MicroserviceRunner'
                            build job: runnerJob, parameters: [string(name: 'SERVICE', value: pipelineParams.serviceName), string(name: 'VERSION', value: VERSION), string(name: 'LOGDRIVER', value: LOGDRIVER)]
                        }
                        else if (pipelineParams.helm) {
                            if (pipelineParams.helm.isStart != false) {
                                helmInitialize(
                                    pipelineParams.helm
                                )
                                pipelineParams.helm.setOptions["image.tag"] = env.tagId
                                helmRelease(
                                    pipelineParams.helm
                                )

                                if (currentBuild.description?.trim())
                                    currentBuild.description += "\nSuccessfully deployed '${pipelineParams.helm.releaseName}' to ns '${pipelineParams.helm.namespace}'."
                                else
                                    currentBuild.description = "Deployed '${pipelineParams.helm.releaseName}' to ns '${pipelineParams.helm.namespace}' with image ${pipelineParams.registry}/${pipelineParams.imageName}:${env.tagId}"
                            }
                        }
                        else
                            throw new Exception("Missing required deployment method, either runner or helm!")
                    }
                }
            }
        }
        post {
            always {
                script {
                    if (currentBuild.resultIsWorseOrEqualTo('UNSTABLE')) {
                        emailext attachLog: true, body: '$DEFAULT_CONTENT', 
                            postsendScript: '$DEFAULT_POSTSEND_SCRIPT', 
                            presendScript: '$DEFAULT_PRESEND_SCRIPT', 
                            recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']], 
                                replyTo: '$DEFAULT_REPLYTO', subject: '$DEFAULT_SUBJECT', to: '$DEFAULT_RECIPIENTS'
                    }

                    if (pipelineParams.dingding != null) {
                        def buildNumber = pipelineParams.version?.trim() ? pipelineParams.version : "${BUILD_ID}"
                        def changeSets = ""
                        for (changeSet in currentBuild.changeSets) {
                            def entries = changeSet.items
                            for (int j = 0; j < entries.length; j++) {
                                def entry = entries[j]
                                changeSets += "- " + entry.msg + " by " + entry.author + "\n"
                            }
                        }
                        
                        pipelineParams.dingding.msgtype = "markdown"
                        pipelineParams.dingding.title = "${JOB_BASE_NAME} #${buildNumber} 发布完成，状态是${currentBuild.currentResult}"
                        pipelineParams.dingding.message = "  # ${JOB_BASE_NAME} #${buildNumber} 发布完成  \\n  状态: ${currentBuild.currentResult} \\n\\n   分支 : ${pipelineParams.branch}  \\n #### 更新日志:  \\n > ${changeSets}"
                        
                        retry(5) {
                            dingTalkNotify(pipelineParams.dingding)
                        }
                    }
                }
            }
        }
    }
}