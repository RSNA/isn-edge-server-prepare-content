/* Copyright (c) <2012>, <Radiological Society of North America>
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

import java.io.IOException;
import java.sql.SQLException;
import org.apache.log4j.Logger;
import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.VR;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.CommandUtils;
import org.dcm4che2.net.ConfigurationException;
import org.dcm4che2.net.DimseRSPHandler;
import org.dcm4che2.net.TransferCapability;
import org.rsna.isn.dao.ConfigurationDao;
import org.rsna.isn.dao.JobDao;
import org.rsna.isn.domain.Device;
import org.rsna.isn.domain.Exam;
import org.rsna.isn.domain.Job;

/**
 * DICOM C-MOVE utility class.
 *
 * @author Wyatt Tellis
 * @since 3.1.0
 * @version 3.1.0
 */
public class CMove
{
	private static final Logger logger = Logger.getLogger(CMove.class);

	private static TransferCapability capabilities[] =
			new TransferCapability[2];

	private final Device device;

	private final Job job;

	private final String studyUid;

	private final int count;

	private final JobDao dao = new JobDao();

	static
	{
		String txs[] =
		{
			UID.ImplicitVRLittleEndian,
			UID.ExplicitVRLittleEndian
		};


		TransferCapability patientRoot =
				new TransferCapability(UID.PatientRootQueryRetrieveInformationModelMOVE,
				txs, TransferCapability.SCU);
		capabilities[0] = patientRoot;


		TransferCapability studyRoot =
				new TransferCapability(UID.StudyRootQueryRetrieveInformationModelMOVE,
				txs, TransferCapability.SCU);
		capabilities[1] = studyRoot;
	}

	private CMove(Device device, Job job, String studyUid, int count)
	{
		this.device = device;
		this.job = job;
		this.studyUid = studyUid;
		this.count = count;
	}

	private CMoveResponse doMove() throws SQLException, ConfigurationException,
			IOException, InterruptedException
	{
		Exam exam = job.getExam();
		String mrn = exam.getMrn();
		String accNum = exam.getAccNum();

		String name = "cmove-" + mrn + "-" + accNum + "-" + studyUid;
		Association assoc = DcmUtil.connect(device, capabilities, name);
		try
		{
			TransferCapability tc =
					DcmUtil.selectCapabilityAsScu(assoc, capabilities);
			if (tc == null)
			{
				throw new ConfigurationException("C-MOVE not supported by "
						+ device.getAeTitle());
			}

			String cuid = tc.getSopClass();
			String tsuid = tc.getTransferSyntax()[0];

			DicomObject keys = new BasicDicomObject();
			keys.putString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
			keys.putString(Tag.PatientID, VR.LO, mrn);
			keys.putString(Tag.StudyInstanceUID, VR.UI, studyUid);

			ConfigurationDao confDao = new ConfigurationDao();
			String scpAeTitle = confDao.getConfiguration("scp-ae-title");

			CMoveHandler handler = new CMoveHandler();

			updateStatus("");
			
			logger.info("Started C-MOVE of study " + studyUid 
					+ " from " + device.getAeTitle() + " for " + job);

			assoc.cmove(cuid, 0, keys, tsuid, scpAeTitle, handler);
			assoc.waitForDimseRSP();

			
			
			logger.info("Completed C-MOVE of study " + studyUid 
					+ " from " + device.getAeTitle() + " for " + job);


			return handler.response;
		}
		finally
		{
			assoc.release(true);
		}
	}

	private void updateStatus(String msg) throws SQLException
	{
		dao.updateComments(job, Job.RSNA_STARTED_DICOM_C_MOVE, "Retrieving study "
				+ studyUid + " from " + device.getAeTitle() + ".  " + msg);
	}

	private class CMoveHandler extends DimseRSPHandler
	{
		private CMoveResponse response;

		@Override
		public void onDimseRSP(Association as, DicomObject cmd, DicomObject data)
		{
			int status = cmd.getInt(Tag.Status);
			String error = cmd.getString(Tag.ErrorComment, "");

			response = new CMoveResponse(device, status, error);

			if (CommandUtils.isPending(cmd))
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
						String comments = "Received " + done + " of " + total + " objects.";
						updateStatus(comments);
					}
					catch (Exception ex)
					{
						logger.error("Unable to update comments "
								+ "for " + job, ex);

						as.abort();
					}
				}
			}
		}

	}

	public static CMoveResponse retrieveStudy(Device device, Job job,
			String studyUid, int count) throws SQLException, ConfigurationException,
			IOException, InterruptedException
	{
		CMove cmove = new CMove(device, job, studyUid, count);

		return cmove.doMove();
	}

}
