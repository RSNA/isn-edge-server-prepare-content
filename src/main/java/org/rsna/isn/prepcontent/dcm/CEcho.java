/* Copyright (c) <2014>, <Radiological Society of North America>
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

import java.sql.SQLException;
import org.dcm4che2.tool.dcmecho.DcmEcho;
import org.rsna.isn.dao.ConfigurationDao;
        
 /*
 * @author Clifton Li
 * @version 3.2.0
 * @since 3.2.0
 */

public class CEcho 
{
        public static String CEcho(String host,int port,String aet) throws SQLException 
        {
                    ConfigurationDao config = new ConfigurationDao();
                    String device = config.getConfiguration("scp-ae-title");
                    
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