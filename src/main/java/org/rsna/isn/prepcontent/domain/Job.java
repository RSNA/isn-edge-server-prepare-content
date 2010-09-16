/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rsna.isn.prepcontent.domain;

/**
 *
 * @author wtellis
 */
public class Job
{
	public static final int NEW = 1;
	public static final int IN_PROGRESS = 2000;
	public static final int FAILED = -2000;
	public static final int SUCCESSFUL = 2;
	
	private int jobId = -1;

	/**
	 * Get the value of jobId
	 *
	 * @return the value of jobId
	 */
	public int getJobId()
	{
		return jobId;
	}

	/**
	 * Set the value of jobId
	 *
	 * @param jobId new value of jobId
	 */
	public void setJobId(int jobId)
	{
		this.jobId = jobId;
	}

	private int status = 0;

	/**
	 * Get the value of status
	 *
	 * @return the value of status
	 */
	public int getStatus()
	{
		return status;
	}

	/**
	 * Set the value of status
	 *
	 * @param status new value of status
	 */
	public void setStatus(int status)
	{
		this.status = status;
	}

	protected String statusMessage = "";

	/**
	 * Get the value of statusMessage
	 *
	 * @return the value of statusMessage
	 */
	public String getStatusMessage()
	{
		return statusMessage;
	}

	/**
	 * Set the value of statusMessage
	 *
	 * @param statusMessage new value of statusMessage
	 */
	public void setStatusMessage(String statusMessage)
	{
		this.statusMessage = statusMessage;
	}

	private int delay = 0;

	/**
	 * Get the value of delay
	 *
	 * @return the value of delay
	 */
	public int getDelay()
	{
		return delay;
	}

	/**
	 * Set the value of delay
	 *
	 * @param delay new value of delay
	 */
	public void setDelay(int delay)
	{
		this.delay = delay;
	}

	private Exam exam;

	/**
	 * Get the value of exam
	 *
	 * @return the value of exam
	 */
	public Exam getExam()
	{
		return exam;
	}

	/**
	 * Set the value of exam
	 *
	 * @param exam new value of exam
	 */
	public void setExam(Exam exam)
	{
		this.exam = exam;
	}


	@Override
	public String toString()
	{
		return "Job [job id = " + jobId + "]";
	}

}
