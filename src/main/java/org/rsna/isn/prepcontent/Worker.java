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

import java.io.File;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.rsna.isn.dao.DeviceDao;
import org.rsna.isn.dao.JobDao;
import org.rsna.isn.prepcontent.dcm.DcmUtil;
import org.rsna.isn.domain.Device;
import org.rsna.isn.domain.Exam;
import org.rsna.isn.domain.Job;
import org.rsna.isn.util.Environment;

/**
 * Worker thread that processes jobs.
 *
 * @author Wyatt Tellis
 * @version 1.2.0
 */
class Worker extends Thread
{
	private static final Logger logger = Logger.getLogger(Worker.class);

	private static final File dcmDir = Environment.getDcmDir();

	private final Job job;

	Worker(ThreadGroup group, Job job)
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
					dao.updateStatus(job, Job.RSNA_FAILED_TO_PREPARE_CONTENT, "No mrn");

					return;
				}
				else if (StringUtils.isEmpty(accNum))
				{
					dao.updateStatus(job, Job.RSNA_FAILED_TO_PREPARE_CONTENT, "No acc #");

					return;
				}
				else
				{
					//File examDir = new File(new File(dcmDir, mrn), accNum);
					//FileUtils.deleteDirectory(examDir);

					DeviceDao deviceDao = new DeviceDao();

					Set<Device> devices = deviceDao.getDevices();
					for (Device device : devices)
					{
						try
						{
							if (DcmUtil.doCMove(device, job))
							{
								logger.info("Completed processing of " + job);

								return;
							}
						}
						catch (Exception ex)
						{
							logger.error("Uncaught exception while doing C-MOVE for " + job, ex);

							dao.updateStatus(job, Job.RSNA_DICOM_C_MOVE_FAILED, ex);

							return;
						}
					}

					dao.updateStatus(job, Job.RSNA_UNABLE_TO_FIND_IMAGES,
							"Unable to retrive study from any remote device.");
				}


			}
			catch (Exception ex)
			{
				logger.error("Uncaught exception while processing job " + job, ex);

				dao.updateStatus(job, Job.RSNA_FAILED_TO_PREPARE_CONTENT, ex);
			}
		}
		catch (Exception ex)
		{
			logger.error("Uncaught exception while updating job " + job, ex);
		}
	}

}
