/* Copyright (c) <2010>, <Radiological Society of North America>
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of the <RSNA> nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 */
package org.rsna.isn.prepcontent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.log4j.Logger;
import org.rsna.isn.dao.JobDao;
import org.rsna.isn.domain.Exam;
import org.rsna.isn.domain.Job;

/**
 * This class monitors the RSNA database for new jobs. If it finds a new job, it
 * will spawn a worker thread to process the job. Currently a max of five
 * concurrent worker threads are allowed.
 *
 * @author Wyatt Tellis
 * @version 2.1.0
 */
class Monitor extends Thread
{
	private static final Logger logger = Logger.getLogger(Monitor.class);

	private final ThreadGroup group = new ThreadGroup("workers");

	private boolean keepRunning;

	Monitor()
	{
		super("monitor");
	}

	@Override
	public void run()
	{
		logger.info("Started monitor thread");

		JobDao dao = new JobDao();

		try
		{
			Set<Job> interruptedJobs = new HashSet();
			interruptedJobs.addAll(dao.getJobsByStatus(Job.RSNA_STARTED_DICOM_C_MOVE));

			for (Job job : interruptedJobs)
			{
				dao.updateStatus(job, Job.RSNA_WAITING_FOR_PREPARE_CONTENT, "Retrying job");

				logger.info("Retrying " + job);
			}
		}
		catch (Exception ex)
		{
			logger.fatal("Uncaught exception while restarting interrupted jobs.", ex);

			return;
		}

		keepRunning = true;
		while (keepRunning)
		{
			try
			{
				Set<Job> jobsToProcess = new HashSet();


				//
				// Evaluate newly created jobs
				//
				Set<Job> newJobs = dao.getJobsByStatus(Job.RSNA_WAITING_FOR_PREPARE_CONTENT);
				for (Job job : newJobs)
				{
					Exam exam = job.getExam();
					if (exam == null)
					{
						// This is pretty serious and suggests there's a
						// database problem
						dao.updateStatus(job,
								Job.RSNA_FAILED_TO_PREPARE_CONTENT, "Unable to load exam data");

						continue;
					}


					if (!isReportReady(exam))
					{
						if ("CANCELED".equals(exam.getStatus()))
						{
							dao.updateStatus(job, Job.RSNA_FAILED_TO_PREPARE_CONTENT,
									"Exam has been canceled");
						}
						else
						{
							dao.updateStatus(job, Job.RSNA_WAITING_FOR_EXAM_FINALIZATION);
						}

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
						dao.updateStatus(job, Job.RSNA_WAITING_FOR_DELAY_EXPIRATION);

						continue;
					}

					jobsToProcess.add(job);
				}





				//
				// Evaluate jobs that are waiting for a final report
				//
				Set<Job> jobsWaitingForReport = dao.getJobsByStatus(Job.RSNA_WAITING_FOR_EXAM_FINALIZATION);
				for (Job job : jobsWaitingForReport)
				{
					Exam exam = job.getExam();
					if (exam == null)
					{
						// This is pretty serious and suggests there's a
						// database problem
						dao.updateStatus(job,
								Job.RSNA_FAILED_TO_PREPARE_CONTENT, "Unable to load exam data");

						continue;
					}



					if (!isReportReady(exam))
					{
						if ("CANCELED".equals(exam.getStatus()))
						{
							dao.updateStatus(job, Job.RSNA_FAILED_TO_PREPARE_CONTENT,
									"Exam has been canceled");
						}


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
						dao.updateStatus(job, Job.RSNA_WAITING_FOR_DELAY_EXPIRATION);

						continue;
					}

					jobsToProcess.add(job);
				}





				//
				// Evaluate jobs that are waiting for transmit delay to
				// expire
				//
				Set<Job> jobsWaitingForTransmitDelay = dao.getJobsByStatus(Job.RSNA_WAITING_FOR_DELAY_EXPIRATION);
				for (Job job : jobsWaitingForTransmitDelay)
				{
					Exam exam = job.getExam();
					if (exam == null)
					{
						// This is pretty serious and suggests there's a
						// database problem
						dao.updateStatus(job,
								Job.RSNA_FAILED_TO_PREPARE_CONTENT, "Unable to load exam data");

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
						continue;


					jobsToProcess.add(job);
				}



				for (Job job : jobsToProcess)
				{
					if (group.activeCount() >= 5)
						break;


					Exam exam = job.getExam();
					String mrn = exam.getMrn();
					String accNum = exam.getAccNum();

					//
					// Make sure we're not already processing this MRN/acc #
					// combo.  Some PACS don't like multiple C-MOVE requests
					// for the same exam. 
					//
					List<Job> otherJobs = dao.findJobs(mrn, accNum, Job.RSNA_STARTED_DICOM_C_MOVE);
					if (!otherJobs.isEmpty())
					{
						continue;
					}

					dao.updateStatus(job, Job.RSNA_STARTED_DICOM_C_MOVE);

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
			catch (Throwable ex)
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

	private boolean isReportReady(Exam exam)
	{
		String status = exam.getStatus();


		if ("FINALIZED".equals(status))
			return true;
		else if ("NON-REPORTABLE".equals(status))
			return true;
		else
			return false;
	}

}
