/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rsna.isn.prepcontent;

import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.rsna.isn.dao.DeviceDao;
import org.rsna.isn.dao.JobDao;
import org.rsna.isn.prepcontent.dcm.DcmUtil;
import org.rsna.isn.domain.Device;
import org.rsna.isn.domain.Exam;
import org.rsna.isn.domain.Job;

/**
 *
 * @author wtellis
 */
public class Worker extends Thread
{
	private static final Logger logger = Logger.getLogger(Worker.class);

	private final Job job;

	public Worker(ThreadGroup group, Job job)
	{
		super(group, "worker-" + job.getJobId());

		this.job = job;
	}

	@Override
	public void run()
	{
		logger.info("Started worker thread");

		try
		{
			JobDao dao = new JobDao();
			try
			{
				Exam exam = job.getExam();
				String mrn = exam.getMrn();
				String accNum = exam.getAccNum();

				if (StringUtils.isEmpty(mrn))
				{
					dao.updateStatus(job, Job.FAILED_TO_PREPARE_CONTENT, "No mrn");

					return;
				}
				else if (StringUtils.isEmpty(accNum))
				{
					dao.updateStatus(job, Job.FAILED_TO_PREPARE_CONTENT, "No acc #");

					return;
				}
				else
				{
					DeviceDao deviceDao = new DeviceDao();

					Set<Device> devices = deviceDao.getDevices();
					for (Device device : devices)
					{
						try
						{
							if (DcmUtil.processJob(device, job))
							{
								logger.info("Completed processing of " + job);

								return;
							}
						}
						catch (Exception ex)
						{
							logger.error("Uncaught exception while doing C-MOVE for " + job, ex);

							dao.updateStatus(job, Job.DICOM_C_MOVE_FAILED, ex);

							return;
						}
					}

					dao.updateStatus(job, Job.UNABLE_TO_FIND_IMAGES,
							"Unable to retrive study from any remote device.");
				}


			}
			catch (Exception ex)
			{
				logger.error("Uncaught exception while processing job " + job, ex);

				dao.updateStatus(job, Job.FAILED_TO_PREPARE_CONTENT, ex);
			}
		}
		catch (Exception ex)
		{
			logger.error("Uncaught exception while updating job " + job, ex);
		}
	}

}
