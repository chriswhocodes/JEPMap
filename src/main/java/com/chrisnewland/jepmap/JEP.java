/*
 * Copyright (c) 2021 Chris Newland.
 * Licensed under https://github.com/chriswhocodes/JEPMap/blob/master/LICENSE
 */

package com.chrisnewland.jepmap;

import java.util.*;

public class JEP
{
	private final String name;
	private final int number;
	private String status;
	private String created;
	private String updated;
	private String release;
	private String discussion;
	private String issue;

	private final Set<Integer> related = new HashSet<>();
	private final Set<Integer> depends = new HashSet<>();
	private final Set<String> projectIds = new HashSet<>();

	public JEP(String name, int number)
	{
		this.number = number;

		String prefix = "JEP " + number + ":";

		this.name = name.replace(prefix, "").replace("JEP XXX:", "");
	}

	public String getName()
	{
		return name;
	}

	public int getNumber()
	{
		return number;
	}

	public void addDepends(int number)
	{
		//System.out.println("JEP " + this.number + " depends on " + number);
		depends.add(number);
	}

	public Set<Integer> getDepends()
	{
		return depends;
	}

	public void addRelated(int number)
	{
		//System.out.println("JEP " + this.number + " related to " + number);
		related.add(number);
	}

	public Set<Integer> getRelated()
	{
		return related;
	}

	public String getStatus()
	{
		return status;
	}

	public void setStatus(String status)
	{
		this.status = status;
	}

	public String getCreated()
	{
		return created;
	}

	public void setCreated(String created)
	{
		this.created = created;
	}

	public String getUpdated()
	{
		return updated;
	}

	public void setUpdated(String updated)
	{
		this.updated = updated;
	}

	public String getRelease()
	{
		return release;
	}

	public void setRelease(String release)
	{
		this.release = release;
	}

	public String getDiscussion()
	{
		return discussion;
	}

	public void setDiscussion(String discussion)
	{
		this.discussion = discussion;
	}

	public void addProjectId(String projectId)
	{
		projectIds.add(projectId);
	}

	public void removeProjectId(String projectId)
	{
		projectIds.remove(projectId);
	}

	public void setIssue(String issue)
	{
		this.issue = issue;
	}

	public String getIssue()
	{
		return issue;
	}

	public Set<String> getProjectIds()
	{
		return projectIds;
	}

	@Override public String toString()
	{
		return number + " => " + name + " (status: " + status + ")";
	}

	@Override public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		JEP jep = (JEP) o;
		return number == jep.number && name.equals(jep.name);
	}

	@Override public int hashCode()
	{
		return Objects.hash(name, number);
	}

	public String toHtmlValueRow()
	{
		String bugLink = "";

		if (issue != null)
		{
			bugLink = makeLink("https://bugs.openjdk.java.net/browse/JDK-" + issue, issue);
		}

		StringBuilder builder = new StringBuilder();

		builder.append("<tr id=\"").append(number).append("\">");
		builder.append("<td>").append(makeJEPLink(number, number)).append("</td>");
		builder.append("<td>").append(makeJEPLink(number, getValueOrEmpty(name))).append("</td>");
		builder.append("<td>").append(bugLink).append("</td>");
		builder.append("<td>").append(getValueOrEmpty(status)).append("</td>");
		builder.append("<td>").append(getValueOrEmpty(created).substring(0, 10)).append("</td>");
		builder.append("<td>").append(getValueOrEmpty(updated).substring(0, 10)).append("</td>");
		builder.append("<td>").append(getValueOrEmpty(release)).append("</td>");
		builder.append("<td>").append(getSafeEmail(getValueOrEmpty(discussion))).append("</td>");

		builder.append("<td>").append(setToString(related, "#")).append("</td>");
		builder.append("<td>").append(setToString(depends, "#")).append("</td>");
		builder.append("<td>").append(setToString(projectIds, "jepmap.html#")).append("</td>");

		builder.append("</tr>");
		return builder.toString();
	}

	private String makeLink(String url, String text)
	{
		return "<a href=\"" + url + "\">" + text + "</a>";
	}

	private String makeJEPLink(int number, Object text)
	{
		return makeLink(Main.URL_JEPS + number, text.toString());
	}

	private String getSafeEmail(String listName)
	{
		int atPos = listName.indexOf(" at ");

		if (atPos != -1)
		{
			listName = listName.substring(0, atPos).replace("dash", "-").replace(" ", "");
		}

		return listName;
	}

	private String getValueOrEmpty(String str)
	{
		return (str == null) ? "" : str;
	}

	private String setToString(Set<?> set, String linkPrefix)
	{
		StringBuilder builder = new StringBuilder();

		for (Object obj : set)
		{
			String link = makeLink(linkPrefix + obj, obj.toString());

			builder.append(link).append(", ");
		}

		if (builder.length() > 2)
		{
			builder.delete(builder.length() - 2, builder.length() - 1);
		}

		return builder.toString();
	}
}