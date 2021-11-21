/*
 * Copyright (c) 2021 Chris Newland.
 * Licensed under https://github.com/chriswhocodes/JEPMap/blob/master/LICENSE
 */
package com.chrisnewland.jepmap.websocket;

import com.chrisnewland.jepmap.JEP;
import org.json.JSONObject;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class JEPLoader
{
	private static final String SUFFIX = ".json";

	private final List<JEP> jepList;

	public JEPLoader(Path jepDir)
	{
		File[] jepFiles = jepDir.toFile().listFiles(new FilenameFilter()
		{
			@Override public boolean accept(File dir, String name)
			{
				return name.endsWith(SUFFIX);
			}
		});

		jepList = new ArrayList<>();

		try
		{
			for (File jepFile : jepFiles)
			{
				String contents = Files.readString(jepFile.toPath());

				JEP jep = JEP.deserialise(new JSONObject(contents));

				jepList.add(jep);
			}

			jepList.sort(new Comparator<JEP>()
			{
				@Override public int compare(JEP o1, JEP o2)
				{
					return Integer.compare(o1.getNumber(), o2.getNumber());
				}
			});
		}
		catch (IOException ioe)
		{
			ioe.printStackTrace();
		}
	}

	public List<JEP> searchJEPs(String searchLower)
	{
		List<JEP> result = new ArrayList<>();

		for (JEP jep : jepList)
		{
			if (jep.getName().toLowerCase().contains(searchLower) || jep.getBody().toLowerCase().contains(searchLower))
			{
				result.add(jep);
			}
		}

		return result;
	}
}