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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
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
import org.dcm4che2.net.TransferCapability;
import org.rsna.isn.dao.DeviceDao;
import org.rsna.isn.domain.Device;
import org.rsna.isn.domain.Exam;
import org.rsna.isn.domain.Job;

/**
 * DICOM C-FIND utility class
 * 
 * @author Wyatt Tellis
 * @since 3.1.0
 * @version 3.1.0
 */
public class CFind
{
	private static final Logger logger = Logger.getLogger(CFind.class);
	
	private static TransferCapability capabilities[] =
			new TransferCapability[2];
	
	private final Device device;
	
	private final Job job;
	
	static
	{
		String txs[] =
		{
			UID.ImplicitVRLittleEndian,
			UID.ExplicitVRLittleEndian
		};
		
		
		TransferCapability patientRoot =
				new TransferCapability(UID.PatientRootQueryRetrieveInformationModelFIND,
				txs, TransferCapability.SCU);
		capabilities[0] = patientRoot;
		
		
		TransferCapability studyRoot =
				new TransferCapability(UID.StudyRootQueryRetrieveInformationModelFIND,
				txs, TransferCapability.SCU);
		capabilities[1] = studyRoot;
	}
	
	private CFind(Device device, Job job)
	{
		this.device = device;
		this.job = job;
	}
	
	private List<CFindResponse> doFind() throws SQLException,
			ConfigurationException, IOException, InterruptedException
	{
		List<CFindResponse> responses = new ArrayList();
		
		Exam exam = job.getExam();
		String mrn = exam.getMrn();
		String accNum = exam.getAccNum();
		
		String name = "cfind-" + mrn + "-" + accNum;
		Association assoc = DcmUtil.connect(device, capabilities, name);
		
		try
		{
			TransferCapability tc =
					DcmUtil.selectCapabilityAsScu(assoc, capabilities);
			if (tc == null)
			{
				throw new ConfigurationException("C-FIND not supported by "
						+ device.getAeTitle());
			}
			
			String cuid = tc.getSopClass();
			String tsuid = tc.getTransferSyntax()[0];
			
			DicomObject keys = new BasicDicomObject();
			keys.putString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
			keys.putString(Tag.PatientID, VR.LO, mrn);
			keys.putString(Tag.AccessionNumber, VR.SH, accNum);
			keys.putNull(Tag.StudyInstanceUID, VR.UI);
			keys.putNull(Tag.NumberOfStudyRelatedInstances, VR.IS);
			
			
			DimseRSP rsp = assoc.cfind(cuid, 0, keys, tsuid, Integer.MAX_VALUE);
			while (rsp.next())
			{
				DicomObject cmd = rsp.getCommand();
				if (CommandUtils.isPending(cmd))
				{
					DicomObject dataset = rsp.getDataset();
					
					String studyUid = dataset.getString(Tag.StudyInstanceUID);
					int count =  // Make sure we get a postive count
							Math.max(0, dataset.getInt(Tag.NumberOfStudyRelatedInstances));
					
					if (StringUtils.isNotBlank(studyUid))
					{
						CFindResponse response =
								new CFindResponse(device, job, studyUid, count);
						responses.add(response);
						
						logger.info("Found study for " + job + " on " + device.getAeTitle() + ".  "
								+ "Study UID is " + studyUid + ". Image count is " + count + ".");
					}
					else
					{
						logger.warn(device.getAeTitle() + " responded with blank study UID. "
								+ "MRN is " + mrn + ", acc # is " + accNum);
					}
				}
			}
			
			return responses;
		}
		finally
		{
			assoc.release(true);
		}
	}
	
	public static List<CFindResponse> findStudies(Job job)
			throws SQLException, ConfigurationException, IOException, InterruptedException
	{
		List<CFindResponse> responses = new ArrayList();
		
		DeviceDao deviceDao = new DeviceDao();
		
		Set<Device> devices = deviceDao.getDevices();
		for (Device device : devices)
		{
			CFind cfind = new CFind(device, job);
			List<CFindResponse> temp = cfind.doFind();
			
			responses.addAll(temp);
		}
		
		return responses;
	}
	
}
