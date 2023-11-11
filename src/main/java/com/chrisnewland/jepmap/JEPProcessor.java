/*
 * Copyright (c) 2021 Chris Newland.
 * Licensed under https://github.com/chriswhocodes/JEPMap/blob/master/LICENSE
 */

package com.chrisnewland.jepmap;

import org.jsoup.*;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class JEPProcessor
{
	private static class JEPComparator implements Comparator<JEP>
	{
		@Override public int compare(JEP j1, JEP j2)
		{
			return Integer.compare(j1.getNumber(), j2.getNumber());
		}
	}

	private static final String URL_OPENJDK_ROOT = "https://openjdk.java.net/";

	public static final String URL_JEPS = "https://openjdk.java.net/jeps/";

	private static final String URL_PROJECT = "https://openjdk.java.net/projects/";

	private static final String URL_WIKI = "https://wiki.openjdk.java.net/display/";

	private final Path htmlCachePath = Paths.get("/tmp/jepmap");

	private final JEPMap jepMap = new JEPMap();

	private final Map<String, Project> projectMap = new HashMap<>();

	private final Map<Integer, Set<String>> badMappings = new HashMap<>();

	private final Path pathOutputJson;

	private final Path pathOutputHtml;

	public static void main(String[] args) throws IOException
	{
		if (args.length != 2)
		{
			System.err.println("JEPProcessor <jsonOutputDir> <htmlOutputDir>");
			System.exit(-1);
		}

		JEPProcessor jepProcessor = new JEPProcessor(args[0], args[1]);

		jepProcessor.loadBadMappings();

		jepProcessor.parseJEPs();

		jepProcessor.parseProjects();

		jepProcessor.parseProjectsJDK();

		jepProcessor.associateJEPsToProjects();

		jepProcessor.cleanBadMappings();

		jepProcessor.report();

		jepProcessor.generateJepSearch();

		jepProcessor.generateFullJep();
	}

	public JEPProcessor(String jsonOutputDir, String htmlOutputDir)
	{
		this.pathOutputJson = Paths.get(jsonOutputDir);

		this.pathOutputHtml = Paths.get(htmlOutputDir);

		if (!Files.exists(htmlCachePath))
		{
			boolean created = htmlCachePath.toFile().mkdirs();

			if (!created)
			{
				throw new RuntimeException("Could not create tmp dir " + htmlCachePath);
			}
		}
	}

	private void loadBadMappings()
	{
		Properties properties = new Properties();

		Path badMappingsPath = Paths.get("src/main/resources/badmappings.properties");

		try
		{
			properties.load(new FileReader(badMappingsPath.toFile()));

			for (String jepNumber : properties.stringPropertyNames())
			{
				String badProjectIds = properties.getProperty(jepNumber);

				String[] idArray = badProjectIds.split(",");

				Set<String> idSet = new HashSet<>(Arrays.asList(idArray));

				System.out.println("JEP " + jepNumber + " must not map to " + Arrays.toString(idArray));

				badMappings.put(Integer.parseInt(jepNumber), idSet);
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException("Couldn't load mappings: " + badMappingsPath);
		}
	}

	private boolean isBapMapping(int jepNumber, String projectId)
	{
		boolean result = false;

		if (badMappings.containsKey(jepNumber))
		{
			Set<String> projectIdSet = badMappings.get(jepNumber);

			if (projectIdSet.contains(projectId) || projectIdSet.contains("*"))
			{
				result = true;
			}
		}

		//System.out.println("isBadMapping: " + jepNumber + "=>" + projectId + " : " + result);

		return result;
	}

	private void parseProjects() throws IOException
	{
		System.out.println("parseProjects()");

		Document doc = loadHTML(URL_OPENJDK_ROOT);

		Element leftSidebar = doc.select("div[id=sidebar]").first();

		Elements hrefElements = leftSidebar.select("a[href]");

		System.out.println("hrefElements:" + hrefElements.size());

		for (Element href : hrefElements)
		{
			String link = href.attr("href");
			String projectName = href.text();

			if (linkIsProject(link))
			{
				//System.out.println(link + "=>" + projectName);

				String projectId = link.substring("/projects/".length()).toLowerCase();

				if (!projectId.isEmpty())
				{
					Project project = new Project(projectId, projectName);

					projectMap.put(projectId, project);

					try
					{
						String urlProject = URL_PROJECT + projectId;
						String urlWiki = URL_WIKI + projectId;

						parseProject(project, urlProject, true);
						project.setProjectURL(urlProject);

						parseProject(project, urlWiki, false);
						project.setWikiURL(urlWiki);
					}
					catch (Exception e)
					{
						System.out.println("Couldn't parse " + projectId);
					}
				}
			}
		}
	}

	private String getProjectIdForJDK(int jdk)
	{
		return "jdk" + (jdk >= 10 ? "/" : "") + jdk;
	}

	private int getJDKMajorVersionFromReleaseVersion(String releaseVersion)
	{
		int lastMajorChar;

		for (lastMajorChar = 0; lastMajorChar < releaseVersion.length(); lastMajorChar++)
		{
			char c = releaseVersion.charAt(lastMajorChar);

			if (!Character.isDigit(c))
			{
				break;
			}
		}

		System.out.println("Substring 0-" + lastMajorChar + " from " + releaseVersion);

		int result = Integer.parseInt(releaseVersion.substring(0, lastMajorChar));

		System.out.println("Got major version " + result + " from " + releaseVersion);

		return result;
	}

	private void parseProjectsJDK()
	{
		System.out.println("parseProjectsJDK()");

		int min = 6;

		int max = 21;

		for (int jdk = min; jdk <= max; jdk++)
		{
			String projectName = "JDK" + jdk;

			String projectId = getProjectIdForJDK(jdk);

			Project project = new Project(projectId, projectName);

			projectMap.put(projectId, project);

			try
			{
				String urlProject = URL_PROJECT + projectId;
				String urlWiki = URL_WIKI + projectId;

				parseProject(project, urlProject, true);
				project.setProjectURL(urlProject);

				parseProject(project, urlWiki, false);
				project.setWikiURL(urlWiki);
			}
			catch (Exception e)
			{
				System.out.println("Couldn't parse " + projectId);
				e.printStackTrace();
			}
		}
	}

	private void cleanBadMappings()
	{
		for (Project project : projectMap.values())
		{
			Set<JEP> projectJEPs = project.getJeps();

			Iterator<JEP> iterator = projectJEPs.iterator();

			System.out.println(project.getId() + " before clean: " + projectJEPs.size());

			while (iterator.hasNext())
			{
				JEP jep = iterator.next();

				if (isBapMapping(jep.getNumber(), project.getId()))
				{
					System.out.println("Removing bad mapping JEP: " + jep.getNumber() + " Project: " + project.getId());
					iterator.remove();

					jep.removeProjectId(project.getId());
				}
			}

			System.out.println(project.getId() + " after clean: " + projectJEPs.size());

		}
	}

	private void report() throws IOException
	{
		List<Project> projectList = new ArrayList<>(projectMap.values());

		projectList.sort(new Comparator<Project>()
		{
			@Override public int compare(Project p1, Project p2)
			{
				String s1 = p1.getName();
				String s2 = p2.getName();

				//sSystem.out.println("comp " + s1 + " v " + s2);

				if (s1.startsWith("JDK") && s2.startsWith("JDK"))
				{
					s1 = s1.substring(3);
					s2 = s2.substring(3);

					return Integer.compare(Integer.parseInt(s1), Integer.parseInt(s2));
				}
				else
				{
					return s1.compareTo(s2);
				}

			}
		});

		StringBuilder builderJump = new StringBuilder();

		StringBuilder builderProject = new StringBuilder();

		for (Project project : projectList)
		{
			Set<JEP> projectJEPs = project.getJeps();

			if (!projectJEPs.isEmpty())
			{
				System.out.println("------------------------------PROJECT: " + project.getName() + " (" + project.getId() + ")");

				builderJump.append("\n<div class=\"jump\"><a href=\"#")
						   .append(project.getId())
						   .append("\">")
						   .append(project.getName())
						   .append("</a></div>");

				builderProject.append("<div class=\"project\" id=\"").append(project.getId()).append("\">\n");
				builderProject.append("<h2><a href=\"")
							  .append(URL_PROJECT)
							  .append(project.getId())
							  .append("\">")
							  .append(project.getName())
							  .append("</a></h2>\n");

				builderProject.append("<div class=\"description\">").append(project.getDescription());

				if (project.getDescription() != null)
				{
					if (project.getDescription().toLowerCase().contains("wiki"))
					{
						builderProject.append(" (<a href=\"")
									  .append(project.getWikiURL())
									  .append("\">")
									  .append(project.getWikiURL())
									  .append("</a>)");
					}
				}
				else
				{
					System.out.println("WARN: missing description on project " + project.getId());
				}

				builderProject.append("</div>\n");

				List<JEP> jepList = new ArrayList<>(project.getJeps());

				jepList.sort(new JEPComparator());

				builderProject.append("<h3>JEPs</h3>\n");

				builderProject.append("<div class=\"jeps\">\n");

				for (JEP jep : jepList)
				{
					builderProject.append("<div class=\"jep\"><a href=\"")
								  .append(URL_JEPS)
								  .append(jep.getNumber())
								  .append("\">JEP ")
								  .append(jep.getNumber())
								  .append(": ")
								  .append(jep.getName())
								  .append("</a><div class=\"jepstatus\">");
					if (jep.getRelease() != null)
					{
						builderProject.append("[Release: ").append(jep.getRelease()).append("] ");
					}
					builderProject.append("[Status: ").append(jep.getStatus());
					builderProject.append("] [Updated: ").append(jep.getUpdated().substring(0, 10));

					builderProject.append("]</div></div>\n");

					System.out.println(jep);
				}

				builderProject.append("</div>\n");

				builderProject.append("</div>\n");
			}
		}

		String template = getResource("templates/jepmap.html");

		template = template.replace("%TOPMENU%", getResource("menu.html"));

		DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_DATE;

		template = template.replace("%UPDATED%", dateTimeFormatter.format(LocalDateTime.now()));

		template = template.replace("%JUMP%", builderJump.toString());

		template = template.replace("%BODY%", builderProject.toString());

		Files.write(pathOutputHtml.resolve("jepmap.html"), template.getBytes(StandardCharsets.UTF_8));
	}

	private void generateJepSearch() throws IOException
	{
		StringBuilder builder = new StringBuilder();

		List<JEP> jepList = new ArrayList<>(jepMap.values());

		jepList.sort(new JEPComparator());

		for (JEP jep : jepList)
		{
			builder.append(jep.toHtmlValueRow()).append("\n");
		}

		String template = getResource("templates/jepsearch.html");

		DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_DATE;

		template = template.replace("%UPDATED%", dateTimeFormatter.format(LocalDateTime.now()));

		template = template.replace("%TOPMENU%", getResource("menu.html"));

		template = template.replace("%BODY%", builder.toString());

		Files.write(pathOutputHtml.resolve("jepsearch.html"), template.getBytes(StandardCharsets.UTF_8));
	}

	private void generateFullJep() throws IOException
	{
		StringBuilder builder = new StringBuilder();

		List<JEP> jepList = new ArrayList<>(jepMap.values());

		jepList.sort(new JEPComparator());

		for (JEP jep : jepList)
		{
			builder.append(jep.toHtmlValueRow()).append("\n");
		}

		String template = getResource("templates/fulljep.html");

		DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_DATE;

		template = template.replace("%UPDATED%", dateTimeFormatter.format(LocalDateTime.now()));

		template = template.replace("%TOPMENU%", getResource("menu.html"));

		template = template.replace("%BODY%", builder.toString());

		Files.write(pathOutputHtml.resolve("fulljep.html"), template.getBytes(StandardCharsets.UTF_8));
	}

	private String getResource(String filename) throws IOException
	{
		return Files.readString(Paths.get("src/main/resources/", filename), StandardCharsets.UTF_8);
	}

	private Document loadHTML(String url) throws IOException
	{
		String filename = url.replace(":", "-").replace("/", "_");

		File file = new File(htmlCachePath.toFile(), filename);

		Document document;

		if (file.exists())
		{
			//System.out.println("Loading from file: " + file.getAbsolutePath());
			document = Jsoup.parse(file, "UTF-8", url);
		}
		else
		{
			//System.out.println("No file: " + file.getAbsolutePath());

			try
			{
				System.out.println("Fetching from network: " + url);

				document = Jsoup.connect(url).userAgent("JEPMap - https://github.com/chriswhocodes/JEPMap").followRedirects(true).get();
			}
			catch (Exception e)
			{
				//System.out.println("Writing empty file: " + file.getAbsolutePath());

				Files.write(file.toPath(), new byte[0]);
				throw e;
			}

			String htmlToSave = document.outerHtml().replace("&#x2009;", " ").replace("&thinsp;", " ");

			//System.out.println("Saving to file: " + file.getAbsolutePath());
			Files.write(file.toPath(), htmlToSave.getBytes(StandardCharsets.UTF_8));
		}

		return document;
	}

	private void parseProject(Project project, String url, boolean parseDescription) throws IOException
	{
		System.out.println("parseProject(" + url + ")");

		Document doc = loadHTML(url);

		if (parseDescription)
		{
			String description = doc.selectFirst("p").text();

			if (description.endsWith(":"))
			{
				String ulBlock = doc.selectFirst("ul").html().replace("<p>", "").replace("</p>", "").replace("<br>", "");

				description += "<ul>" + ulBlock + "</ul>";
			}

			project.setDescription(description);
		}

		Elements hrefElements = doc.select("a[href]");

		for (Element href : hrefElements)
		{
			String link = href.attr("href");

			if (linkIsJEP(link))
			{
				int jepNumber = getNumberFromJEPLink(link);

				System.out.println("Got JEP number " + jepNumber + " from " + link);

				if (jepNumber == 8294992 || jepNumber == 8277163)
				{
					jepNumber = 450;
				}

				JEP jep = jepMap.get(jepNumber);

				project.addJEP(jep);
			}
		}
	}

	private int getNumberFromJEPLink(String link)
	{
		//System.out.println("getNumberFromJEPLink: " + link);

		String[] parts = link.split("/");

		String last = parts[parts.length - 1];

		if (last.indexOf('#') != -1)
		{
			last = last.substring(0, last.indexOf('#'));
		}

		return Integer.parseInt(last);
	}

	private String getProjectIdFromLink(String link)
	{
		System.out.println("getProjectIdFromLink: " + link);

		link = link.replace("%5D", "");

		String[] parts = link.split("/");

		boolean lastPartWasProjects = false;

		String projectId = null;

		for (String part : parts)
		{
			if ("projects".equals(part))
			{
				lastPartWasProjects = true;
				continue;
			}

			if (lastPartWasProjects)
			{
				projectId = part;
				break;
			}
		}

		if (projectId != null && projectId.indexOf('#') != -1)
		{
			projectId = projectId.substring(0, projectId.indexOf('#'));
		}

		System.out.println("getProjectIdFromLink got: " + projectId);

		return projectId;
	}

	private void parseJEPs() throws IOException
	{
		Document documentJEPs = loadHTML(URL_JEPS);

		Elements jepTables = documentJEPs.select("table[class=jeps]");

		for (Element jepTable : jepTables)
		{
			Elements hrefElements = jepTable.select("a[href]");

			for (Element hrefElement : hrefElements)
			{
				String link = hrefElement.attr("href");

				System.out.println("JEP link: " + link);

				try
				{
					int jepNumber = Integer.parseInt(link);

					System.out.println("looking for " + link);

					JEP jep = parseJEP(jepNumber);

					jepMap.put(jepNumber, jep);
				}
				catch (Exception e)
				{
					System.out.println("Couldn't load JEP " + link);

					e.printStackTrace();
					System.exit(-1);
				}
			}
		}
	}

	private JEP parseJEP(int number) throws IOException
	{
		String url = URL_JEPS + number;

		Document doc = loadHTML(url);

		Element h1 = doc.select("h1").first();

		String title = h1.text();

		JEP jep = new JEP(title, number);

		System.out.println("================================ " + jep);

		Element headTable = doc.select("table.head").first();

		Elements trElements = headTable.getElementsByTag("tr");

		boolean inRelated = false;
		boolean inDepends = false;

		for (Element tr : trElements)
		{
			Elements tdElements = tr.getElementsByTag("td");

			if (tdElements.size() == 2)
			{
				String key = tdElements.get(0).text();

				Element valueElementTd = tdElements.get(1);
				String valueText = valueElementTd.text();

				System.out.println(key + "=>" + valueText);

				if ("Relates to".equals(key))
				{
					inRelated = true;
				}
				else if (!key.trim().isEmpty())
				{
					inRelated = false;
				}

				if ("Depends".equals(key))
				{
					inDepends = true;
				}
				else if (!key.trim().isEmpty())
				{
					inDepends = false;
				}

				if (inRelated)
				{
					String hrefJEP = valueElementTd.child(0).attr("href");

					try
					{
						int related = Integer.parseInt(hrefJEP);

						jep.addRelated(related);
					}
					catch (NumberFormatException nfe)
					{
					}
				}
				else if (inDepends)
				{
					String hrefJEP = valueElementTd.child(0).attr("href");

					try
					{
						int depends = Integer.parseInt(hrefJEP);

						jep.addDepends(depends);
					}
					catch (NumberFormatException nfe)
					{
					}
				}

				if ("Discussion".equals(key))
				{
					jep.setDiscussion(valueText);
				}
				else if ("Status".equals(key))
				{
					jep.setStatus(valueText);
				}
				else if ("Created".equals(key))
				{
					jep.setCreated(valueText);
				}
				else if ("Updated".equals(key))
				{
					jep.setUpdated(valueText);
				}
				else if ("Release".equals(key))
				{
					jep.setRelease(valueText);
				}
				else if ("Issue".equals(key))
				{
					jep.setIssue(valueText);
				}
			}
		}

		Element markdown = doc.select("div[class=markdown]").first();

		jep.setBody(markdown.text());

		Elements hrefElements = markdown.select("a[href]");

		for (Element hrefElement : hrefElements)
		{
			String link = hrefElement.attr("href");

			if (linkIsProject(link))
			{
				String projectId = getProjectIdFromLink(link);

				System.out.println("Found project link in JEP:" + projectId);

				jep.addProjectId(projectId);
			}
		}

		Files.write(pathOutputJson.resolve(jep.getNumber() + ".json"), jep.serialise().getBytes(StandardCharsets.UTF_8));

		return jep;
	}

	private void associateJEPsToProjects()
	{
		for (JEP jep : jepMap.values())
		{
			String discussion = jep.getDiscussion();

			//System.out.println("Checking discussion " + discussion + " for " + jep);

			if (discussion != null)
			{
				discussion = discussion.replace(" dash ", "-").replace(" at ", "@").replace(" dot ", ".");

				discussion = discussion.replace("-dev", "");

				int atPos = discussion.indexOf('@');

				if (atPos != -1)
				{
					String projectId = discussion.substring(0, atPos);

					System.out.println(jep + " discussed on " + projectId);

					if (projectMap.containsKey(projectId))
					{
						Project project = projectMap.get(projectId);

						if (project != null)
						{
							project.addJEP(jep);
						}
					}
				}
			}

			String release = jep.getRelease();

			if (release != null && !release.isEmpty() && !"tbd".equals(release))
			{
				int jdkMajorVersion = getJDKMajorVersionFromReleaseVersion(release);

				String projectId = getProjectIdForJDK(jdkMajorVersion);

				System.out.println(jep + " released in " + projectId);

				if (projectMap.containsKey(projectId))
				{
					Project project = projectMap.get(projectId);

					if (project != null)
					{
						project.addJEP(jep);
					}
				}
			}

			Set<String> projectIds = jep.getProjectIds();

			for (String projectId : projectIds)
			{
				System.out.println("Checking projectId " + projectId + " for " + jep);

				if (projectMap.containsKey(projectId))
				{
					Project project = projectMap.get(projectId);

					System.out.println("Found project: " + project.getName());

					if (project != null)
					{
						project.addJEP(jep);
					}
				}
			}
		}
	}

	private boolean linkIsProject(String url)
	{
		if (url.contains("http") && !url.contains("openjdk.java.net"))
		{
			System.out.println("Ignoring non-JDK project URL " + url);
			return false;
		}

		return url.contains("/projects/") && !"/projects/".equals(url) && !url.contains("/projects/jdk");
	}

	private boolean linkIsJEP(String url)
	{
		return url.contains("/jeps/") && !"/jeps/".equals(url) && !url.endsWith("/jeps/0");
	}
}