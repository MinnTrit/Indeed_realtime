import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import com.microsoft.playwright.Page.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.time.format.DateTimeFormatter;
import com.microsoft.playwright.PlaywrightException;
import java.lang.StringBuilder;
import java.io.FileReader;
import java.io.BufferedReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;

public class Scrapper {
	private Browser browser;
	private Playwright playwright;
	private Page page;
	private String searchKeyword;
	private int jobs1Page = 15;
	private List<String> jobsList;
	private List<String> companiesList;
	private List<String> salariesList;
	private List<String> locationsList;
	private List<String> deadlinesList;
	private List<String> jobDetailsList;
	public Scrapper(boolean headlessOption, String searchKeyword) {
		initialize(headlessOption, searchKeyword);
		this.searchKeyword = searchKeyword;
	}
	
	private void initialize(boolean headlessOption, String searchKeyword) {
		System.out.println("Start connecting to the browser");
		List<Cookie> cookies = new ArrayList<>();
		try {
			StringBuilder stringBuilder = new StringBuilder();
			FileReader reader = new FileReader("path_to_indeed_cookies");
			BufferedReader br = new BufferedReader(reader);
			String line;
			while ((line = br.readLine()) != null) {
				stringBuilder.append(line);
			}
			br.close();
			String cookiesString = stringBuilder.toString();
			ObjectMapper mapper = new ObjectMapper();
			JsonNode rootNode = mapper.readTree(cookiesString);
			for (JsonNode jsonObj : rootNode) {
				((ObjectNode) jsonObj).put("sameSite", "NONE");
				if (jsonObj.has("expirationDate")) {
					String name = jsonObj.get("name").asText();
					String value = jsonObj.get("value").asText();
					String path = jsonObj.get("path").asText();
					double expires = jsonObj.get("expirationDate").asDouble();
					boolean httpOnly = jsonObj.get("httpOnly").asBoolean();
					boolean secure = jsonObj.get("secure").asBoolean();
					String siteString = jsonObj.get("sameSite").asText();
					SameSiteAttribute sameSite = SameSiteAttribute.valueOf(siteString);
					String domain = jsonObj.get("domain").asText();
					Cookie cookie = new Cookie(name, value)
										.setDomain(domain)
										.setExpires(expires)
										.setHttpOnly(httpOnly)
										.setPath(path)
										.setSecure(secure)
										.setSameSite(sameSite);
					cookies.add(cookie);
				}
				else {
					String name = jsonObj.get("name").asText();
					String value = jsonObj.get("value").asText();
					String path = jsonObj.get("path").asText();
					boolean httpOnly = jsonObj.get("httpOnly").asBoolean();
					boolean secure = jsonObj.get("secure").asBoolean();
					String siteString = jsonObj.get("sameSite").asText();
					SameSiteAttribute sameSite = SameSiteAttribute.valueOf(siteString);
					String domain = jsonObj.get("domain").asText();
					Cookie cookie = new Cookie(name, value)
										.setDomain(domain)
										.setHttpOnly(httpOnly)
										.setPath(path)
										.setSecure(secure)
										.setSameSite(sameSite);
					cookies.add(cookie);
				}
			}
		}
		catch (Exception e) {
			System.out.println("Error occured as "+e.getMessage());
		}
		this.playwright = Playwright.create();
		BrowserType browserType = playwright.chromium();
		this.browser = browserType.launch(new BrowserType.LaunchOptions().setHeadless(headlessOption));
		Browser.NewContextOptions contextOptions = new Browser.NewContextOptions();
		contextOptions.setViewportSize(1366, 768);
		BrowserContext context = browser.newContext(contextOptions);
		context.addCookies(cookies);
		this.page = context.newPage();
		String navigateUrl = "https://jobs.vn.indeed.com";
		page.navigate(navigateUrl);
		fillSearchKeyWord(searchKeyword);
		int maxRetries = 2;
		int currentRetry = 0;
		while (currentRetry < maxRetries) {
			try {
				page.waitForLoadState(LoadState.LOAD);
				System.out.println("The page has been fully loaded");
				break;
			}
			catch (PlaywrightException e) {
				System.out.println("Error occuring while waiting the page to load");
				currentRetry += 1;
			}
		};
	};
	
	public void endPlaywright() {
		browser.close();
		page.close();
		playwright.close();
	};
	
