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

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.Executor;
import org.apache.commons.lang.time.DateUtils;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.ConfigurationException;
import org.dcm4che2.net.NetworkApplicationEntity;
import org.dcm4che2.net.NetworkConnection;
import org.dcm4che2.net.NewThreadExecutor;
import org.dcm4che2.net.TransferCapability;
import org.dcm4che2.tool.dcmecho.DcmEcho;
import org.rsna.isn.dao.ConfigurationDao;
import org.rsna.isn.domain.Device;

/**
 * A collection of DICOM utility functions. Mostly used by the worker thread.
 *
 * @author Wyatt Tellis
 * @version 2.1.0
 *
 */
public class DcmUtil
{
	private DcmUtil()
	{
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
        
        public static String CEcho(String host,int port,String aet) throws SQLException 
        {
                    ConfigurationDao config = new ConfigurationDao();
                    String device = config.getConfiguration("scu-ae-title");
                    
                    DcmEcho dcmecho = new DcmEcho(device);
                    dcmecho.setRemoteHost(host);
                    dcmecho.setRemotePort(port);
                    dcmecho.setCalledAET(aet, true);
                    
                    try 
                    {
                            dcmecho.open();
                    } 
                    catch (Exception e) 
                    {
                        return "Failed to establish association:" + e.getMessage();
                    }
                    
                    try
                    {
                            dcmecho.echo();
                            dcmecho.close(); 
                    } 
                    catch (Exception e) 
                    {
                            return e.getMessage();
                    } 
                     
                    return "Successfully connected to " + host;
        }
}
