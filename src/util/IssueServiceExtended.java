package util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.egit.github.core.IRepositoryIdProvider;
import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.client.GitHubRequest;
import org.eclipse.egit.github.core.client.GitHubResponse;
import org.eclipse.egit.github.core.service.IssueService;

import static org.eclipse.egit.github.core.client.IGitHubConstants.SEGMENT_ISSUES;
import static org.eclipse.egit.github.core.client.IGitHubConstants.SEGMENT_REPOS;

public class IssueServiceExtended extends IssueService{
	private GitHubClientExtended ghClient;
	
	public static final String ISSUE_DATE = "date";
	public static final String ISSUE_CONTENTS = "issue";
	
	public IssueServiceExtended(GitHubClientExtended client){
		super(client);
		this.ghClient = client;
	}
	
	public HashMap<String, Object> getIssueData(IRepositoryIdProvider repository, int issueId) throws IOException{
		HashMap<String, Object> result = new HashMap<String, Object>();
		GitHubResponse response = getIssueResponse(repository.generateId(), issueId + "");
		String dateModified = response.getHeader("Date");
		result.put(ISSUE_DATE, dateModified);
		result.put(ISSUE_CONTENTS, (Issue) response.getBody());
		return result;
	}
	private GitHubResponse getIssueResponse(String repoId, String issueNumber)
			throws IOException {
		if (issueNumber == null)
			throw new IllegalArgumentException("Issue number cannot be null"); //$NON-NLS-1$
		if (issueNumber.length() == 0)
			throw new IllegalArgumentException("Issue number cannot be empty"); //$NON-NLS-1$

		StringBuilder uri = new StringBuilder(SEGMENT_REPOS);
		uri.append('/').append(repoId);
		uri.append(SEGMENT_ISSUES);
		uri.append('/').append(issueNumber);
		GitHubRequest request = createRequest();
		request.setUri(uri);
		request.setType(Issue.class);
		return client.get(request);
	}
	
	public Issue editIssue(IRepositoryIdProvider repository, Issue issue, String dateModified) throws IOException {
		if (issue == null)
			throw new IllegalArgumentException("Issue cannot be null"); //$NON-NLS-1$

		StringBuilder uri = new StringBuilder(SEGMENT_REPOS);
		uri.append('/').append(repository.generateId());
		uri.append(SEGMENT_ISSUES);
		uri.append('/').append(issue.getNumber());
		HttpURLConnection connection = ghClient.createPost(uri.toString());
		connection.setRequestProperty("If-Unmodified-Since", dateModified);
		Map<Object, Object> params = createIssueMap(issue, false);
		String state = issue.getState();
		if (state != null)
			params.put(FILTER_STATE, state);
		return ghClient.sendJson(connection, params, Issue.class);
	}

}
