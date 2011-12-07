/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rsna.isn.prepcontent.dcm;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
import org.rsna.isn.dao.ConfigurationDao;

/**
 *
 * @author wtellis
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

	public Scp() throws SQLException
	{
		device = new Device();

		ConfigurationDao dao = new ConfigurationDao();
		aeTitle = StringUtils.defaultIfEmpty(dao.getConfiguration("scp-ae-title"), "RSNA-ISN");
		port = NumberUtils.toInt(dao.getConfiguration("scp-port"), 4104);



		NetworkConnection nc = new NetworkConnection();
		nc.setPort(port);
		device.setNetworkConnection(nc);


		NetworkApplicationEntity ae = new NetworkApplicationEntity();
		ae.setAETitle(aeTitle);
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
		CStoreHandler cstore = new CStoreHandler(CUIDS);
		ae.register(cstore);

		for (String sopClass : CUIDS)
		{
			TransferCapability capability = new TransferCapability(sopClass,
					NATIVE_LE_TS, TransferCapability.SCP);
			capabilities.add(capability);
		}








		ae.setTransferCapability(capabilities.toArray(new TransferCapability[0]));
		device.setNetworkApplicationEntity(ae);
	}

	public void start() throws IOException
	{
		this.device.startListening(new NewThreadExecutor("Edge SCP"));

		logger.info("Started listening on port "
				+ port + " with AE title " + aeTitle);
	}

}
