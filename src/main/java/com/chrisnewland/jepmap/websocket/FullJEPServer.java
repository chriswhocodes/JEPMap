/*
 * Copyright (c) 2021 Chris Newland.
 * Licensed under https://github.com/chriswhocodes/JEPMap/blob/master/LICENSE
 */
package com.chrisnewland.jepmap.websocket;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.javax.server.config.JavaxWebSocketServletContainerInitializer;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FullJEPServer
{
	public static void main(String[] args)
	{
		Path jepDir = Paths.get(args[0]);

		new FullJEPServer(jepDir);
	}

	private static JEPLoader jepLoader;

	public static JEPLoader getJEPLoader()
	{
		return jepLoader;
	}

	public FullJEPServer(Path jepDir)
	{
		jepLoader = new JEPLoader(jepDir);

		// https://www.eclipse.org/jetty/documentation/jetty-10/programming_guide.php

		Server server = new Server(new InetSocketAddress("127.0.0.1", 8080));

		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);

		context.setContextPath("/");

		server.setHandler(context);

		JavaxWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
			wsContainer.setDefaultMaxTextMessageBufferSize(256);
			wsContainer.setDefaultMaxBinaryMessageBufferSize(256);
			wsContainer.setDefaultMaxSessionIdleTimeout(120_000);
			wsContainer.addEndpoint(WebsocketServerEndpoint.class);
		});

		try
		{
			server.start();

			server.join();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}