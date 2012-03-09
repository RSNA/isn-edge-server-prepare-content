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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.dcm4che2.data.UID;
import org.dcm4che2.net.Device;
import org.dcm4che2.net.NetworkApplicationEntity;
import org.dcm4che2.net.NetworkConnection;
import org.dcm4che2.net.NewThreadExecutor;
import org.dcm4che2.net.TransferCapability;
import org.dcm4che2.net.service.VerificationService;
import org.dcm4che2.util.UIDUtils;
import org.rsna.isn.dao.ConfigurationDao;
import org.rsna.isn.util.Environment;

/**
 * Implementation of a DICOM SCP.  This class is responsible for loading the SCP
 * configuration from the database and starting the TCP/IP listener. 
 *
 * @author Wyatt Tellis
 * @version 2.1.0
 * @since 2.1.0
 */
public class Scp
{
	private static final Logger logger = Logger.getLogger(Scp.class);

	private static final String[] NATIVE_LE_TS =
	{
		UID.ExplicitVRLittleEndian,
		UID.ImplicitVRLittleEndian
	};

	private static final String[] CUIDS =
	{
		UID.BasicStudyContentNotificationSOPClassRetired,
		UID.StoredPrintStorageSOPClassRetired,
		UID.HardcopyGrayscaleImageStorageSOPClassRetired,
		UID.HardcopyColorImageStorageSOPClassRetired,
		UID.ComputedRadiographyImageStorage,
		UID.DigitalXRayImageStorageForPresentation,
		UID.DigitalXRayImageStorageForProcessing,
		UID.DigitalMammographyXRayImageStorageForPresentation,
		UID.DigitalMammographyXRayImageStorageForProcessing,
		UID.DigitalIntraoralXRayImageStorageForPresentation,
		UID.DigitalIntraoralXRayImageStorageForProcessing,
		UID.StandaloneModalityLUTStorageRetired,
		UID.EncapsulatedPDFStorage, UID.StandaloneVOILUTStorageRetired,
		UID.GrayscaleSoftcopyPresentationStateStorageSOPClass,
		UID.ColorSoftcopyPresentationStateStorageSOPClass,
		UID.PseudoColorSoftcopyPresentationStateStorageSOPClass,
		UID.BlendingSoftcopyPresentationStateStorageSOPClass,
		UID.XRayAngiographicImageStorage, UID.EnhancedXAImageStorage,
		UID.XRayRadiofluoroscopicImageStorage, UID.EnhancedXRFImageStorage,
		UID.XRayAngiographicBiPlaneImageStorageRetired,
		UID.PositronEmissionTomographyImageStorage,
		UID.StandalonePETCurveStorageRetired, UID.CTImageStorage,
		UID.EnhancedCTImageStorage, UID.NuclearMedicineImageStorage,
		UID.UltrasoundMultiframeImageStorageRetired,
		UID.UltrasoundMultiframeImageStorage, UID.MRImageStorage,
		UID.EnhancedMRImageStorage, UID.MRSpectroscopyStorage,
		UID.RTImageStorage, UID.RTDoseStorage, UID.RTStructureSetStorage,
		UID.RTBeamsTreatmentRecordStorage, UID.RTPlanStorage,
		UID.RTBrachyTreatmentRecordStorage,
		UID.RTTreatmentSummaryRecordStorage,
		UID.NuclearMedicineImageStorageRetired,
		UID.UltrasoundImageStorageRetired, UID.UltrasoundImageStorage,
		UID.RawDataStorage, UID.SpatialRegistrationStorage,
		UID.SpatialFiducialsStorage, UID.RealWorldValueMappingStorage,
		UID.SecondaryCaptureImageStorage,
		UID.MultiframeSingleBitSecondaryCaptureImageStorage,
		UID.MultiframeGrayscaleByteSecondaryCaptureImageStorage,
		UID.MultiframeGrayscaleWordSecondaryCaptureImageStorage,
		UID.MultiframeTrueColorSecondaryCaptureImageStorage,
		UID.VLImageStorageTrialRetired, UID.VLEndoscopicImageStorage,
		UID.VideoEndoscopicImageStorage, UID.VLMicroscopicImageStorage,
		UID.VideoMicroscopicImageStorage,
		UID.VLSlideCoordinatesMicroscopicImageStorage,
		UID.VLPhotographicImageStorage, UID.VideoPhotographicImageStorage,
		UID.OphthalmicPhotography8BitImageStorage,
		UID.OphthalmicPhotography16BitImageStorage,
		UID.StereometricRelationshipStorage,
		UID.VLMultiframeImageStorageTrialRetired,
		UID.StandaloneOverlayStorageRetired, UID.BasicTextSRStorage,
		UID.EnhancedSRStorage, UID.ComprehensiveSRStorage,
		UID.ProcedureLogStorage, UID.MammographyCADSRStorage,
		UID.KeyObjectSelectionDocumentStorage,
		UID.ChestCADSRStorage, UID.XRayRadiationDoseSRStorage,
		UID.EncapsulatedPDFStorage, UID.EncapsulatedCDAStorage,
		UID.StandaloneCurveStorageRetired,
		UID._12leadECGWaveformStorage, UID.GeneralECGWaveformStorage,
		UID.AmbulatoryECGWaveformStorage, UID.HemodynamicWaveformStorage,
		UID.CardiacElectrophysiologyWaveformStorage,
		UID.BasicVoiceAudioWaveformStorage, UID.HangingProtocolStorage,
		UID.SiemensCSANonImageStorage
	};

