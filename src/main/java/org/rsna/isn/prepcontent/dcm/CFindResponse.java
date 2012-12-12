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

import org.rsna.isn.domain.Device;
import org.rsna.isn.domain.Job;

/**
 * Class for tracking the results of a C-FIND request.
 * 
 * @author Wyatt Tellis
 * @since 3.1.0
 * @version 3.1.0
 */
public class CFindResponse
{

	public CFindResponse(Device device, Job job, String studyUid, int count)
	{
		this.studyUid = studyUid;
		this.device = device;
		this.job = job;
		this.count = count;
	}
	private final String studyUid;

	/**
	 * Get the value of studyUid
	 *
	 * @return the value of studyUid
	 */
	public String getStudyUid()
	{
		return studyUid;
	}

	private final Device device;

	/**
	 * Get the value of device
	 *
	 * @return the value of device
	 */
	public Device getDevice()
	{
		return device;
	}

	private final Job job;

	/**
	 * Get the value of job
	 *
	 * @return the value of job
	 */
	public Job getJob()
	{
		return job;
	}

	private final int count;

	/**
	 * Get the value of count
	 *
	 * @return the value of count
	 */
	public int getCount()
	{
		return count;
	}

}
