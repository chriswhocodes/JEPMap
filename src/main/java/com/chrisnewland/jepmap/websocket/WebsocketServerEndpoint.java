/*
 * Copyright (c) 2021 Chris Newland.
 * Licensed under https://github.com/chriswhocodes/JEPMap/blob/master/LICENSE
 */
package com.chrisnewland.jepmap.websocket;

import com.chrisnewland.jepmap.JEP;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint(value = "/fulltext") public class WebsocketServerEndpoint
{
	@OnOpen public void onWebSocketConnect(Session session)
	{
	}

	@OnMessage public void onWebSocketText(Session session, String search) throws IOException
	{
		if (search.length() >= 3)
		{
			search = search.toLowerCase();

			long start = System.currentTimeMillis();
			List<JEP> jeps = FullJEPServer.getJEPLoader().searchJEPs(search.trim());
			long stop = System.currentTimeMillis();

			System.out.println(search + " in " + (stop - start) + "ms found " + jeps.size() + " results");

			JSONArray result = new JSONArray();

			int context = 80;

			if (!jeps.isEmpty())
			{
				forlabel:
				for (JEP jep : jeps)
				{
					try
					{
						String body = jep.getBody();

						String bodyLower = body.toLowerCase();

						StringBuilder builder = new StringBuilder();

						int pos = bodyLower.indexOf(search);

						builder.append("<ul>");

						do
						{
							String snippet = body.substring(Math.max(0, pos - context), Math.min(pos + context, body.length()));

							int firstSpace = snippet.indexOf(' ');
							int lastSpace = snippet.lastIndexOf(' ');

							if (firstSpace == -1 || lastSpace == -1 || firstSpace == lastSpace)
							{
								continue forlabel;
							}

							builder.append("<li>...").append(snippet.substring(firstSpace + 1, lastSpace)).append("...</li>");

							pos = bodyLower.indexOf(search, pos + context);

						} while (pos != -1);

						builder.append("</ul>");

						JSONObject jsonObject = new JSONObject();

						jsonObject.put("number", jep.getNumber());
						jsonObject.put("name", jep.getName());
						jsonObject.put("snippet", builder);

						result.put(jsonObject);
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
				}
			}

			session.getBasicRemote().sendText(result.toString());
		}
	}

	@OnClose public void onWebSocketClose(CloseReason reason)
	{
		//System.out.println("Socket Closed: " + reason);
	}

	@OnError public void onWebSocketError(Throwable cause)
	{
		if (cause.getMessage() != null)
		{
			System.out.println("onWebSocketError: " + cause.getMessage());
		}
	}
}