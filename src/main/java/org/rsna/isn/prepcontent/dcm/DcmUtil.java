/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rsna.isn.prepcontent.dcm;

import java.util.concurrent.Executor;
import java.util.logging.Logger;
import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.VR;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.CommandUtils;
import org.dcm4che2.net.DimseRSPHandler;
import org.dcm4che2.net.ExtRetrieveTransferCapability;
import org.dcm4che2.net.NetworkApplicationEntity;
import org.dcm4che2.net.NetworkConnection;
import org.dcm4che2.net.NewThreadExecutor;
import org.dcm4che2.net.TransferCapability;
import org.rsna.isn.prepcontent.dao.JobDao;
import org.rsna.isn.prepcontent.domain.Device;
import org.rsna.isn.prepcontent.domain.Exam;
import org.rsna.isn.prepcontent.domain.Job;

/**
 *
 * @author wtellis
 */
public class DcmUtil
{
	private static final Logger logger = Logger.getLogger(DcmUtil.class.getName());

	private static final String moveUids[] =
	{
		UID.StudyRootQueryRetrieveInformationModelMOVE,
		UID.PatientRootQueryRetrieveInformationModelMOVE,
		UID.PatientStudyOnlyQueryRetrieveInformationModelMOVERetired
	};

	private static final String transferSyntaxes[] =
	{
		UID.ImplicitVRLittleEndian,
		UID.ExplicitVRLittleEndian
	};

	private static TransferCapability capabilities[] = new TransferCapability[moveUids.length];

	static
	{
		for (int i = 0; i < moveUids.length; i++)
		{
			String cuid = moveUids[i];

			ExtRetrieveTransferCapability tc = new ExtRetrieveTransferCapability(
					cuid, transferSyntaxes, TransferCapability.SCU);
			tc.setExtInfoBoolean(ExtRetrieveTransferCapability.RELATIONAL_RETRIEVAL, true);

			capabilities[i] = tc;
		}
	}

	private DcmUtil()
	{
	}

	public static boolean moveStudy(Device device, Job job) throws Exception
	{
		NetworkConnection remoteConn = new NetworkConnection();
		remoteConn.setHostname(device.getHost());
		remoteConn.setPort(device.getPort());

		NetworkApplicationEntity remoteAe = new NetworkApplicationEntity();
		remoteAe.setAETitle(device.getAeTitle());
		remoteAe.setAssociationAcceptor(true);
		remoteAe.setNetworkConnection(remoteConn);


		NetworkConnection localConn = new NetworkConnection();

		NetworkApplicationEntity localAe = new NetworkApplicationEntity();
		localAe.setAETitle("RSNA-ISN");
		localAe.setAssociationInitiator(true);
		localAe.setNetworkConnection(localConn);
		localAe.setTransferCapability(capabilities);



		org.dcm4che2.net.Device dcmDevice = new org.dcm4che2.net.Device();
		dcmDevice.setNetworkApplicationEntity(localAe);
		dcmDevice.setNetworkConnection(localConn);


		Executor executor = new NewThreadExecutor("job-" + job.getJobId());



		Association assoc = localAe.connect(remoteAe, executor);

		TransferCapability tc = null;
		for (int i = 0; i < moveUids.length; i++)
		{
			tc = assoc.getTransferCapabilityAsSCU(moveUids[i]);
			if (tc != null)
				break;
		}

		if (tc == null)
		{
			logger.warning("C-MOVE not supported by " + remoteAe.getAETitle());

			assoc.release(true);

			return false;
		}
		else
		{
			String cuid = tc.getSopClass();
			String tsuid = tc.getTransferSyntax()[0];

			Exam exam = job.getExam();
			String mrn = exam.getMrn();
			String accNum = exam.getAccNum();

			DicomObject keys = new BasicDicomObject();
			keys.putString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
			keys.putString(Tag.PatientID, VR.LO, mrn);
			keys.putString(Tag.AccessionNumber, VR.SH, accNum);


			CMoveResponseHandler handler = new CMoveResponseHandler();
			assoc.cmove(cuid, 0, keys, tsuid, "RSNA-ISN", handler);

			assoc.waitForDimseRSP();

			assoc.release(true);


			if (handler.completed + handler.warning + handler.failed == 0)
			{
				return false;
			}
			else
			{
				int status;
				String msg;
				if (handler.warning > 0 || handler.failed > 0)
				{
					status = Job.FAILED;

					msg = "Unable to retrieve study. There were "
							+ handler.warning + " warnings and "
							+ handler.failed + " failures";
				}
				else
				{
					status = Job.SUCCESSFUL;

					msg = "Successfully retrieved "
							+ handler.completed + " objects";

				}

				JobDao dao = new JobDao();
				dao.updateStatus(job, status, msg);

				return true;
			}
		}
	}

	private static class CMoveResponseHandler extends DimseRSPHandler
	{
		private int completed = 0;

		private int warning = 0;

		private int failed = 0;

		@Override
		public void onDimseRSP(Association as, DicomObject cmd, DicomObject data)
		{
			if (!CommandUtils.isPending(cmd))
			{
				completed += cmd.getInt(Tag.NumberOfCompletedSuboperations);
				warning += cmd.getInt(Tag.NumberOfWarningSuboperations);
				failed += cmd.getInt(Tag.NumberOfFailedSuboperations);
			}
		}

	}
}
