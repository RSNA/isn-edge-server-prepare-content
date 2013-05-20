/* Copyright (c) <2013>, <Radiological Society of North America>
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
package org.rsna.isn.prepcontent.dcm;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.apache.log4j.Logger;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.AssociationAcceptEvent;
import org.dcm4che2.net.AssociationCloseEvent;
import org.dcm4che2.net.AssociationListener;
import org.rsna.isn.dao.JobDao;
import org.rsna.isn.domain.Job;

/**
 * Handles association open and close events within the SCP.
 *
 * @author Wyatt Tellis
 * @since 3.1.0
 * @version 3.1.0
 */
public class ScpAssociationListener implements AssociationListener
{
	private static final Logger logger =
			Logger.getLogger(ScpAssociationListener.class);

	private static final Map<Association, Set<Job>> jobsToRetry =
			Collections.synchronizedMap(new WeakHashMap<Association, Set<Job>>());

	@Override
	public void associationAccepted(AssociationAcceptEvent event)
	{
	}

	@Override
	public void associationClosed(AssociationCloseEvent event)
	{
		Association a = event.getAssociation();
		
		Set<Job> jobs = getJobs(event.getAssociation());
		jobsToRetry.remove(a);
		
		JobDao dao = new JobDao();
		for (Job job : jobs)
		{
			try
			{
				Job retry = dao.getJobById(job.getJobId());
				if (retry == null) // Shouldn't happen but just in case
					continue;

				int status = retry.getStatus();
				if (status == Job.RSNA_STARTED_DICOM_C_MOVE)
				{
					dao.updateStatus(job,
							Job.RSNA_WAITING_FOR_PREPARE_CONTENT, "Retried by SCP");

					logger.warn("Retrying " + job);
				}
			}
			catch (SQLException ex)
			{
				logger.warn("Unable to retry " + job, ex);
			}
		}

	}

	private static Set<Job> getJobs(Association a)
	{
		synchronized (jobsToRetry)
		{
			Set<Job> jobs = jobsToRetry.get(a);
			if (jobs == null)
			{
				jobs = new HashSet<Job>();

				jobsToRetry.put(a, jobs);
			}

			return jobs;
		}
	}

	public static boolean addJobToRetry(Association a, Job j)
	{
		Set<Job> jobs = getJobs(a);

		return jobs.add(j);
	}

}
