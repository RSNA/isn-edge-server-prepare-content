 package org.rsna.isn.prepcontent;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hello world!
 *
 */
public class App
{
	private static final Logger logger = Logger.getLogger(App.class.getName());

	public static void main(String[] args) throws Exception
	{
		Monitor monitor = new Monitor();
		Runtime.getRuntime().addShutdownHook(new ShutdownHook(monitor));


		logger.info("Attempting to start job monitor");
		monitor.start();
	}

	private static class ShutdownHook extends Thread
	{
		private final Monitor monitor;

		public ShutdownHook(Monitor monitor)
		{
			this.monitor = monitor;
		}

		@Override
		public void run()
		{
			try
			{
				logger.info("Attempting to stop job monitor.");
				
				monitor.stopRunning();
			}
			catch (InterruptedException ex)
			{
				logger.log(Level.SEVERE,
						"Uncaught exception while stopping job monitor", ex);
			}
		}

	}
}
