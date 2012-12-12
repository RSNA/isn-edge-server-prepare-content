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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.apache.commons.lang.time.DateUtils;
import org.apache.log4j.Logger;
import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.VR;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.CommandUtils;
import org.dcm4che2.net.ConfigurationException;
import org.dcm4che2.net.DimseRSP;
import org.dcm4che2.net.DimseRSPHandler;
import org.dcm4che2.net.ExtRetrieveTransferCapability;
import org.dcm4che2.net.NetworkApplicationEntity;
import org.dcm4che2.net.NetworkConnection;
import org.dcm4che2.net.NewThreadExecutor;
import org.dcm4che2.net.TransferCapability;
import org.dcm4che2.util.StringUtils;
import org.rsna.isn.dao.ConfigurationDao;
import org.rsna.isn.dao.JobDao;
import org.rsna.isn.domain.Device;
import org.rsna.isn.domain.Exam;
import org.rsna.isn.domain.Job;
import org.rsna.isn.util.Environment;

/**
 * A collection of DICOM utility functions. Mostly used by the worker thread.
 *
 * @author Wyatt Tellis
 * @version 2.1.0
 *
 */
public class DcmUtil
{
	private static final Logger logger = Logger.getLogger(DcmUtil.class);

	private static final String moveUids[] =
	{
		UID.StudyRootQueryRetrieveInformationModelMOVE
	//UID.PatientRootQueryRetrieveInformationModelMOVE,
	//UID.PatientStudyOnlyQueryRetrieveInformationModelMOVERetired
	};

	private static final String findUids[] =
	{
		UID.PatientRootQueryRetrieveInformationModelFIND
	};

	private static final String transferSyntaxes[] =
	{
		UID.ImplicitVRLittleEndian,
		UID.ExplicitVRLittleEndian
	};

	private static TransferCapability capabilities[] =
			new TransferCapability[moveUids.length + findUids.length];

	public static final File dcmDir;

	static
	{
		dcmDir = Environment.getDcmDir();


		for (int i = 0; i < moveUids.length; i++)
		{
			String cuid = moveUids[i];

			ExtRetrieveTransferCapability tc = new ExtRetrieveTransferCapability(
					cuid, transferSyntaxes, TransferCapability.SCU);
			tc.setExtInfoBoolean(ExtRetrieveTransferCapability.RELATIONAL_RETRIEVAL, true);

			capabilities[i] = tc;
		}

		for (int i = moveUids.length, j = 0;
				i < capabilities.length && j < findUids.length;
				i++, j++)
		{
			String cuid = findUids[j];

			ExtRetrieveTransferCapability tc = new ExtRetrieveTransferCapability(
					cuid, transferSyntaxes, TransferCapability.SCU);
			tc.setExtInfoBoolean(ExtRetrieveTransferCapability.RELATIONAL_RETRIEVAL, true);

			capabilities[i] = tc;
		}
	}

	private DcmUtil()
	{
	}

	/**
	 * Attempts a DICOM C-MOVE against the specified remote device.
	 *
	 * @param device The remote device.
	 * @param job The job to process
	 * @return True if objects were successfully retrieved, false if not
	 * @throws Exception If there was an error attempting the C-MOVE.
	 */
	public static boolean doCMove(Device device, Job job) throws Exception
	{
		List<String> studyUids = findStudyUids(device, job);

		if (studyUids.isEmpty())
			return false;

		for (String studyUid : studyUids)
		{
			String error = moveStudy(device, studyUid, job);

			if (error != null)
			{
				JobDao dao = new JobDao();
				dao.updateStatus(job, Job.RSNA_DICOM_C_MOVE_FAILED, error);

				logger.warn(job + ": C-MOVE failed for study UID " + studyUid);

				return true;
			}

			logger.info("Completed C-MOVE of study UID " + studyUid + " for " + job);
		}

		JobDao dao = new JobDao();


		File jobDir = new File(dcmDir, Integer.toString(job.getJobId()));
		if (jobDir.isDirectory())
			dao.updateStatus(job, Job.RSNA_WAITING_FOR_TRANSFER_CONTENT);
		else
			dao.updateStatus(job, Job.RSNA_DICOM_C_MOVE_FAILED, "No images received.");


		return true;
	}