	public List<Map<String, String>> toScrape(int totalPage) {
		System.out.println("Start scrapping the page with the total of "+totalPage+" page");
		List<Map<String, String>> resultList = new ArrayList<>();
		int initialPage = 0;
		while (initialPage < totalPage) {
			checkActivate();
			page.evaluate("window.scrollTo(0,document.body.scrollHeight)");
			LocalDateTime currentDatetime = LocalDateTime.now();
			DateTimeFormatter datetimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
			String datetimeString = currentDatetime.format(datetimeFormatter);
			this.jobsList = getJobTitles();
			this.companiesList = getCompanies();
			this.salariesList = getSalaries();
			this.locationsList = getLocations();
			this.deadlinesList = getDeadlines();
			this.jobDetailsList = getJobsDetails();
			while (true) {
				boolean checkResult = checkLength();
				if (checkResult = true) {
					break;
				}
				else {
					System.out.println("Start getting the length again");
					continue;
				}
			}
			for (int index = 0; index < jobsList.size(); index++) {
				Map<String, String> detailMap = new HashMap<>();
				String job = jobsList.get(index);
				String company = companiesList.get(index);
				String salary = salariesList.get(index);
				String location = locationsList.get(index);
				String deadline = deadlinesList.get(index);
				String jobDetail = jobDetailsList.get(index);
				detailMap.put("SearchKeyWord", searchKeyword);
				detailMap.put("Job", job);
				detailMap.put("Company", company);
				detailMap.put("Salary", salary);
				detailMap.put("Location", location);
				detailMap.put("Deadline", deadline);
				detailMap.put("JobDetails", jobDetail);
				detailMap.put("Created", datetimeString);
				resultList.add(detailMap);
		}
			checkContinue();
			initialPage+=1;
		}
		return resultList;
	}
	
	private boolean checkLength() {
		ElementHandle nextButton = page.querySelector("a[data-testid='pagination-page-next']");
		if (nextButton == null) {
			return true;
		}
		int checkValidation = 6;
		int currentCheck = 0;
		while (currentCheck < checkValidation) {
			//Check for the jobList
			if (jobsList.size() < jobs1Page) {
				this.jobsList = getJobTitles();
				System.out.println("Attempt getting the total jobs again");
			}
			else {
				currentCheck += 1;
			}
			
			//Check for the companyList
			if (companiesList.size() < jobs1Page) {
				this.companiesList = getCompanies();
				System.out.println("Attempt getting the total companies again");
			}
			else {
				currentCheck += 1;
			}
			
			//Check for the salaryList
			if (salariesList.size() < jobs1Page) {
				this.jobsList = getSalaries();
				System.out.println("Attempt getting the total salaries again");
			}
			else {
				currentCheck += 1;
			}
			
			//Check for the locationList
			if (locationsList.size() < jobs1Page) {
				this.companiesList = getLocations();
				System.out.println("Attempt getting the total locations again");
			}
			else {
				currentCheck+=1;
			}
			
			//Check for the deadlineList
			if (deadlinesList.size() < jobs1Page) {
				this.jobsList = getDeadlines();
				System.out.println("Attempt getting the total deadlines again");
			}
			else {
				currentCheck+=1;
			}
			
			//Check for the jobDetailsList
			if (jobDetailsList.size() < jobs1Page) {
				this.companiesList = getJobsDetails();
				System.out.println("Attempt getting the total job details again");
			}
			else {
				currentCheck+=1;
			}
			
		}
		if (currentCheck == checkValidation) {
			return true;
		}
		else {
			return false;
		}
	};
	
	private void fillSearchKeyWord(String searchKeyword) {
		page.waitForLoadState(LoadState.LOAD);
		page.waitForSelector("div.css-1ei70bn.e1ttgm5y0 input");
		ElementHandle inputBar = page.querySelector("div.css-1ei70bn.e1ttgm5y0 input");
		if (inputBar != null) {
			inputBar.fill(searchKeyword);
			page.keyboard().press("Enter");
			System.out.println("Enter the search keyword");
		}
		else {
			System.out.println("Failed searching the keywords");
		}
	};
	
	private List<String> getJobTitles() {
		page.waitForSelector("ul.css-zu9cdh.eu4oa1w0");
		List<String> resultList = new ArrayList<>();
		List<ElementHandle> jobDivs = page.querySelector("ul.css-zu9cdh.eu4oa1w0").querySelectorAll("div.css-dekpa.e37uo190");
		for (ElementHandle jobDiv : jobDivs) {
			String jobTitle = jobDiv.querySelector("h2 a span").textContent();
			resultList.add(jobTitle);
		}
		return resultList;
	}
	
