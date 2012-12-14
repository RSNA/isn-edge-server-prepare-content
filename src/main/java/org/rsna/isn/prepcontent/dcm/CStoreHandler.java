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
package org.rsna.isn.prepcontent.dcm;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomOutputStream;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.AssociationAcceptEvent;
import org.dcm4che2.net.AssociationCloseEvent;
import org.dcm4che2.net.AssociationListener;
import org.dcm4che2.net.CommandUtils;
import org.dcm4che2.net.DicomServiceException;
import org.dcm4che2.net.PDVInputStream;
import org.dcm4che2.net.Status;
import org.dcm4che2.net.service.CStoreSCP;
import org.dcm4che2.net.service.DicomService;
import org.rsna.isn.dao.JobDao;
import org.rsna.isn.domain.Job;
import org.rsna.isn.util.Environment;
import org.rsna.isn.util.FileUtil;

/**
 * Handler for C-STORE requests
 *
 * @author Wyatt Tellis
 * @version 3.1.0
 * @since 2.1.0
 */
public class CStoreHandler extends DicomService implements CStoreSCP, AssociationListener
{
	private static final Logger logger = Logger.getLogger(CStoreHandler.class);

	private static final ThreadLocal<Map<String, List<Job>>> jobsMap = new ThreadLocal();

	private static final ThreadLocal<Set<Job>> jobsToRetry = new ThreadLocal();

	public final File dcmDir;

	public CStoreHandler(String[] sopClasses)
	{
		super(sopClasses);

		this.dcmDir = Environment.getDcmDir();
	}

	@Override
	public void associationAccepted(AssociationAcceptEvent event)
	{
		jobsMap.set(new HashMap());

		jobsToRetry.set(new HashSet());
	}

	@Override
	public void cstore(Association as, int pcid, DicomObject cmd,
			PDVInputStream dataStream, String tsuid) throws DicomServiceException, IOException
	{
		try
		{
			DicomObject dcmObj = dataStream.readDataset();

			String mrn = dcmObj.getString(Tag.PatientID);
			if (StringUtils.isBlank(mrn))
			{
				logger.warn("Patient id is empty");

				throw new DicomServiceException(cmd,
						Status.ProcessingFailure, "Patient id is empty");
			}

			String accNum = dcmObj.getString(Tag.AccessionNumber);
			if (StringUtils.isBlank(accNum))
			{
				logger.warn("Accession number is empty");

				throw new DicomServiceException(cmd,
						Status.ProcessingFailure, "Accession number is empty");
			}

			String studyUid = dcmObj.getString(Tag.StudyInstanceUID);
			if (StringUtils.isBlank(studyUid))
			{
				logger.warn("Study UID is empty");

				throw new DicomServiceException(cmd,
						Status.ProcessingFailure, "Study UID is empty");
			}


			String instanceUid = dcmObj.getString(Tag.SOPInstanceUID);
			if (StringUtils.isBlank(instanceUid))
			{
				logger.warn("SOP instance UID is empty");

				throw new DicomServiceException(cmd,
						Status.ProcessingFailure, "SOP instance UID is empty");
			}


			String key = mrn + "/" + accNum;
			Map<String, List<Job>> map = jobsMap.get();
			List<Job> jobs = map.get(key);
			if (jobs == null)
			{
				JobDao dao = new JobDao();
				jobs = dao.findJobs(mrn, accNum,
						Job.RSNA_STARTED_DICOM_C_MOVE, Job.RSNA_FAILED_TO_PREPARE_CONTENT,
						Job.RSNA_UNABLE_TO_FIND_IMAGES, Job.RSNA_DICOM_C_MOVE_FAILED);

				if (jobs.isEmpty())
				{
					logger.warn("No pending jobs associated with: " + mrn + "/" + accNum);

					throw new DicomServiceException(cmd, Status.ProcessingFailure,
							"No pending jobs associated with this study.");
				}

				Set<Job> retries = jobsToRetry.get();
				for (Job job : jobs)
				{
					int status = job.getStatus();
					if (status < 0)
					{
						dao.updateStatus(job, Job.RSNA_STARTED_DICOM_C_MOVE,
								"Receiving images for study " + studyUid);

						retries.add(job);

						logger.warn("Flagging " + job + " as in progress.");
					}
				}

				map.put(key, jobs);
			}


			for (Job job : jobs)
			{
				int jobId = job.getJobId();

				File jobDir = FileUtil.newFile(dcmDir, jobId);
				File patDir = FileUtil.newFile(jobDir, mrn);
				File examDir = FileUtil.newFile(patDir, accNum);
				File studyDir = FileUtil.newFile(examDir, studyUid);

				studyDir.mkdirs();

				BasicDicomObject fmi = new BasicDicomObject();
				String classUid = dcmObj.getString(Tag.SOPClassUID);
				fmi.initFileMetaInformation(classUid, instanceUid, tsuid);


				File dcmFile = FileUtil.newFile(studyDir, instanceUid + ".dcm");
				DicomOutputStream dout = new DicomOutputStream(dcmFile);
				dout.writeFileMetaInformation(fmi);
				dout.writeDataset(dcmObj, tsuid);

				dout.close();

				logger.info("Saved file " + dcmFile + " for " + jobId);
			}


			as.writeDimseRSP(pcid, CommandUtils.mkRSP(cmd, CommandUtils.SUCCESS));
		}
		catch (IOException ex) // Includes DicomServiceException
		{
			throw ex;
		}
		catch (Throwable ex)
		{
			logger.warn("Unable to store object due to uncaught exception.", ex);

			throw new DicomServiceException(cmd, Status.ProcessingFailure, ex.getMessage());
		}
	}

	@Override
	public void associationClosed(AssociationCloseEvent event)
	{
		JobDao dao = new JobDao();

		Set<Job> jobs = jobsToRetry.get();
		for (Job job : jobs)
		{
			try
			{
				Job retry = dao.getJobById(job.getJobId());
				if(retry == null) // Shouldn't happen but just in case
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


		jobsToRetry.remove();
		jobsMap.remove();
	}

}