	private static List<String> findStudyUids(Device device, Job job) throws Exception
	{
		List<String> uids = new ArrayList();

		Exam exam = job.getExam();
		String mrn = exam.getMrn();
		String accNum = exam.getAccNum();

		Association assoc = connect(device, "query-" + accNum);

		TransferCapability tc = null;
		for (int i = 0; i < moveUids.length; i++)
		{
			tc = assoc.getTransferCapabilityAsSCU(findUids[i]);
			if (tc != null)
				break;
		}

		if (tc == null)
		{

			assoc.release(true);

			logger.warn("C-FIND not supported by "
					+ device.getAeTitle());
		}

		String cuid = tc.getSopClass();
		String tsuid = tc.getTransferSyntax()[0];

		DicomObject keys = new BasicDicomObject();
		keys.putString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
		keys.putString(Tag.PatientID, VR.LO, mrn);
		keys.putString(Tag.AccessionNumber, VR.SH, accNum);
		keys.putString(Tag.StudyInstanceUID, VR.SH, null);

		DimseRSP rsp = assoc.cfind(cuid, 0, keys, tsuid, Integer.MAX_VALUE);
		while (rsp.next())
		{
			DicomObject cmd = rsp.getCommand();
			if (CommandUtils.isPending(cmd))
			{
				DicomObject dataset = rsp.getDataset();
				String studyUid = dataset.getString(Tag.StudyInstanceUID);
				uids.add(studyUid);
			}
		}

		assoc.release(true);

		return uids;
	}

	private static String moveStudy(Device device, String studyUid, Job job) throws Exception
	{
		Association assoc = connect(device, "transfer-" + studyUid);

		TransferCapability tc = null;
		for (int i = 0; i < moveUids.length; i++)
		{
			tc = assoc.getTransferCapabilityAsSCU(moveUids[i]);
			if (tc != null)
				break;
		}

		if (tc == null)
		{
			assoc.release(true);

			return "C-MOVE not supported by " + device.getAeTitle();
		}
		else
		{
			String cuid = tc.getSopClass();
			String tsuid = tc.getTransferSyntax()[0];

			DicomObject keys = new BasicDicomObject();
			keys.putString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
			keys.putString(Tag.StudyInstanceUID, VR.SH, studyUid);

			ConfigurationDao confDao = new ConfigurationDao();
			String scpAeTitle = confDao.getConfiguration("scp-ae-title");

			CMoveResponseHandler handler = new CMoveResponseHandler(job);
			assoc.cmove(cuid, 0, keys, tsuid, scpAeTitle, handler);

			assoc.waitForDimseRSP();

			assoc.release(true);


			if (handler.status == CommandUtils.SUCCESS)
			{
				return null;
			}
			else
			{
				StringBuilder msg = new StringBuilder();
				msg.append("C-MOVE failed for study UID: ").append(studyUid).
						append(". ");

				msg.append("Error code is: 0x").
						append(StringUtils.shortToHex(handler.status)).
						append(". ");

				msg.append("Error message is: \"").
						append(handler.error).append("\". ");

				return msg.toString();
			}

		}
	}

