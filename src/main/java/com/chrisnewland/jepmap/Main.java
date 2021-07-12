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
import java.util.*;

public class Main
{
	private static final String URL_OPENJDK_ROOT = "https://openjdk.java.net/";

	private static final String URL_JEPS = "https://openjdk.java.net/jeps/";

	private static final String URL_PROJECT = "https://openjdk.java.net/projects/";

	private static final String URL_WIKI = "https://wiki.openjdk.java.net/display/";

	private final Path htmlCachePath = Paths.get("/tmp/jepmap");

	private final JEPMap jepMap = new JEPMap();

	private final Map<String, Project> projectMap = new HashMap<>();

	private final Map<Integer, Set<String>> badMappings = new HashMap<>();

	public static void main(String[] args) throws IOException
	{
		Main main = new Main();

		main.loadBadMappings();

		main.parseJEPs();

		main.parseProjects();

		main.parseProjectsJDK();

		main.associateJEPsToProjects();

		main.report();
	}

	public Main()
	{
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
			throw new RuntimeException("Couldn't load " + badMappingsPath);
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

	private void parseProjectsJDK()
	{
		System.out.println("parseProjectsJDK()");

		int min = 6;

		int max = 18;

		for (int jdk = min; jdk <= max; jdk++)
		{
			String projectName = "JDK" + jdk;

			String projectId = "jdk" + (jdk >= 10 ? "/" : "") + jdk;

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

	private void report() throws IOException
	{
		List<Project> projectList = new ArrayList<>(projectMap.values());

		Collections.sort(projectList, new Comparator<Project>()
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

			Iterator<JEP> iterator = projectJEPs.iterator();

			while (iterator.hasNext())
			{
				JEP jep = iterator.next();

				if (isBapMapping(jep.getNumber(), project.getId()))
				{
					System.out.println("Removing bad mapping " + jep.getNumber() + "=>" + project.getId());
					iterator.remove();
				}
			}

			if (!projectJEPs.isEmpty())
			{
				System.out.println("------------------------------PROJECT: " + project.getName() + " (" + project.getId() + ")");

				builderJump.append("<div class=\"jump\"><a href=\"#")
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

				if (project.getDescription().toLowerCase().contains("wiki"))
				{
					builderProject.append(" (<a href=\"")
								  .append(project.getWikiURL())
								  .append("\">")
								  .append(project.getWikiURL())
								  .append("</a>)");
				}

				builderProject.append("</div>\n");

				List<JEP> jepList = new ArrayList<>(project.getJeps());

				Collections.sort(jepList, new Comparator<JEP>()
				{
					@Override public int compare(JEP j1, JEP j2)
					{
						return Integer.compare(j1.getNumber(), j2.getNumber());
					}
				});

				builderProject.append("<h3>JEPS</h3>\n");

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
					builderProject.append("[Status: ").append(jep.getStatus());
					builderProject.append("] [Updated: ").append(jep.getUpdated().substring(0, 10));
					builderProject.append("</span>");

					builderProject.append("]</div></div>\n");

					System.out.println(jep);
				}

				builderProject.append("</div>\n");

				builderProject.append("</div>\n");
			}
		}

		String template = new String(Files.readAllBytes(Paths.get("src/main/resources/template.html")), StandardCharsets.UTF_8);

		template = template.replace("%JUMP%", builderJump.toString());

		template = template.replace("%BODY%", builderProject.toString());

		Files.write(Paths.get("src/main/resources/jepmap.html"), template.getBytes(StandardCharsets.UTF_8));
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

				document = Jsoup.connect(url).userAgent("JEPMap - https://github.com/chriswhocodes/JEPMap").get();
			}
			catch (Exception e)
			{
				//System.out.println("Writing empty file: " + file.getAbsolutePath());

				Files.write(file.toPath(), new byte[0]);
				throw e;
			}

			//System.out.println("Saving to file: " + file.getAbsolutePath());
			Files.write(file.toPath(), document.outerHtml().getBytes(StandardCharsets.UTF_8));
		}

		return document;
	}

	private void parseProject(Project project, String url, boolean parseDescription) throws IOException
	{
		//System.out.println("parseProject(" + url + ")");

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

				//System.out.println("Got JEP number " + jepNumber + " from " + link);

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
				String value = tdElements.get(1).text();

				System.out.println(key + "=>" + value);

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
					int related = getNumberFromJEPName(value);

					jep.addRelated(related);
				}
				else if (inDepends)
				{
					int depends = getNumberFromJEPName(value);

					jep.addDepends(depends);
				}

				if ("Discussion".equals(key))
				{
					jep.setDiscussion(value);
				}
				else if ("Status".equals(key))
				{
					jep.setStatus(value);
				}
				else if ("Created".equals(key))
				{
					jep.setCreated(value);
				}
				else if ("Updated".equals(key))
				{
					jep.setUpdated(value);
				}
				else if ("Release".equals(key))
				{
					jep.setRelease(value);
				}
			}
		}

		Element markdown = doc.select("div[class=markdown]").first();

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

		return jep;
	}

	private int getNumberFromJEPName(String name)
	{
		name = name.replace(":", "");

		String[] parts = name.split(" ");

		for (String part : parts)
		{
			try
			{
				return Integer.parseInt(part);
			}
			catch (NumberFormatException nfe)
			{
			}
		}

		throw new RuntimeException("Could not parse number from description:" + name);
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

				String projectId = discussion.substring(0, discussion.indexOf('@'));

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
		return url.contains("/projects/") && !"/projects/".equals(url) && !url.contains("/projects/jdk");
	}

	private boolean linkIsJEP(String url)
	{
		return url.contains("/jeps/") && !"/jeps/".equals(url) && !url.endsWith("/jeps/0");
	}
}