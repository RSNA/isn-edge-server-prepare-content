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
 * OF SUCH DAMAGE.
 * 
 * 
 * 
 * 
 * 
 * 3.1.0:
 *		05/20/2013: Wyatt Tellis
 *			- Moved logic for handling retryable jobs to ScpAssociationListener
 *
 *
 * 3.1.1:
 *		07/03/2014: Wyatt Tellis
 *			- Changed the way images are handled.  Images are now stored to a
 *			  temp file first and then only the header is read back into memory.
 *			  This change is required because calling PDVInputStream.readDataset
 *            loads the entire object into memory. 
 */
package org.rsna.isn.prepcontent.dcm;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.DicomOutputStream;
import org.dcm4che2.io.StopTagInputHandler;
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
 * @version 3.1.1
 * @since 2.1.0
 */
public class CStoreHandler extends DicomService implements CStoreSCP, AssociationListener
{
	private static final Logger logger = Logger.getLogger(CStoreHandler.class);

	private static final ThreadLocal<Map<String, List<Job>>> jobsMap
			= new ThreadLocal<Map<String, List<Job>>>();

	public final File dcmDir;

	public final File tmpDir;

	public CStoreHandler(String[] sopClasses)
	{
		super(sopClasses);

		this.dcmDir = Environment.getDcmDir();

		this.tmpDir = Environment.getTmpDir();
	}

	@Override
	public void associationAccepted(AssociationAcceptEvent event)
	{
		jobsMap.set(new HashMap<String, List<Job>>());
	}

	@Override
	public void cstore(Association as, int pcid, DicomObject cmd,
			PDVInputStream dataStream, String tsuid) throws DicomServiceException, IOException
	{
		String cuid = cmd.getString(Tag.AffectedSOPClassUID);
		String iuid = cmd.getString(Tag.AffectedSOPInstanceUID);

		File tmpFile = File.createTempFile(iuid + "-", ".dcm", tmpDir);
		try
		{
			// Write object to disk because calling dataStream.readDataset()
			// will load the entire object into memory resulting in potential
			// out of memory errors


			DicomOutputStream dos = new DicomOutputStream(
					new BufferedOutputStream(
							new FileOutputStream(tmpFile)));

			BasicDicomObject fmi = new BasicDicomObject();
			fmi.initFileMetaInformation(cuid, iuid, tsuid);
			dos.writeFileMetaInformation(fmi);

			dataStream.copyTo(dos);
			dos.close();


			// Read in just the header
			DicomInputStream din = new DicomInputStream(tmpFile);
			din.setHandler(new StopTagInputHandler(Tag.PixelData));

			DicomObject header = din.readDicomObject();
			din.close();

			String mrn = header.getString(Tag.PatientID);
			if (StringUtils.isBlank(mrn))
			{
				logger.warn("Patient id is empty");

				throw new DicomServiceException(cmd,
						Status.ProcessingFailure, "Patient id is empty");
			}

			String accNum = header.getString(Tag.AccessionNumber);
			if (StringUtils.isBlank(accNum))
			{
				logger.warn("Accession number is empty");

				throw new DicomServiceException(cmd,
						Status.ProcessingFailure, "Accession number is empty");
			}

			String studyUid = header.getString(Tag.StudyInstanceUID);
			if (StringUtils.isBlank(studyUid))
			{
				logger.warn("Study UID is empty");

				throw new DicomServiceException(cmd,
						Status.ProcessingFailure, "Study UID is empty");
			}


			String instanceUid = header.getString(Tag.SOPInstanceUID);
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

				for (Job job : jobs)
				{
					int status = job.getStatus();
					if (status < 0)
					{
						dao.updateStatus(job, Job.RSNA_STARTED_DICOM_C_MOVE,
								"Receiving images for study " + studyUid);

						ScpAssociationListener.addJobToRetry(as, job);

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

				File dcmFile = FileUtil.newFile(studyDir, instanceUid + ".dcm");
				FileUtils.copyFile(tmpFile, dcmFile);

				logger.info("Saved file " + dcmFile + " for " + job);
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
		finally
		{
			tmpFile.delete();
		}

	}

	@Override
	public void associationClosed(AssociationCloseEvent event)
	{
		jobsMap.remove();
	}

}
