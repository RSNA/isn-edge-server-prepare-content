/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rsna.isn.prepcontent.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.rsna.isn.prepcontent.domain.Exam;
import org.rsna.isn.prepcontent.domain.Job;

/**
 *
 * @author wtellis
 */
public class JobDao extends Dao
{
	public Set<Job> getNewJobs() throws ClassNotFoundException, SQLException
	{
		Connection con = getConnection();
		try
		{
			Set<Job> jobs = new LinkedHashSet<Job>();

			String select = "SELECT * FROM v_job_status WHERE status =  " + Job.NEW
					+ " ORDER BY last_transaction_timestamp LIMIT 1";

			ResultSet rs = con.createStatement().executeQuery(select);
			while (rs.next())
			{
				Job job = new Job();

				job.setJobId(rs.getInt("job_id"));
				job.setStatus(rs.getInt("status"));
				job.setStatusMessage(rs.getString("status_message"));
				job.setDelay(rs.getInt("delay_in_hrs"));

				int examId = rs.getInt("exam_id");

				Exam exam = new ExamDao().getExam(examId);
				job.setExam(exam);

				jobs.add(job);
			}

			return jobs;
		}
		finally
		{
			con.close();
		}

	}

	public void updateStatus(Job job, int status, String message)
			throws ClassNotFoundException, SQLException
	{
		Connection con = getConnection();

		try
		{
			String insert = "INSERT INTO transactions"
					+ "(job_id, status, status_message) VALUES (?, ?, ?)";

			PreparedStatement stmt = con.prepareStatement(insert);
			stmt.setInt(1, job.getJobId());
			stmt.setInt(2, status);
			stmt.setString(3, message);

			stmt.execute();
		}
		finally
		{
			con.close();
		}
	}

}
