/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.rsna.isn.prepcontent.domain;

import java.util.Date;

/**
 *
 * @author wtellis
 */
public class Exam {
	private String mrn = "";

	/**
	 * Get the value of mrn
	 *
	 * @return the value of mrn
	 */
	public String getMrn()
	{
		return mrn;
	}

	/**
	 * Set the value of mrn
	 *
	 * @param mrn new value of mrn
	 */
	public void setMrn(String mrn)
	{
		this.mrn = mrn;
	}

	protected String accNum = "";

	/**
	 * Get the value of accNum
	 *
	 * @return the value of accNum
	 */
	public String getAccNum()
	{
		return accNum;
	}

	/**
	 * Set the value of accNum
	 *
	 * @param accNum new value of accNum
	 */
	public void setAccNum(String accNum)
	{
		this.accNum = accNum;
	}

	private String status = "";

	/**
	 * Get the value of status
	 *
	 * @return the value of status
	 */
	public String getStatus()
	{
		return status;
	}

	/**
	 * Set the value of status
	 *
	 * @param status new value of status
	 */
	public void setStatus(String status)
	{
		this.status = status;
	}

	private Date statusTimestamp;

	/**
	 * Get the value of statusTimestamp
	 *
	 * @return the value of statusTimestamp
	 */
	public Date getStatusTimestamp()
	{
		return statusTimestamp;
	}

	/**
	 * Set the value of statusTimestamp
	 *
	 * @param statusTimestamp new value of statusTimestamp
	 */
	public void setStatusTimestamp(Date statusTimestamp)
	{
		this.statusTimestamp = statusTimestamp;
	}

}