	private final String aeTitle;

	private final int port;

	private final Device device;

	public Scp() throws Exception
	{
		ConfigurationDao dao = new ConfigurationDao();


		port = NumberUtils.toInt(dao.getConfiguration("scp-port"), 4104);
		int releaseTimeout = NumberUtils.toInt(dao.getConfiguration("scp-release-timeout"), 5000);
		int requestTimeout = NumberUtils.toInt(dao.getConfiguration("scp-request-timeout"), 5000);

		NetworkConnection nc = new NetworkConnection();
		nc.setPort(port);
		nc.setReleaseTimeout(releaseTimeout);
		nc.setRequestTimeout(requestTimeout);







		this.aeTitle = StringUtils.defaultIfEmpty(dao.getConfiguration("scp-ae-title"), "RSNA-ISN");
		int maxSendPduLength = NumberUtils.toInt(dao.getConfiguration("scp-max-send-pdu-length"), 16364);
		int maxReceivePduLength = NumberUtils.toInt(dao.getConfiguration("scp-max-receive-pdu-length"), 16364);


		NetworkApplicationEntity ae = new NetworkApplicationEntity();
		ae.setAETitle(aeTitle);
		ae.setMaxPDULengthSend(maxSendPduLength);
		ae.setMaxPDULengthReceive(maxReceivePduLength);
		ae.setAssociationAcceptor(true);


		List<TransferCapability> capabilities = new ArrayList();



		//
		// Enable C-ECHO support
		//
		ae.register(new VerificationService());

		TransferCapability verification = new TransferCapability(UID.VerificationSOPClass,
				NATIVE_LE_TS, TransferCapability.SCP);
		capabilities.add(verification);




		//
		// Enable C-STORE support
		//

		Properties props = new Properties();
		File confDir = Environment.getConfDir();
		File propFile = new File(confDir, "dicom.properties");
		if (propFile.exists())
		{
			FileInputStream in = new FileInputStream(propFile);
			props.load(in);

			in.close();
		}


		Set<String> sopClassUids = new HashSet();
		sopClassUids.addAll(Arrays.asList(CUIDS));
		
		for (String key : props.stringPropertyNames())
		{
			if (key.startsWith("scp.sop_class."))
			{
				String sopClsUid = props.getProperty(key).trim();
				if (!UIDUtils.isValidUID(sopClsUid))
				{
					throw new IllegalArgumentException("Invalid SOP class UID in dicom.properties file: \"" + sopClsUid
							+ "\".  Property key is: " + key);
				}
				
				if(sopClassUids.add(sopClsUid))
					logger.info("Added support for SOP class: " + sopClsUid);				
			}
		}


		CStoreHandler cstore = new CStoreHandler(CUIDS);
		ae.register(cstore);
		ae.addAssociationListener(cstore);

		for (String sopClassUid : sopClassUids)
		{
			TransferCapability capability = new TransferCapability(sopClassUid,
					NATIVE_LE_TS, TransferCapability.SCP);
			capabilities.add(capability);
		}

		ae.setTransferCapability(capabilities.toArray(new TransferCapability[0]));




		device = new Device();
		device.setNetworkConnection(nc);
		device.setNetworkApplicationEntity(ae);
	}

	public void start() throws IOException
	{
		this.device.startListening(new NewThreadExecutor("Edge SCP"));

		logger.info("Started listening on port "
				+ port + " with AE title " + aeTitle);
	}

}
