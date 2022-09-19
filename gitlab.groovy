def HttpReq(reqType,reqUrl,reqBody){
	def gitServer = "http://192.168.175.138/api/v4"
	withCredentials([string(credentialsId: 'gitlab-admin', variable: 'gitlabToken')]) {
		result = httpRequest customHeaders: [[maskValue: true, name: 'PRIVATE-TOKEN', value: "${gitlabToken}"]],
					httpMode: reqType,
					contentType: "APPLICATION_JSON",
					consoleLogResponseBody: true,
                	ignoreSslErrors: true, 
                	requestBody: reqBody,
                	url: "${gitServer}/${reqUrl}"
    }
    return result
}

def GetProjectID(repoName='',projectName){
	projectApi= "projects?search=${projectName}"
	response = HttpReq('GET',projectApi,'')
	def result = readJSON text: """${response.content}"""
	for  (repo in result){
		if (repo['path'] == "${projectName}"){
			repoId = repo['id']
			print(repoId)
		}
	}
	return repoId
}

def CreateBranch(projectId,refBranch,newBranch){
	try{
		branchApi = "projects/${projectId}/repository/branches?branch=${newBranch}&ref=${refBranch}"
		response = HttpReq("POST",branchApi,'').content
		branchInfo = readJSON text: """${response}"""
	} catch(e){
		print(e)
	}
}