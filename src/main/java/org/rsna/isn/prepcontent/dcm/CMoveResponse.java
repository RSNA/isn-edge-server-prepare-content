/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rsna.isn.prepcontent.dcm;

import org.rsna.isn.domain.Device;

/**
 *
 * @author Wyatt Tellis
 */
public class CMoveResponse
{
	protected CMoveResponse(Device device, int status, String comments)
	{
		this.device = device;
		this.status = status;
		this.comments = comments;
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

	private final int status;

	/**
	 * Get the value of status
	 *
	 * @return the value of status
	 */
	public int getStatus()
	{
		return status;
	}

	private final String comments;

	/**
	 * Get the value of comments
	 *
	 * @return the value of comments
	 */
	public String getComments()
	{
		return comments;
	}

}
