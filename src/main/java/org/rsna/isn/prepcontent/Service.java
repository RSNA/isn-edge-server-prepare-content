/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rsna.isn.prepcontent;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.tanukisoftware.wrapper.WrapperListener;
import org.tanukisoftware.wrapper.WrapperManager;

/**
 *
 * @author wtellis
 */
public class Service implements WrapperListener
{
	private static final Logger logger = Logger.getLogger(Service.class.getName());

	private static Monitor monitor;

	@Override
	public Integer start(String[] strings)
	{

		if (monitor == null)
			monitor = new Monitor();

		logger.info("Attempting to start job monitor");

		monitor.start();

		return null;
	}

	@Override
	public int stop(int exitCode)
	{
		try
		{
			if (monitor != null)
			{
				logger.info("Attempting to stop job monitor.");

				monitor.stopRunning();

				monitor = null;
			}

			return exitCode;
		}
		catch (InterruptedException ex)
		{
			logger.log(Level.WARNING,
					"Uncaught exception while stopping job monitor", ex);

			return 1;
		}
	}

	@Override
	public void controlEvent(int event)
	{
		logger.fine("Received " + event + " event");

		if (event == WrapperManager.WRAPPER_CTRL_LOGOFF_EVENT
				&& WrapperManager.isLaunchedAsService())
		{
			// Ignore
		}
		else
		{
			WrapperManager.stop(0);
		}
	}

}