	private static Association connect(Device device, String threadName) throws Exception
	{
		NetworkConnection remoteConn = new NetworkConnection();
		remoteConn.setHostname(device.getHost());
		remoteConn.setPort(device.getPort());

		NetworkApplicationEntity remoteAe = new NetworkApplicationEntity();
		remoteAe.setAETitle(device.getAeTitle());
		remoteAe.setAssociationAcceptor(true);
		remoteAe.setNetworkConnection(remoteConn);


		NetworkConnection localConn = new NetworkConnection();

		ConfigurationDao confDao = new ConfigurationDao();
		String scuAeTitle = confDao.getConfiguration("scu-ae-title");

		NetworkApplicationEntity localAe = new NetworkApplicationEntity();
		localAe.setAETitle(scuAeTitle);
		localAe.setAssociationInitiator(true);
		localAe.setNetworkConnection(localConn);
		localAe.setRetrieveRspTimeout((int) DateUtils.MILLIS_PER_DAY);
		localAe.setTransferCapability(capabilities);



		org.dcm4che2.net.Device dcmDevice = new org.dcm4che2.net.Device();
		dcmDevice.setNetworkApplicationEntity(localAe);
		dcmDevice.setNetworkConnection(localConn);


		Executor executor = new NewThreadExecutor("transfer-" + threadName);

		return localAe.connect(remoteAe, executor);
	}

	private static class CMoveResponseHandler extends DimseRSPHandler
	{
		private int status = -1;

		private String error = "";

		private final Job job;

		private final JobDao jobDao = new JobDao();

		public CMoveResponseHandler(Job job)
		{
			this.job = job;
		}

		private void updateCount(Association as, DicomObject cmd)
		{
			int completed = cmd.getInt(Tag.NumberOfCompletedSuboperations);
			int remaining = cmd.getInt(Tag.NumberOfRemainingSuboperations);
			int warning = cmd.getInt(Tag.NumberOfWarningSuboperations);
			int done = completed + warning;


			int total = done + remaining;
			if (total > 0)
			{
				try
				{
					String comments = done + " of " + total + " completed.";

					jobDao.updateComments(job, Job.RSNA_STARTED_DICOM_C_MOVE, comments);
				}
				catch (Exception ex)
				{
					logger.error("Unable to update comments "
							+ "for job #" + job.getJobId(), ex);

					as.abort();
				}
			}
		}

		@Override
		public void onDimseRSP(Association as, DicomObject cmd, DicomObject data)
		{
			if (!CommandUtils.isPending(cmd))
			{
				status = cmd.getInt(Tag.Status);

				error = cmd.getString(Tag.ErrorComment, "");
			}


			updateCount(as, cmd);
		}

	}

	public static Association connect(Device device,
			TransferCapability[] capabilities, String threadName)
			throws SQLException, ConfigurationException, IOException, InterruptedException
	{
		NetworkConnection remoteConn = new NetworkConnection();
		remoteConn.setHostname(device.getHost());
		remoteConn.setPort(device.getPort());

		NetworkApplicationEntity remoteAe = new NetworkApplicationEntity();
		remoteAe.setAETitle(device.getAeTitle());
		remoteAe.setAssociationAcceptor(true);
		remoteAe.setNetworkConnection(remoteConn);


		NetworkConnection localConn = new NetworkConnection();

		ConfigurationDao confDao = new ConfigurationDao();
		String scuAeTitle = confDao.getConfiguration("scu-ae-title");

		NetworkApplicationEntity localAe = new NetworkApplicationEntity();
		localAe.setAETitle(scuAeTitle);
		localAe.setAssociationInitiator(true);
		localAe.setNetworkConnection(localConn);
		localAe.setRetrieveRspTimeout((int) DateUtils.MILLIS_PER_DAY);
		localAe.setTransferCapability(capabilities);



		org.dcm4che2.net.Device dcmDevice = new org.dcm4che2.net.Device();
		dcmDevice.setNetworkApplicationEntity(localAe);
		dcmDevice.setNetworkConnection(localConn);


		Executor executor = new NewThreadExecutor(threadName);

		return localAe.connect(remoteAe, executor);
	}

	public static TransferCapability selectCapabilityAsScu(Association assoc, 
			TransferCapability requested[])
	{
		for (TransferCapability tc : requested)
		{
			TransferCapability supported
					= assoc.getTransferCapabilityAsSCU(tc.getSopClass());
			
			if (supported != null)
				return supported;
		}
		
		return null;
	}
}
