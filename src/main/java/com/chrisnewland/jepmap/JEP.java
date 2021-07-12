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

	private final Set<Integer> related = new HashSet<>();
	private final Set<Integer> depends = new HashSet<>();

	public JEP(String name, int number)
	{
		this.number = number;

		String prefix = "JEP " + number + ":";

		this.name = name.substring(prefix.length())
						.trim();
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

	@Override
	public String toString()
	{
		return number + " => " + name + " (" + status + ")";
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		JEP jep = (JEP) o;
		return number == jep.number && name.equals(jep.name);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, number);
	}
}