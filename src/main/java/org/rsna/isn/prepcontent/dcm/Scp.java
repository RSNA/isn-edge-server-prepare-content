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
 * 
 * 
 * 
 * 
 * 
 * 3.1.0:
 *		05/20/2013: Wyatt Tellis
 *			- Switched to using ScpAssociationListener to track retried jobs
 */
package org.rsna.isn.prepcontent.dcm;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.UIDDictionary;
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
 * @version 3.2.0
 * @since 2.1.0
 */
public class Scp
{
	private static final Logger logger = Logger.getLogger(Scp.class);

	private static final UIDDictionary dict = UIDDictionary.getDictionary();

	private static final String[] CECHO_TS =
	{
		UID.ExplicitVRLittleEndian,
		UID.ImplicitVRLittleEndian
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
                int idleTimeout = NumberUtils.toInt(dao.getConfiguration("scp-idle-timeout"), 60000);
                

		NetworkApplicationEntity ae = new NetworkApplicationEntity();
		ae.setAETitle(aeTitle);
		ae.setMaxPDULengthSend(maxSendPduLength);
		ae.setMaxPDULengthReceive(maxReceivePduLength);
		ae.setAssociationAcceptor(true);
                ae.setIdleTimeout(idleTimeout);


		List<TransferCapability> capabilities = new ArrayList<TransferCapability>();



		//
		// Enable C-ECHO support
		//
		ae.register(new VerificationService());

		TransferCapability verification = new TransferCapability(UID.VerificationSOPClass,
				CECHO_TS, TransferCapability.SCP);
		capabilities.add(verification);




		//
		// Enable C-STORE support
		//

		Properties props = new Properties();
		File confDir = Environment.getConfDir();
		File propFile = new File(confDir, "scp.properties");
		if (propFile.exists())
		{
			FileInputStream in = new FileInputStream(propFile);
			props.load(in);

			in.close();
		}
		else
		{
			InputStream in = Scp.class.getResourceAsStream("scp.properties");


			byte buffer[] = IOUtils.toByteArray(in);
			in.close();


			props.load(new ByteArrayInputStream(buffer));

			FileOutputStream fos = new FileOutputStream(propFile);
			fos.write(buffer);
			fos.close();
		}






		// Setup the presentation contexts
		Map<String, String[]> pcs = new TreeMap<String, String[]>();
		for (String sopClass : props.stringPropertyNames())
		{
			if (!UIDUtils.isValidUID(sopClass))
			{
				throw new IllegalArgumentException("Invalid SOP class UID "
						+ "in scp.properties file: \"" + sopClass);
			}

			String value = props.getProperty(sopClass);
			String txUids[] = StringUtils.split(value, ',');
			for (int i = 0; i < txUids.length; i++)
			{
				String txUid = txUids[i].trim();

				if (!UIDUtils.isValidUID(txUid))
				{
					throw new IllegalArgumentException("Invalid transfer syntax UID "
							+ "in scp.properties file: \"" + txUid);
				}

				txUids[i] = txUid;
			}


			pcs.put(sopClass, txUids);
		}




		String sopClassUids[] = pcs.keySet().toArray(new String[0]);


		CStoreHandler cstore = new CStoreHandler(sopClassUids);
		ae.register(cstore);
		ae.addAssociationListener(cstore);
		
		
		ae.addAssociationListener(new ScpAssociationListener());


		for (String sopClassUid : sopClassUids)
		{
			String txUids[] = pcs.get(sopClassUid);

			TransferCapability capability = new TransferCapability(sopClassUid,
					txUids, TransferCapability.SCP);
			capabilities.add(capability);

			logger.info("Enabling C-STORE support for: "
					+ dict.nameOf(sopClassUid) + " (" + sopClassUid + ")");
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
