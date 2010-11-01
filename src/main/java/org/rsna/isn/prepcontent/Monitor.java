/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rsna.isn.prepcontent;

import java.util.HashSet;
import java.util.Set;
import org.apache.log4j.Logger;
import org.rsna.isn.dao.JobDao;
import org.rsna.isn.domain.Exam;
import org.rsna.isn.domain.Job;

/**
 *
 * @author wtellis
 */
public class Monitor extends Thread
{
	private static final Logger logger = Logger.getLogger(Monitor.class);

	private final ThreadGroup group = new ThreadGroup("workers");

	private boolean keepRunning;

	public Monitor()
	{
		super("monitor");
	}

	@Override
	public void run()
	{
		logger.info("Started monitor thread");

		JobDao dao = new JobDao();
		keepRunning = true;
		while (keepRunning)
		{
			try
			{
				Set<Job> jobsToProcess = new HashSet();


				//
				// Evaluate newly created jobs
				//
				Set<Job> newJobs = dao.getJobsByStatus(Job.WAITING_FOR_PREPARE_CONTENT);
				for (Job job : newJobs)
				{
					Exam exam = job.getExam();
					String status = exam.getStatus();

					if (!"FINALIZED".equals(status))
					{
						dao.updateStatus(job, Job.WAITING_FOR_EXAM_FINALIZATION);

						continue;
					}


					long age = System.currentTimeMillis()
							- exam.getStatusTimestamp().getTime();

					if (age < 0)
						age = 0;

					long delay = job.getDelay() * 3600000L;

					if (delay < 0)
						delay = 0;

					if (age < delay)
					{
						dao.updateStatus(job, Job.WAITING_FOR_DELAY_EXPIRATION);

						continue;
					}

					jobsToProcess.add(job);
				}





				//
				// Evaluate jobs that are waiting for a final report
				//
				Set<Job> jobsWaitingForReport = dao.getJobsByStatus(Job.WAITING_FOR_EXAM_FINALIZATION);
				for (Job job : jobsWaitingForReport)
				{
					Exam exam = job.getExam();
					String status = exam.getStatus();

					if (!"FINALIZED".equals(status))
						continue;


					long age = System.currentTimeMillis()
							- exam.getStatusTimestamp().getTime();

					if (age < 0)
						age = 0;

					long delay = job.getDelay() * 3600000L;

					if (delay < 0)
						delay = 0;

					if (age < delay)
					{
						dao.updateStatus(job, Job.WAITING_FOR_DELAY_EXPIRATION);

						continue;
					}

					jobsToProcess.add(job);
				}





				//
				// Evaluate jobs that are waiting for transmit delay to
				// expire
				//
				Set<Job> jobsWaitingForTransmitDelay = dao.getJobsByStatus(Job.WAITING_FOR_DELAY_EXPIRATION);
				for (Job job : jobsWaitingForTransmitDelay)
				{
					Exam exam = job.getExam();

					long age = System.currentTimeMillis()
							- exam.getStatusTimestamp().getTime();

					if (age < 0)
						age = 0;

					long delay = job.getDelay() * 3600000L;

					if (delay < 0)
						delay = 0;

					if (age < delay)
						continue;


					jobsToProcess.add(job);
				}



				for (Job job : jobsToProcess)
				{
					if (group.activeCount() >= 5)
						break;

					dao.updateStatus(job, Job.STARTED_DICOM_C_MOVE);
					Worker worker = new Worker(group, job);
					worker.start();
				}

				sleep(1000);
			}
			catch (InterruptedException ex)
			{
				logger.fatal("Monitor thread interrupted", ex);

				break;
			}
			catch (Exception ex)
			{
				logger.fatal("Uncaught exception while processing jobs.", ex);

				break;
			}
		}



		logger.info("Stopped monitor thread");
	}

	public void stopRunning() throws InterruptedException
	{
		keepRunning = false;

		join(10 * 1000);
	}

}
