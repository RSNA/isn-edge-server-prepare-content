/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rsna.isn.prepcontent.dcm;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
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

/**
 *
 * @author wtellis
 */
public class CStoreHandler extends DicomService implements CStoreSCP
{
	private static final Logger logger = Logger.getLogger(CStoreHandler.class);

	private static final ThreadLocal<List<Job>> associatedJobs = new ThreadLocal();

	public final File dcmDir;

	public CStoreHandler(String[] sopClasses)
	{
		super(sopClasses);

		this.dcmDir = Environment.getDcmDir();
	}

	@Override
	public void cstore(Association as, int pcid, DicomObject cmd,
			PDVInputStream dataStream, String tsuid) throws DicomServiceException, IOException
	{
		try
		{
			DicomObject dcmObj = dataStream.readDataset();

			String accNum = dcmObj.getString(Tag.AccessionNumber);
			String mrn = dcmObj.getString(Tag.PatientID);

			List<Job> jobs = associatedJobs.get();
			if (jobs == null)
			{
				JobDao dao = new JobDao();
				jobs = dao.findJobs(mrn, accNum, Job.RSNA_STARTED_DICOM_C_MOVE);

				if (jobs.isEmpty())
				{
					logger.warn("No pending jobs assoicated with: " + mrn + "/" + accNum);

					throw new DicomServiceException(cmd, Status.ProcessingFailure,
							"No pending jobs associated with this study.");
				}

				associatedJobs.set(jobs);
			}


			for (Job job : jobs)
			{
				int jobId = job.getJobId();
				File jobDir = new File(dcmDir, Integer.toString(jobId));
				File patDir = new File(jobDir, mrn);
				File examDir = new File(patDir, accNum);
				examDir.mkdirs();


				String instanceUid = dcmObj.getString(Tag.SOPInstanceUID);
				String classUid = dcmObj.getString(Tag.SOPClassUID);





				BasicDicomObject fmi = new BasicDicomObject();
				fmi.initFileMetaInformation(classUid, instanceUid, tsuid);


				File dcmFile = new File(examDir, instanceUid + ".dcm");
				DicomOutputStream dout = new DicomOutputStream(dcmFile);
				dout.writeFileMetaInformation(fmi);
				dout.writeDataset(dcmObj, tsuid);
				
				dout.close();
			}


			as.writeDimseRSP(pcid, CommandUtils.mkRSP(cmd, CommandUtils.SUCCESS));
		}
		catch (SQLException ex)
		{
			logger.warn("Unable to store object due to database error", ex);

			throw new DicomServiceException(cmd, Status.ProcessingFailure, ex.getMessage());
		}
	}

}