	private List<String> getSalaries() {
		page.waitForSelector("ul.css-zu9cdh.eu4oa1w0");
		List<String> resultList = new ArrayList<>();
		List<ElementHandle> jobDivs = page.querySelector("ul.css-zu9cdh.eu4oa1w0")
				.querySelectorAll("div.jobMetaDataGroup.css-pj786l.eu4oa1w0 div");
		for (ElementHandle jobDiv : jobDivs) {
			ElementHandle salaryDiv = jobDiv.querySelector("div[data-testid='attribute_snippet_testid']");
			if (salaryDiv != null) {
				String salaryString = salaryDiv.textContent();
				resultList.add(salaryString);
			}
			else {
				resultList.add("Not disclosed");
			}
		}
		return resultList;
	};
	
	private List<String> getDeadlines() {
		List<String> resultList = new ArrayList<>();
		page.waitForSelector("ul.css-zu9cdh.eu4oa1w0");
		List<ElementHandle> spanElements = page.querySelector("ul.css-zu9cdh.eu4oa1w0")
				.querySelectorAll("span[data-testid='myJobsStateDate'],"
						+ "span[data-testid='myJobsState']");
	    for (ElementHandle spanElement : spanElements) {
	        String deadline = (String) page.evaluate("(span) => { " +
	            "let textContent = ''; " +
	            "span.childNodes.forEach(node => { " +
	            "  if (node.nodeType === Node.TEXT_NODE) { " +
	            "    textContent += node.textContent; " +
	            "  } " +
	            "}); " +
	            "return textContent.trim(); " +
	            "}", spanElement);
	        resultList.add(deadline);
	    }
	    return resultList;
	};
	
	private List<String> getLocations() {
		page.waitForSelector("ul.css-zu9cdh.eu4oa1w0");
		List<String> resultList = new ArrayList<>();
		List<ElementHandle> jobDivs = page.querySelector("ul.css-zu9cdh.eu4oa1w0")
				.querySelectorAll("div.company_location.css-17fky0v.e37uo190");
		for (ElementHandle jobDiv : jobDivs) {
			ElementHandle locationDiv = jobDiv.querySelector("div").querySelectorAll("div").get(1);
			String location = locationDiv.textContent();
			resultList.add(location);
		}
		return resultList;
	}
	
	private void checkContinue() {
		try {
			WaitForSelectorOptions waitingOptions = new WaitForSelectorOptions();
			waitingOptions.setTimeout(2000);
			page.waitForSelector("a[data-testid='pagination-page-next']", waitingOptions);
			ElementHandle nextButton = page.querySelector("a[data-testid='pagination-page-next']");
			if (nextButton != null) {
				nextButton.click();
				System.out.println("Clicked the next button");
			}
		}
		catch (Exception e) {
			System.out.println("There's no next button to press");
		}
	}
	
	private List<String> getJobsDetails() {
		page.waitForSelector("div.css-9446fg.eu4oa1w0");
		List<String> resultList = new ArrayList<>();
		List<ElementHandle> divElements = page.querySelectorAll("div.css-9446fg.eu4oa1w0");
		if (divElements != null) {
			for (ElementHandle divElement : divElements) {
				try {
					String jobText = divElement.querySelector("ul li").textContent();
					resultList.add(jobText);	
				}
				catch (Exception e) {
					String jobText = divElement.textContent();
					resultList.add(jobText);
				}
			}
		}
		return resultList;
	};
	
	private List<String> getCompanies() {
		page.waitForSelector("ul.css-zu9cdh.eu4oa1w0");
		List<String> resultList = new ArrayList<>();
		List<ElementHandle> jobDivs = page.querySelector("ul.css-zu9cdh.eu4oa1w0") 
				.querySelectorAll("div.company_location");
		for (ElementHandle jobDiv : jobDivs) {
			String company = jobDiv.querySelector("div div span").textContent();
			resultList.add(company);
		}
		return resultList;
	};
	
	private void checkActivate() {
		page.waitForLoadState(LoadState.LOAD);
		WaitForSelectorOptions waitingOptions = new WaitForSelectorOptions();
		waitingOptions.setTimeout(2000);
		try {
			page.waitForSelector("div.DesktopSERPJobAlertPopup-activateButtonContainer", waitingOptions);
			ElementHandle closeButton = page.querySelector("div button.css-yi9ndv.e8ju0x51");
			if (closeButton != null) {
				closeButton.click();
				System.out.println("Clicked the activate confirmations and button");
			}	
		}
		catch (Exception e) {
			
		}
	}
	
}
