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

import org.apache.commons.lang.StringUtils;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.UIDDictionary;

/**
 * Utility class for creating default scp.properties file. 
 * 
 * @author Wyatt Tellis
 */
class DefaultScpConfigCreator
{
	private static final UIDDictionary dict = UIDDictionary.getDictionary();

	private static final String[] TS =
	{
		UID.ImplicitVRLittleEndian,
		UID.ExplicitVRLittleEndian,
		UID.ExplicitVRBigEndian,
		UID.JPEGBaseline1,
		UID.JPEGExtended24,
		UID.JPEGLosslessNonHierarchical14,
		UID.JPEGLossless,
		UID.MPEG2,
		UID.RLELossless
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
		UID.DigitalIntraOralXRayImageStorageForPresentation,
		UID.DigitalIntraOralXRayImageStorageForProcessing,
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
		UID.UltrasoundMultiFrameImageStorageRetired,
		UID.UltrasoundMultiFrameImageStorage, UID.MRImageStorage,
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
		UID.MultiFrameSingleBitSecondaryCaptureImageStorage,
		UID.MultiFrameGrayscaleByteSecondaryCaptureImageStorage,
		UID.MultiFrameGrayscaleWordSecondaryCaptureImageStorage,
		UID.MultiFrameTrueColorSecondaryCaptureImageStorage,
		UID.VLImageStorageTrialRetired, UID.VLEndoscopicImageStorage,
		UID.VideoEndoscopicImageStorage, UID.VLMicroscopicImageStorage,
		UID.VideoMicroscopicImageStorage,
		UID.VLSlideCoordinatesMicroscopicImageStorage,
		UID.VLPhotographicImageStorage, UID.VideoPhotographicImageStorage,
		UID.OphthalmicPhotography8BitImageStorage,
		UID.OphthalmicPhotography16BitImageStorage,
		UID.StereometricRelationshipStorage,
		UID.VLMultiFrameImageStorageTrialRetired,
		UID.StandaloneOverlayStorageRetired, UID.BasicTextSRStorage,
		UID.EnhancedSRStorage, UID.ComprehensiveSRStorage,
		UID.ProcedureLogStorage, UID.MammographyCADSRStorage,
		UID.KeyObjectSelectionDocumentStorage,
		UID.ChestCADSRStorage, UID.XRayRadiationDoseSRStorage,
		UID.EncapsulatedPDFStorage, UID.EncapsulatedCDAStorage,
		UID.StandaloneCurveStorageRetired,
		UID.TwelveLeadECGWaveformStorage, UID.GeneralECGWaveformStorage,
		UID.AmbulatoryECGWaveformStorage, UID.HemodynamicWaveformStorage,
		UID.CardiacElectrophysiologyWaveformStorage,
		UID.BasicVoiceAudioWaveformStorage, UID.HangingProtocolStorage,
		UID.SiemensCSANonImageStorage
	};

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args)
	{
		for (String txuid : TS)
		{
			System.out.println("#\t" + txuid + " = " + dict.nameOf(txuid));
		}

		System.out.println();
		System.out.println();

		for (String cuid : CUIDS)
		{
			System.out.println("# " + dict.nameOf(cuid));
			System.out.println(cuid + " = " + StringUtils.join(TS, ", "));
			System.out.println();
		}
	}

}
