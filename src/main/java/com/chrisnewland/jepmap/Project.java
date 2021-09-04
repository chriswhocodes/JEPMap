/*
 * Copyright (c) 2021 Chris Newland.
 * Licensed under https://github.com/chriswhocodes/JEPMap/blob/master/LICENSE
 */

package com.chrisnewland.jepmap;

import java.util.HashSet;
import java.util.Set;

public class Project
{
	private final Set<JEP> jepSet = new HashSet<>();

	private final String id;

	private final String name;

	private String description;

	private String projectURL;

	private String wikiURL;

	public Project(String id, String name)
	{
		this.id = id;
		this.name = name;
	}

	public void addJEP(JEP jep)
	{
		if (jep == null)
		{
			Thread.dumpStack();
			System.exit(-1);
		}

		if (!jepSet.contains(jep))
		{
			jepSet.add(jep);
			System.out.println("Added " + jep.getNumber() + " to " + name);
		}
	}

	public Set<JEP> getJeps()
	{
		return jepSet;
	}

	public String getName()
	{
		return name;
	}

	public String getId()
	{
		return id;
	}

	public String getProjectURL()
	{
		return projectURL;
	}

	public void setProjectURL(String projectURL)
	{
		this.projectURL = projectURL;
	}

	public String getWikiURL()
	{
		return wikiURL;
	}

	public void setWikiURL(String wikiURL)
	{
		this.wikiURL = wikiURL;
	}

	public String getDescription()
	{
		return description;
	}

	public void setDescription(String description)
	{
		this.description = description;
	}
}